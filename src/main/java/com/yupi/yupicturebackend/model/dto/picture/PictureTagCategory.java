
package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;
import java.util.List;

/**
 * 图片标签和分类封装类
 */
@Data
public class PictureTagCategory {

    // 图片标签列表
    private List<String> tagList;

    // 图片分类列表
    private List<String> categoryList;
}
