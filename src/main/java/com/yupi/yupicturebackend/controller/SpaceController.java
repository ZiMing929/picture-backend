
package com.yupi.yupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceLevel;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间管理接口
 */
@RestController
@RequestMapping("/space")
public class SpaceController {

    /**
     * 用户服务
     */
    @Resource
    private UserService userService;

    /**
     * 空间服务
     */
    @Resource
    private SpaceService spaceService;

    /**
     * 创建空间
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(
            @RequestBody SpaceAddRequest spaceAddRequest,
            HttpServletRequest request) {

        // 1. 校验请求参数
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 调用 SpaceService 创建空间
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);

        // 4. 返回新创建的空间 ID
        return ResultUtils.success(newId);
    }

    /**
     * 删除空间
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {

        // 1. 参数校验
        if (deleteRequest == null || deleteRequest.getId() == null
                || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();

        // 3. 判断空间是否存在
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 仅空间创建者或管理员可以删除
        if (!oldSpace.getUserId().equals(loginUser.getId())
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 5. 删除空间
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 更新空间（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(
            @RequestBody SpaceUpdateRequest spaceUpdateRequest,
            HttpServletRequest request) {

        // 1. 参数校验
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() == null
                || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 将 DTO 转换为 Space 实体
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);

        // 3. 根据空间级别自动填充额度
        spaceService.fileSpaceBySpaceLevel(space);

        // 4. 校验空间数据
        spaceService.validSpace(space, false);

        // 5. 判断空间是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 6. 更新空间
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 根据 ID 获取空间（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {

        // 1. 校验 ID
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 查询空间
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 返回空间实体
        return ResultUtils.success(space);
    }

    /**
     * 根据 ID 获取空间封装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(
            long id,
            HttpServletRequest request) {

        // 1. 校验 ID
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 2. 查询空间
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 转换为 SpaceVO
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);

        return ResultUtils.success(spaceVO);
    }

    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(
            @RequestBody SpaceQueryRequest spaceQueryRequest) {

        // 1. 参数校验
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);

        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();

        ThrowUtils.throwIf(current <= 0 || size <= 0 || size > 100,
                ErrorCode.PARAMS_ERROR);

        // 2. 查询数据库
        Page<Space> spacePage = spaceService.page(
                new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest)
        );

        // 3. 返回分页结果
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(
            @RequestBody SpaceQueryRequest spaceQueryRequest,
            HttpServletRequest request) {

        // 1. 参数校验
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);

        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();

        // 限制单次查询数量
        ThrowUtils.throwIf(current <= 0 || size <= 0 || size > 20,
                ErrorCode.PARAMS_ERROR);

        // 2. 查询数据库
        Page<Space> spacePage = spaceService.page(
                new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest)
        );

        // 3. 转换为空间 VO 分页数据
        Page<SpaceVO> spaceVOPage = spaceService.getSpaceVOPage(
                spacePage,
                request
        );

        return ResultUtils.success(spaceVOPage);
    }

    /**
     * 编辑空间（空间创建者或管理员可用）
     * 暂时使用 SpaceUpdateRequest
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(
            @RequestBody SpaceUpdateRequest spaceEditRequest,
            HttpServletRequest request) {

        // 1. 参数校验
        if (spaceEditRequest == null || spaceEditRequest.getId() == null
                || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3. 查询原空间
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 权限校验
        if (!oldSpace.getUserId().equals(loginUser.getId())
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 5. DTO 转换为实体
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);

        // 6. 普通用户不能通过编辑接口修改空间等级和额度
        if (!userService.isAdmin(loginUser)) {
            space.setSpaceLevel(null);
            space.setMaxSize(null);
            space.setMaxCount(null);
        } else if (space.getSpaceLevel() != null) {
            spaceService.fileSpaceBySpaceLevel(space);
        }

        // 7. 数据校验
        spaceService.validSpace(space, false);

        // 8. 更新空间
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 获取空间级别列表，供前端展示
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {

        // 获取所有空间级别枚举
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()
                ))
                .collect(Collectors.toList());

        return ResultUtils.success(spaceLevelList);
    }
}
