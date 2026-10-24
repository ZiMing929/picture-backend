package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

@Data

//定义一个图片抓取的请求！

public class PictureUploadByBatchRequest {  
  
    /**  
     * 搜索词  
     */  
    private String searchText;  
  
    /**  
     * 抓取数量  
     */  
    private Integer count = 10;


    /**
     * 定义抓取图片的名称前缀，便于我们后续的管理。
     */
    private String namePrefix;


    /**
     * 图片名称
     */
    private String picName;


}
