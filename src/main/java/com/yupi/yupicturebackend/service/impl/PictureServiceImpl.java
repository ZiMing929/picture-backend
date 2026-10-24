package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.upload.FilePictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.UrlPictureUpload;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片服务实现类
 *
 * 功能：
 * 1. 图片上传（本地文件、URL）
 * 2. 图片信息查询和分页
 * 3. 图片审核
 * 4. 批量抓取并上传图片
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService {

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    // 注入腾讯云 COS 管理对象，用于删除图片文件
    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    // 注入 Spring 事务模板
    @Resource
    private TransactionTemplate transactionTemplate;




    /**
     * 上传图片，支持本地文件和 URL 上传
     *
     * @param inputSource 图片来源，可以是 MultipartFile 或 URL 字符串
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser 当前登录用户
     * @return 图片封装对象
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {

        // 1. 校验登录用户
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 获取图片 ID 和空间 ID
        Long pictureId = null;
        Long spaceId = null;

        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
            spaceId = pictureUploadRequest.getSpaceId();
        }

        // 3. 如果是更新图片，先校验原图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);

            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 仅本人或管理员可编辑图片
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // 校验空间是否一致
            // 没传 spaceId，则使用原图片的 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片所属空间一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }


        // 4. 校验空间是否存在以及用户权限
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);

            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

            // 目前空间管理员就是空间创建者
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }

            // 校验空间额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }

            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }

        }


        // 5. 根据空间 ID 设置图片上传目录
        String uploadPathPrefix;

        if (spaceId == null) {
            // 公共图库：按照用户 ID 划分目录
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 私人空间：按照空间 ID 划分目录
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 6. 根据图片来源选择上传方式
        PictureUploadTemplate uploadTemplate = filePictureUpload;

        if (inputSource instanceof String) {
            uploadTemplate = urlPictureUpload;
        }

        // 7. 上传图片到腾讯云 COS
        UploadPictureResult uploadResult = uploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 8. 构造图片实体
        Picture picture = new Picture();

        // 设置压缩图和缩略图地址
        picture.setUrl(uploadResult.getUrl());
        picture.setThumbnailUrl(uploadResult.getThumbnailUrl());

        // 优先使用用户自定义名称，否则使用原图片名称
        String picName = uploadResult.getPicName();

        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }

        // 9. 设置图片基本信息
        picture.setName(picName);
        picture.setPicSize(uploadResult.getPicSize());
        picture.setPicWidth(uploadResult.getPicWidth());
        picture.setPicHeight(uploadResult.getPicHeight());
        picture.setPicScale(uploadResult.getPicScale());
        picture.setPicFormat(uploadResult.getPicFormat());

        // 设置图片创建者
        picture.setUserId(loginUser.getId());

        // 10. 设置图片所属空间
        picture.setSpaceId(spaceId);

        // 11. 填充审核信息
        this.fillReviewParams(picture, loginUser);

        // 12. 如果是更新图片，设置原图片 ID 和编辑时间
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 13. 保存或更新图片数据
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });

        // 14. 返回图片 VO
        return PictureVO.objToVo(picture);
    }




    /**
     * 获取单张图片的 VO 封装对象
     */

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {

        if (picture == null) {
            return null;
        }

        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = picture.getUserId();

        // 查询图片上传者信息
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);

            if (user != null) {
                UserVO userVO = userService.getUserVO(user);
                pictureVO.setUser(userVO);
            }
        }

        return pictureVO;
    }


    /**
     * 分页获取图片 VO 列表
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {

        List<Picture> pictureList = picturePage.getRecords();

        // 创建 VO 分页对象，保留原始分页信息
        Page<PictureVO> pictureVOPage = new Page<>(
                picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal()
        );

        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 1. 将 Picture 转换为 PictureVO
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 2. 收集所有图片对应的用户 ID
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 3. 批量查询用户，避免循环查询数据库
        if (CollUtil.isNotEmpty(userIdSet)) {

            Map<Long, User> userMap = userService.listByIds(userIdSet).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));

            // 4. 为图片 VO 填充用户信息
            for (PictureVO pictureVO : pictureVOList) {
                User user = userMap.get(pictureVO.getUserId());

                if (user != null) {
                    pictureVO.setUser(userService.getUserVO(user));
                }
            }
        }

        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }



    /**
     * 构造图片查询条件
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {

        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();

        if (pictureQueryRequest == null) {
            return queryWrapper;
        }

        // 1. 获取查询参数
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();

        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();

        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();

        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();

        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();

        // 2. 根据关键词搜索名称和简介
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }

        // 3. 普通字段查询
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);

        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");

        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);

        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);

        // 4. 审核相关条件
        queryWrapper.eq(reviewStatus != null, "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

        // 5. 标签查询：数据库中的 tags 为 JSON 字符串
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                if (StrUtil.isNotBlank(tag)) {
                    queryWrapper.like("tags", "\"" + tag + "\"");
                }
            }
        }

        // 6. 排序字段白名单，避免直接拼接非法字段
        if (StrUtil.isNotBlank(sortField)) {
            Set<String> allowedSortFields = new HashSet<>(
                    Arrays.asList("id", "createTime", "editTime", "picSize", "picWidth", "picHeight")
            );

            if (allowedSortFields.contains(sortField)) {
                queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
            }
        }

        return queryWrapper;

    }



    /**
     * 校验图片信息
     */
    @Override
    public void validPicture(Picture picture) {

        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);

        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();

        // 编辑图片时必须传入 ID
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");

        // 校验 URL 长度
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }

        // 校验简介长度
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }



    /**
     * 管理员审核图片
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {

        // 1. 参数和权限校验
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();

        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);

        // 不允许将图片手动审核为“待审核”
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 查询原图片
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 避免重复审核
        if (reviewStatus.equals(oldPicture.getReviewStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }

        // 4. 更新审核信息
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);

        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());

        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }



    /**
     * 填充图片审核信息
     *
     * 管理员上传：自动审核通过
     * 普通用户上传：进入待审核状态
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {

        if (userService.isAdmin(loginUser)) {

            // 管理员上传自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());

        } else {

            // 普通用户上传进入待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
            picture.setReviewerId(null);
            picture.setReviewMessage(null);
            picture.setReviewTime(null);
        }
    }



    /**
     * 批量抓取并上传图片
     *
     * 流程：
     * 1. 校验参数
     * 2. 设置图片名称前缀
     * 3. 请求 Bing 图片搜索页面
     * 4. 使用 Jsoup 解析图片 URL
     * 5. 循环上传图片到 COS
     * 6. 返回成功上传的数量
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {

        // 1. 校验请求参数
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        String searchText = pictureUploadByBatchRequest.getSearchText();
        ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR, "搜索关键词不能为空");

        // 2. 设置抓取数量，默认 10 张，最多 30 张
        Integer count = pictureUploadByBatchRequest.getCount();

        if (count == null) {
            count = 10;
        }

        ThrowUtils.throwIf(count <= 0 || count > 30, ErrorCode.PARAMS_ERROR, "抓取数量必须在 1 到 30 之间");

        // 3. 设置图片名称前缀，未指定时默认使用搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();

        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // 4. 构造 Bing 图片搜索 URL
        String encodedSearchText = URLEncoder.encode(searchText, StandardCharsets.UTF_8);
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", encodedSearchText);

        // 5. 请求并解析搜索页面
        Document document;

        try {
            document = Jsoup.connect(fetchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .timeout(10000)
                    .get();

        } catch (IOException e) {
            log.error("获取图片搜索页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片搜索页面失败");
        }

        // 6. 获取图片列表容器
        Element div = document.getElementsByClass("dgControl").first();

        if (div == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片列表失败，可能是搜索页面结构发生变化");
        }

        // 7. 获取所有图片元素
        Elements imgElementList = div.select("img.mimg");

        // 8. 遍历图片并上传
        int uploadCount = 0;

        for (Element imgElement : imgElementList) {

            // 优先获取图片 src
            String fileUrl = imgElement.attr("src");

            // 跳过没有 URL 的图片
            if (StrUtil.isBlank(fileUrl)) {
                log.info("图片链接为空，已跳过");
                continue;
            }

            // 只处理 HTTP 和 HTTPS 图片
            if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
                continue;
            }

            // 去除 URL 查询参数
            int questionMarkIndex = fileUrl.indexOf("?");

            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 9. 构造单张图片上传请求
            PictureUploadRequest uploadRequest = new PictureUploadRequest();

            // 设置图片名称：名称前缀 + 序号
            if (StrUtil.isNotBlank(namePrefix)) {
                uploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }

            // 10. 上传图片，单张失败不影响后续图片
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, uploadRequest, loginUser);

                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;

            } catch (Exception e) {
                log.error("批量上传单张图片失败，URL = {}", fileUrl, e);
            }

            // 达到指定数量后停止抓取
            if (uploadCount >= count) {
                break;
            }
        }

        log.info("批量图片上传结束，成功数量 = {}", uploadCount);
        return uploadCount;
    }



    /**
     * 清理图片文件（异步执行）
     * 删除 COS 中的压缩图和缩略图
     *
     * @param oldPicture 需要清理的旧图片
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {

        // 1. 校验图片对象
        if (oldPicture == null || StrUtil.isBlank(oldPicture.getUrl())) {
            return;
        }

        // 2. 获取原图片 URL
        String pictureUrl = oldPicture.getUrl();

        // 3. 检查是否还有其他图片记录引用该文件
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();

        // 如果还有其他记录引用，则不能删除
        if (count > 0) {
            log.info("图片仍被数据库记录引用，跳过清理：{}", pictureUrl);
            return;
        }

        // 4. 删除 COS 中的压缩图片
        String pictureKey = getCosObjectKey(pictureUrl);
        cosManager.deleteObject(pictureKey);

        // 5. 删除 COS 中的缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();

        if (StrUtil.isNotBlank(thumbnailUrl)) {
            // 缩略图可能与原图相同，避免重复删除
            if (!thumbnailUrl.equals(pictureUrl)) {
                String thumbnailKey = getCosObjectKey(thumbnailUrl);
                cosManager.deleteObject(thumbnailKey);
            }
        }

        log.info("图片文件清理完成：{}", pictureUrl);
    }


/**
 * 校验图片操作权限
 *
 * @param loginUser 当前登录用户
 * @param picture   要操作的图片
 */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();

        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

        }

    }



    /**
     * 从 COS 图片 URL 中提取对象 Key
     *
     * 例如：
     * https://example.com/public/1/test.webp
     * 转换为：
     * public/1/test.webp
     */
    private String getCosObjectKey(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }

        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();

            if (StrUtil.isNotBlank(path)) {
                return path.startsWith("/") ? path.substring(1) : path;
            }
        } catch (IllegalArgumentException e) {
            log.warn("图片 URL 解析失败：{}", url, e);
        }

        // 如果传入的本身就是对象 Key，直接返回
        return url.startsWith("/") ? url.substring(1) : url;
    }



    //删除图片的逻辑代码
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 校验权限
        checkPictureAuth(loginUser, oldPicture);

        // 操作数据库
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);

        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });

        // 异步清理文件
        this.clearPictureFile(oldPicture);

    }



    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

    }

}