package com.yupi.yupicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;


@Service
@Slf4j
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 一、通过本地文件上传图片
     */
    public UploadPictureResult uploadPicture(
            MultipartFile multipartFile,
            String uploadPathPrefix) {

        // 1. 校验图片
        validPicture(multipartFile);

        // 2. 生成上传文件名
        String uuid = RandomUtil.randomString(16);
        String originFilename = multipartFile.getOriginalFilename();

        String uploadFilename = String.format(
                "%s_%s.%s",
                DateUtil.formatDate(new Date()),
                uuid,
                FileUtil.getSuffix(originFilename)
        );

        String uploadPath = String.format(
                "/%s/%s",
                uploadPathPrefix,
                uploadFilename
        );

        File file = null;

        try {
            // 3. 创建临时文件
            file = File.createTempFile("picture_", ".tmp");

            // 4. MultipartFile 写入临时文件
            multipartFile.transferTo(file);

            // 5. 上传至 COS
            PutObjectResult putObjectResult =
                    cosManager.putPictureObject(uploadPath, file);

            // 6. 获取图片信息并返回
            return buildUploadPictureResult(
                    putObjectResult,
                    file,
                    originFilename,
                    uploadPath
            );

        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "上传失败"
            );
        } finally {
            deleteTempFile(file);
        }
    }

    /**
     * 二、通过图片 URL 上传图片
     */
    public UploadPictureResult uploadPictureByUrl(
            String fileUrl,
            String uploadPathPrefix) {

        // 1. 校验 URL 图片
        validPicture(fileUrl);

        // 2. 提取 URL 路径中的文件名
        String originFilename;
        try {
            URL url = new URL(fileUrl);
            originFilename = FileUtil.getName(url.getPath());
        } catch (MalformedURLException e) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "图片 URL 格式不正确"
            );
        }

        if (StrUtil.isBlank(originFilename)) {
            originFilename = "image.jpg";
        }

        // 3. 生成 COS 文件名
        String uuid = RandomUtil.randomString(16);

        String suffix = FileUtil.getSuffix(originFilename);

        if (StrUtil.isBlank(suffix)) {
            suffix = "jpg";
        }

        String uploadFilename = String.format(
                "%s_%s.%s",
                DateUtil.formatDate(new Date()),
                uuid,
                suffix
        );

        String uploadPath = String.format(
                "/%s/%s",
                uploadPathPrefix,
                uploadFilename
        );

        File file = null;

        try {
            // 4. 创建临时文件
            file = File.createTempFile("picture_url_", ".tmp");

            // 5. 下载网络图片到临时文件
            HttpUtil.downloadFile(fileUrl, file);

            // 6. 下载后再次校验大小
            long fileSize = FileUtil.size(file);

            final long TWO_MB = 2 * 1024 * 1024L;

            ThrowUtils.throwIf(
                    fileSize > TWO_MB,
                    ErrorCode.PARAMS_ERROR,
                    "文件大小不能超过 2M"
            );

            ThrowUtils.throwIf(
                    fileSize <= 0,
                    ErrorCode.PARAMS_ERROR,
                    "下载的图片为空"
            );

            // 7. 上传至 COS
            PutObjectResult putObjectResult =
                    cosManager.putPictureObject(uploadPath, file);

            // 8. 封装上传结果
            return buildUploadPictureResult(
                    putObjectResult,
                    file,
                    originFilename,
                    uploadPath
            );

        } catch (Exception e) {
            log.error("通过 URL 上传图片失败，url = {}", fileUrl, e);

            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }

            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "通过 URL 上传图片失败"
            );

        } finally {
            deleteTempFile(file);
        }
    }

    /**
     * 三、统一封装上传结果
     */
    private UploadPictureResult buildUploadPictureResult(
            PutObjectResult putObjectResult,
            File file,
            String originFilename,
            String uploadPath) {

        ImageInfo imageInfo = putObjectResult
                .getCiUploadResult()
                .getOriginalInfo()
                .getImageInfo();

        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();

        ThrowUtils.throwIf(
                picHeight <= 0,
                ErrorCode.SYSTEM_ERROR,
                "图片高度不合法"
        );

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

        String host = cosClientConfig.getHost();
        String normalizedHost = StrUtil.removeSuffix(host, "/");
        String normalizedPath = StrUtil.addPrefixIfNot(uploadPath, "/");

        result.setUrl(normalizedHost + normalizedPath);

        return result;
    }

    /**
     * 四、校验本地上传文件
     */
    public void validPicture(MultipartFile multipartFile) {

        ThrowUtils.throwIf(
                multipartFile == null,
                ErrorCode.PARAMS_ERROR,
                "文件不能为空"
        );

        // 1. 校验大小
        long fileSize = multipartFile.getSize();

        final long TWO_MB = 2 * 1024 * 1024L;

        ThrowUtils.throwIf(
                fileSize > TWO_MB,
                ErrorCode.PARAMS_ERROR,
                "文件大小不能超过 2M"
        );

        // 2. 校验后缀
        String fileSuffix = FileUtil.getSuffix(
                multipartFile.getOriginalFilename()
        );

        String normalizedSuffix =
                StrUtil.nullToEmpty(fileSuffix)
                        .toLowerCase(Locale.ROOT);

        final List<String> ALLOW_FORMAT_LIST =
                Arrays.asList("jpeg", "jpg", "png", "webp");

        ThrowUtils.throwIf(
                !ALLOW_FORMAT_LIST.contains(normalizedSuffix),
                ErrorCode.PARAMS_ERROR,
                "文件类型错误"
        );
    }

    /**
     * 五、校验 URL 图片
     */
    private void validPicture(String fileUrl) {

        ThrowUtils.throwIf(
                StrUtil.isBlank(fileUrl),
                ErrorCode.PARAMS_ERROR,
                "文件地址不能为空"
        );

        // 1. 校验 URL 格式
        try {
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "文件地址格式不正确"
            );
        }

        // 2. 校验协议
        ThrowUtils.throwIf(
                !(fileUrl.startsWith("http://")
                        || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR,
                "仅支持 HTTP 或 HTTPS 协议的文件地址"
        );

        // 3. HEAD 请求
        HttpResponse response = null;

        try {
            response = HttpUtil
                    .createRequest(Method.HEAD, fileUrl)
                    .timeout(10000)
                    .execute();

            // 部分图片服务器不支持 HEAD
            // 这种情况下跳过响应头检查，下载后继续检查
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                log.warn(
                        "图片 HEAD 请求未返回 200，url = {}，status = {}",
                        fileUrl,
                        response.getStatus()
                );
                return;
            }

            // 4. 校验 Content-Type
            String contentType = response.header("Content-Type");

            if (StrUtil.isNotBlank(contentType)) {
                String normalizedContentType =
                        contentType.split(";")[0]
                                .trim()
                                .toLowerCase(Locale.ROOT);

                final List<String> ALLOW_CONTENT_TYPES =
                        Arrays.asList(
                                "image/jpeg",
                                "image/jpg",
                                "image/png",
                                "image/webp"
                        );

                ThrowUtils.throwIf(
                        !ALLOW_CONTENT_TYPES.contains(normalizedContentType),
                        ErrorCode.PARAMS_ERROR,
                        "文件类型错误"
                );
            }

            // 5. 校验 Content-Length
            String contentLengthStr =
                    response.header("Content-Length");

            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength =
                            Long.parseLong(contentLengthStr);

                    final long TWO_MB = 2 * 1024 * 1024L;

                    ThrowUtils.throwIf(
                            contentLength > TWO_MB,
                            ErrorCode.PARAMS_ERROR,
                            "文件大小不能超过 2M"
                    );

                } catch (NumberFormatException e) {
                    throw new BusinessException(
                            ErrorCode.PARAMS_ERROR,
                            "文件大小格式错误"
                    );
                }
            }

        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * 六、删除临时文件
     */
    public void deleteTempFile(File file) {

        if (file == null) {
            return;
        }

        if (file.exists()) {
            boolean deleteResult = file.delete();

            if (!deleteResult) {
                log.error(
                        "file delete error, filepath = {}",
                        file.getAbsolutePath()
                );
            }
        }
    }
}
