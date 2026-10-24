package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import javax.servlet.http.HttpServletRequest;


/**
* @author 86155
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-10-22 16:10:07
*/

public interface SpaceService extends IService<Space> {     // 接口类，然后要去对应的实现类去做实现！


    //获取数据库的请求
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    // 获取单条空间的数据
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    // 空间分页的获取
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    //做空间的数据校验功能
    void validSpace(Space space,boolean add);

    //空间的限额数据
    void fileSpaceBySpaceLevel(Space space);

    //创建空间的业务逻辑
    long  addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

}
