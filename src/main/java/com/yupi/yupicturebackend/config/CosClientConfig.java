package com.yupi.yupicturebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

    /**
     * COS 访问域名
     */
    private String host;

    /**
     * 腾讯云 SecretId
     */
    private String secretId;

    /**
     * 腾讯云 SecretKey
     */
    private String secretKey;

    /**
     * 存储桶地域
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 创建 COS 客户端
     */
    @Bean
    public COSClient cosClient() {

        // 1. 检查配置是否存在
        if (isBlank(secretId) || isBlank(secretKey)
                || isBlank(region) || isBlank(bucket)) {
            throw new IllegalStateException(
                    "COS 配置不完整，请检查环境变量"
            );
        }

        // 2. 检查是否存在首尾空白字符

        if (!secretId.equals(secretId.trim())) {
            throw new IllegalStateException("COS_SECRET_ID 存在首尾空白字符");
        }

        if (!secretKey.equals(secretKey.trim())) {
            throw new IllegalStateException("COS_SECRET_KEY 存在首尾空白字符");
        }

        if (!region.equals(region.trim())) {
            throw new IllegalStateException("COS_REGION 存在首尾空白字符");
        }

        if (!bucket.equals(bucket.trim())) {
            throw new IllegalStateException("COS_BUCKET 存在首尾空白字符");
        }


        // 3. 输出安全调试信息，不打印真实密钥
        System.out.println("========== COS 配置检查 ==========");
        System.out.println("COS Bucket: [" + bucket + "]");
        System.out.println("COS Region: [" + region + "]");
        System.out.println("COS SecretId 已配置: true");
        System.out.println("COS SecretKey 已配置: true");
        System.out.println("COS SecretId 长度: " + secretId.length());
        System.out.println("COS SecretKey 长度: " + secretKey.length());
        System.out.println("==================================");

        // 4. 初始化用户身份信息
        COSCredentials cred =
                new BasicCOSCredentials(secretId, secretKey);

        // 5. 设置 COS 存储桶地域
        ClientConfig clientConfig =
                new ClientConfig(new Region(region));

        // 6. 创建 COS 客户端
        return new COSClient(cred, clientConfig);
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
