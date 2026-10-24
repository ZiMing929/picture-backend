package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;

    /**
     * 图片上传模板方法
     *
     * @param inputSource 图片来源：本地文件或 URL
     * @param uploadPathPrefix 上传路径前缀
     * @return 上传结果
     */
    public final UploadPictureResult uploadPicture(
            Object inputSource,
            String uploadPathPrefix) {

        validPicture(inputSource);

        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);

        String suffix = FileUtil.getSuffix(originFilename);

        if (suffix == null || suffix.trim().isEmpty()) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "图片文件缺少有效扩展名"
            );
        }

        suffix = suffix.trim().toLowerCase();

        String uploadFilename = String.format(
                "%s_%s.%s",
                DateUtil.formatDate(new Date()),
                uuid,
                suffix
        );

        // COS 对象 Key 不需要以 / 开头
        String pathPrefix = uploadPathPrefix == null
                ? ""
                : uploadPathPrefix.trim()
                .replace("\\", "/")
                .replaceAll("^/+|/+$", "");

        String uploadPath = pathPrefix.isEmpty()
                ? uploadFilename
                : pathPrefix + "/" + uploadFilename;

        File file = null;

        try {
            // 创建本地临时文件
            file = File.createTempFile("picture_", "." + suffix);

            // 将上传来源写入临时文件
            processFile(inputSource, file);

            // 上传 COS 并执行图片处理
            PutObjectResult putObjectResult =
                    cosManager.putPictureObject(uploadPath, file);

            if (putObjectResult == null
                    || putObjectResult.getCiUploadResult() == null
                    || putObjectResult.getCiUploadResult()
                    .getOriginalInfo() == null
                    || putObjectResult.getCiUploadResult()
                    .getOriginalInfo()
                    .getImageInfo() == null) {

                throw new IllegalStateException(
                        "COS 未返回有效的原图信息"
                );
            }

            ImageInfo imageInfo = putObjectResult
                    .getCiUploadResult()
                    .getOriginalInfo()
                    .getImageInfo();

            ProcessResults processResults = putObjectResult
                    .getCiUploadResult()
                    .getProcessResults();

            if (processResults != null) {
                List<CIObject> objectList =
                        processResults.getObjectList();

                if (CollUtil.isNotEmpty(objectList)) {

                    CIObject compressedCiObject = objectList.get(0);

                    if (compressedCiObject != null
                            && hasValidKey(compressedCiObject.getKey())) {

                        CIObject thumbnailCiObject = null;

                        if (objectList.size() > 1) {
                            CIObject candidate = objectList.get(1);

                            if (candidate != null
                                    && hasValidKey(candidate.getKey())) {
                                thumbnailCiObject = candidate;
                            }
                        }

                        return buildResult(
                                originFilename,
                                compressedCiObject,
                                thumbnailCiObject
                        );
                    }
                }
            }

            // COS 没有返回有效的图片处理结果，使用原图
            return buildResult(
                    originFilename,
                    file,
                    uploadPath,
                    imageInfo
            );

        } catch (Exception e) {
            log.error(
                    "图片上传失败，uploadPath={}",
                    uploadPath,
                    e
            );

            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "上传失败"
            );

        } finally {
            deleteTempFile(file);
        }
    }

    /**
     * 统一生成 COS 访问地址
     * 清理 Host 前后空格及重复斜杠
     */
    private String buildCosUrl(String objectKey) {

        String host = cosClientConfig.getHost();

        if (host == null || host.trim().isEmpty()) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "COS Host 未配置"
            );
        }

        if (!hasValidKey(objectKey)) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "COS 对象 Key 无效"
            );
        }

        // 去除 Host 前后空格及末尾 /
        host = host.trim().replaceAll("/+$", "");

        // 去除对象 Key 前后的空格及开头 /
        String key = objectKey.trim().replaceFirst("^/+", "");

        return host + "/" + key;
    }

    /**
     * 判断 COS 对象 Key 是否有效
     */
    private boolean hasValidKey(String key) {

        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        String normalizedKey = key.trim();

        // 防止出现 xxx_thumbnail. 这种缺少扩展名的 Key
        return !normalizedKey.endsWith(".");
    }

    /**
     * 封装压缩图和缩略图上传结果
     */
    private UploadPictureResult buildResult(
            String originFilename,
            CIObject compressedCiObject,
            CIObject thumbnailCiObject) {

        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();

        if (picWidth <= 0 || picHeight <= 0) {
            throw new IllegalStateException(
                    "COS 返回的图片宽高无效"
            );
        }

        double picScale = NumberUtil.round(
                picWidth * 1.0 / picHeight,
                2
        ).doubleValue();

        UploadPictureResult result = new UploadPictureResult();

        result.setPicName(FileUtil.mainName(originFilename));
        result.setPicWidth(picWidth);
        result.setPicHeight(picHeight);
        result.setPicScale(picScale);
        result.setPicFormat(compressedCiObject.getFormat());
        result.setPicSize(
                compressedCiObject.getSize().longValue()
        );

        // 压缩图 URL
        String pictureUrl = buildCosUrl(
                compressedCiObject.getKey()
        );

        result.setUrl(pictureUrl);

        // 有有效缩略图时使用缩略图，否则使用压缩图
        if (thumbnailCiObject != null
                && hasValidKey(thumbnailCiObject.getKey())) {

            result.setThumbnailUrl(
                    buildCosUrl(thumbnailCiObject.getKey())
            );

        } else {

            result.setThumbnailUrl(pictureUrl);
        }

        log.info(
                "图片上传成功，url={}, thumbnailUrl={}",
                result.getUrl(),
                result.getThumbnailUrl()
        );

        return result;
    }

    /**
     * COS 没有返回处理结果时，封装原图信息
     */
    private UploadPictureResult buildResult(
            String originFilename,
            File file,
            String uploadPath,
            ImageInfo imageInfo) {

        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();

        if (picWidth <= 0 || picHeight <= 0) {
            throw new IllegalStateException(
                    "COS 返回的原图宽高无效"
            );
        }

        double picScale = NumberUtil.round(
                picWidth * 1.0 / picHeight,
                2
        ).doubleValue();

        UploadPictureResult result = new UploadPictureResult();

        result.setPicName(FileUtil.mainName(originFilename));
        result.setPicWidth(picWidth);
        result.setPicHeight(picHeight);
        result.setPicScale(picScale);
        result.setPicFormat(imageInfo.getFormat());
        result.setPicSize(FileUtil.size(file));

        String pictureUrl = buildCosUrl(uploadPath);

        result.setUrl(pictureUrl);
        result.setThumbnailUrl(pictureUrl);

        log.info(
                "图片使用原图回退，url={}",
                pictureUrl
        );

        return result;
    }

    /**
     * 校验图片输入来源
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理图片来源并写入本地文件
     */
    protected abstract void processFile(
            Object inputSource,
            File file
    ) throws Exception;

    /**
     * 删除本地临时文件
     */
    public void deleteTempFile(File file) {

        if (file == null) {
            return;
        }

        if (file.exists() && !file.delete()) {
            log.warn(
                    "临时文件删除失败，filepath={}",
                    file.getAbsolutePath()
            );
        }
    }
}
