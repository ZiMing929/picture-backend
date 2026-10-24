package generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.model.entity.Picture;
import generator.service.PictureService;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import org.springframework.stereotype.Service;

/**
* @author 86155
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2026-10-17 19:25:31
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

}




