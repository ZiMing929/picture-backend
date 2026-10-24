package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;


/**
* @author 86155
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-10-17 19:25:31
*/

public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */

    //业务逻辑---需要在实现类里面去实现！ ---按住uploadPicture---然后去alt+回车----生成对应的实现类去做实现！
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);


    // 获取单条图片的数据
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    // 图片分页的获取
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);


    //做图片的数据校验功能
    void validPicture(Picture picture);


    /**
     * 图片审核
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);


    void fillReviewParams(Picture picture, User loginUser);



    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser);


    //删除图片的方法
    void clearPictureFile(Picture picture);



    //校验空间的权限
    void checkPictureAuth(User loginUser,Picture picture);


    //删除图片
    void deletePicture(long pictureId, User loginUser);


    // 编辑图片
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);



}
