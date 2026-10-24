package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.util.Locale;

@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    /**
     * 校验图片 URL
     */
    @Override
    protected void validPicture(Object inputSource) {
        ThrowUtils.throwIf(
                !(inputSource instanceof String),
                ErrorCode.PARAMS_ERROR,
                "图片地址格式错误"
        );

        String fileUrl = ((String) inputSource).trim();

        ThrowUtils.throwIf(
                StrUtil.isBlank(fileUrl),
                ErrorCode.PARAMS_ERROR,
                "文件地址不能为空"
        );

        try {
            URI uri = new URI(fileUrl);
            String scheme = uri.getScheme();

            ThrowUtils.throwIf(
                    scheme == null ||
                            (!"http".equalsIgnoreCase(scheme)
                                    && !"https".equalsIgnoreCase(scheme))
                            || uri.getHost() == null,
                    ErrorCode.PARAMS_ERROR,
                    "只支持 HTTP 或 HTTPS 图片地址"
            );
        } catch (Exception e) {
            ThrowUtils.throwIf(
                    true,
                    ErrorCode.PARAMS_ERROR,
                    "图片 URL 不合法"
            );
        }
    }

    /**
     * 获取图片文件名
     *
     * 对没有标准扩展名的 URL 暂时使用 jpg，
     * 解决必应图片 URL 无扩展名导致上传前校验失败的问题。
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = ((String) inputSource).trim();

        try {
            String path = new URI(fileUrl).getPath();

            if (StrUtil.isNotBlank(path)) {
                String filename = path.substring(path.lastIndexOf('/') + 1);

                String lowerName = filename.toLowerCase(Locale.ROOT);

                if (lowerName.endsWith(".jpg")
                        || lowerName.endsWith(".jpeg")
                        || lowerName.endsWith(".png")
                        || lowerName.endsWith(".webp")
                        || lowerName.endsWith(".gif")
                        || lowerName.endsWith(".bmp")) {
                    return filename;
                }
            }
        } catch (Exception ignored) {
            // 校验阶段已检查 URL，这里使用默认文件名
        }

        return "remote_picture.jpg";
    }

    /**
     * 下载网络图片到本地临时文件
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = ((String) inputSource).trim();
        HttpUtil.downloadFile(fileUrl, file);
    }
}