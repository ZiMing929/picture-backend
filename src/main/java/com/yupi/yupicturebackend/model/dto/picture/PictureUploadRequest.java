package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;
import java.io.Serializable;



@Data
//上传图片的请求！

public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;      //支持url地址来上传图片的功能！


    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id
     */
    private Long spaceId;



    private static final long serialVersionUID = 1L;


}
