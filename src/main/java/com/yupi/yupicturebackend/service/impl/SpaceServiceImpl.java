package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 空间服务实现类
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 获取空间查询条件
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {

        // 1. 创建查询条件对象
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();

        // 2. 判断请求参数是否为空
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }

        // 3. 从请求对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        // 4. 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);

        // 5. 排序：限制允许排序的字段，避免 SQL 注入
        Set<String> allowedSortFields = new HashSet<>(Arrays.asList("id", "spaceName", "spaceLevel", "createTime", "editTime", "updateTime"));
        if (StrUtil.isNotBlank(sortField) && allowedSortFields.contains(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        }

        // 6. 返回查询条件
        return queryWrapper;

    }


    /**
     * 获取空间封装类
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {

        // 1. 判断空间是否为空
        if (space == null) {
            return null;
        }

        // 2. 将 Space 实体转换为 SpaceVO
        SpaceVO spaceVO = new SpaceVO();
        BeanUtils.copyProperties(space, spaceVO);

        // 3. 获取空间创建者的用户 ID
        Long userId = space.getUserId();

        // 4. 查询空间创建者的信息
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);

            if (user != null) {
                // 5. 封装用户信息
                UserVO userVO = userService.getUserVO(user);
                spaceVO.setUser(userVO);
            }
        }
        // 6. 返回空间 VO
        return spaceVO;
    }

    
    
    /**
     * 分页获取空间封装类---空间的返回也是直接复制的图片的代码
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {

        List<Space> spaceList = spacePage.getRecords();

        // 创建 VO 分页对象，保留原始分页信息
        Page<SpaceVO> spaceVOPage = new Page<>(
                spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal()
        );

        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }

        // 1. 将 Space 转换为 SpaceVO
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());

        // 2. 收集所有图片对应的用户 ID
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 3. 批量查询用户，避免循环查询数据库
        if (CollUtil.isNotEmpty(userIdSet)) {

            Map<Long, User> userMap = userService.listByIds(userIdSet).stream()
                    .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first));

            // 4. 为图片 VO 填充用户信息
            for (SpaceVO spaceVO : spaceVOList) {
                User user = userMap.get(spaceVO.getUserId());

                if (user != null) {
                    spaceVO.setUser(userService.getUserVO(user));
                }
            }
        }
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;

    }



    
    /**
     * 校验空间数据
     */
    @Override
    public void validSpace(Space space, boolean add) {

        // 1. 校验空间对象不能为空
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);

        // 2. 获取空间字段
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();

        // 3. 新增空间时进行必填校验
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }

            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }

        // 4. 校验空间级别是否合法
        if (spaceLevel != null &&
                SpaceLevelEnum.getEnumByValue(spaceLevel) == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }

        // 5. 校验空间名称长度
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }

    }

    @Override
    public void fileSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }




    // 创建空间!
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }

        // 填充数据  ---注意这里是file，而不是fill！别搞错了！

        this.fileSpaceBySpaceLevel(space);


        // 数据校验

        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);

        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }


        // 针对用户进行加锁，一个用户一把锁！
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                boolean exists = this.lambdaQuery().eq(Space::getUserId, userId).exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户仅能有一个私有空间");

                // 写入数据库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

                // 返回新写入的数据 id
                return space.getId();
            });

            // 返回结果是包装类，可以做一些处理
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }


}
