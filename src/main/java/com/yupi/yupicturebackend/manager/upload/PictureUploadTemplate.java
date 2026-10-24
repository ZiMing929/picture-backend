
package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;


    /**
     * 模板方法：定义图片上传的完整流程
     *
     * @param inputSource 图片来源（本地文件或 URL）
     * @param uploadPathPrefix 上传路径前缀
     * @return 图片上传结果
     */
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {

        // 一、校验图片
        validPicture(inputSource);

        // 二、生成图片上传路径
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);
        String uploadFilename = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()),
                uuid,
                FileUtil.getSuffix(originFilename));

        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;

        try {
            // 三、创建本地临时文件
            file = File.createTempFile("picture_", "." + FileUtil.getSuffix(originFilename));

            // 四、处理图片来源（本地文件或 URL）
            processFile(inputSource, file);

            // 五、上传图片到腾讯云 COS，并执行图片处理
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 六、获取原图信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult()
                    .getOriginalInfo()
                    .getImageInfo();

            // 七、获取图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();

            if (processResults != null) {
                List<CIObject> objectList = processResults.getObjectList();


                // 八、按照 COS 处理规则，获取压缩图和缩略图
                if (CollUtil.isNotEmpty(objectList)) {
                    CIObject compressedCiObject = objectList.get(0);

                    // 缩略图默认等于压缩图
                    CIObject thumbnailCiObject = compressedCiObject;

                    // 有生成缩略图，才得到缩略图
                    if (objectList.size() > 1) {
                        thumbnailCiObject = objectList.get(1);
                    }
                    // 封装压缩图返回结果
                    return buildResult(originFilename, compressedCiObject, thumbnailCiObject);
                }

                // 兼容只有压缩图、没有缩略图的情况
                if (CollUtil.isNotEmpty(objectList)) {
                    CIObject compressedCiObject = objectList.get(0);
                    return buildResult(originFilename, compressedCiObject, null);
                }
            }

            // 十、如果 COS 没有返回图片处理结果，则返回原图信息
            return buildResult(originFilename, file, uploadPath, imageInfo);

        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");

        } finally {
            // 十一、上传结束后删除临时文件
            deleteTempFile(file);
        }
    }


    /**
     * 封装压缩图及缩略图的返回结果
     *
     * @param originFilename 原始文件名
     * @param compressedCiObject 压缩后的图片对象
     * @param thumbnailCiObject 缩略图对象，允许为 null
     * @return 图片上传结果
     */
    private UploadPictureResult buildResult(String originFilename,
                                            CIObject compressedCiObject,
                                            CIObject thumbnailCiObject) {

        // 一、获取压缩后的图片宽高
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();

        // 二、计算图片宽高比
        double picScale = NumberUtil.round(
                picWidth * 1.0 / picHeight, 2
        ).doubleValue();

        // 三、创建返回结果对象
        UploadPictureResult uploadPictureResult = new UploadPictureResult();

        // 四、设置图片名称
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));

        // 五、设置图片尺寸信息
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);

        // 六、设置图片格式和文件大小
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());

        // 七、设置压缩后的图片 URL
        uploadPictureResult.setUrl(
                cosClientConfig.getHost() + "/" + compressedCiObject.getKey()
        );

        // 八、设置缩略图 URL
        if (thumbnailCiObject != null) {
            uploadPictureResult.setThumbnailUrl(
                    cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey()
            );
        }

        // 九、返回结果
        return uploadPictureResult;
    }

    /**
     * 校验图片输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取图片原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源，生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装原图的返回结果
     *
     * 当 COS 没有返回压缩图和缩略图时使用
     */
    private UploadPictureResult buildResult(String originFilename,
                                            File file,
                                            String uploadPath,
                                            ImageInfo imageInfo) {

        UploadPictureResult uploadPictureResult = new UploadPictureResult();

        // 一、获取原图宽高
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();

        // 二、计算图片宽高比
        double picScale = NumberUtil.round(
                picWidth * 1.0 / picHeight, 2
        ).doubleValue();

        // 三、设置图片基本信息
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));

        // 四、设置原图 URL
        uploadPictureResult.setUrl(
                cosClientConfig.getHost() + "/" + uploadPath
        );

        // 五、原图回退时暂时使用原图地址作为缩略图地址
        uploadPictureResult.setThumbnailUrl(
                cosClientConfig.getHost() + "/" + uploadPath
        );

        return uploadPictureResult;
    }

    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {

        if (file == null) {
            return;
        }

        boolean deleteResult = file.delete();

        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}
