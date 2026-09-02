// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\oss\OssService.java
package com.chiji.framework.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * 阿里云 OSS 服务。
 * <p>
 * 提供三种能力：
 * <ol>
 *     <li>{@link #generatePostPolicy(String)} 生成 Web 端直传 POST 签名
 *     （policy 限制单文件 20MB、key 前缀目录、有效期 5 分钟）；</li>
 *     <li>{@link #signedUrl(String, int)} 生成带签名的临时访问 URL；</li>
 *     <li>{@link #delete(String)} 删除对象。</li>
 * </ol>
 * <p>
 * OSS 客户端懒加载：仅在首次使用时创建，未配置环境变量时不阻塞应用启动；
 * 调用前校验配置完整性，缺失时抛出 OSS_ERROR 业务异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    /** 直传文件大小上限：20MB。 */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /** 直传 policy 有效期：5 分钟。 */
    private static final long POST_POLICY_TTL_SECONDS = 5 * 60L;

    private final OssProperties ossProperties;
    private final ObjectMapper objectMapper;

    /** 懒加载的 OSS 客户端（volatile + 双重检查，保证线程安全）。 */
    private volatile OSS ossClient;

    /**
     * 生成 Web 端直传 POST 签名参数。
     *
     * @param dir 允许上传的 key 前缀目录（如 {@code aligner/20260816}，可传空表示不限目录）
     * @return 直传签名参数
     */
    public OssPostPolicy generatePostPolicy(String dir) {
        try {
            String dirKey = normalizeDir(dir);
            String host = "https://" + ossProperties.getBucket() + "." + endpointHost();
            long expireEndTime = System.currentTimeMillis() / 1000 + POST_POLICY_TTL_SECONDS;

            // 构建 policy 文档：bucket、文件大小上限 20MB、key 以 dir 开头
            Map<String, Object> policyDoc = new HashMap<>();
            policyDoc.put("expiration", isoExpiration(expireEndTime));
            List<Object> conditions = new ArrayList<>();
            conditions.add(Map.<String, String>of("bucket", ossProperties.getBucket()));
            conditions.add(List.<Object>of("content-length-range", 0, MAX_FILE_SIZE));
            conditions.add(List.<Object>of("starts-with", "$key", dirKey));
            policyDoc.put("conditions", conditions);

            String policyJson = objectMapper.writeValueAsString(policyDoc);
            String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
            String signature = hmacSha1(ossProperties.getAccessKeySecret(), policy);

            return new OssPostPolicy(ossProperties.getAccessKeyId(), host, policy, signature, expireEndTime, dirKey);
        } catch (Exception e) {
            log.error("生成 OSS 直传签名失败", e);
            throw new BusinessException(ErrorCode.OSS_ERROR, "生成直传签名失败");
        }
    }

    /**
     * 生成带签名的临时访问 URL。
     *
     * @param ossKey  对象 key
     * @param minutes 有效期（分钟）
     * @return 签名 URL
     */
    public String signedUrl(String ossKey, int minutes) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    ossProperties.getBucket(), ossKey);
            request.setExpiration(new Date(System.currentTimeMillis() + minutes * 60_000L));
            URL url = getClient().generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            log.error("生成 OSS 签名 URL 失败, key={}", ossKey, e);
            throw new BusinessException(ErrorCode.OSS_ERROR, "生成签名 URL 失败");
        }
    }

    /**
     * 删除对象。
     *
     * @param ossKey 对象 key
     */
    public void delete(String ossKey) {
        try {
            getClient().deleteObject(ossProperties.getBucket(), ossKey);
        } catch (Exception e) {
            log.error("删除 OSS 对象失败, key={}", ossKey, e);
            throw new BusinessException(ErrorCode.OSS_ERROR, "删除对象失败");
        }
    }

    /**
     * 获取 OSS 客户端（懒加载 + 配置校验）。
     *
     * @return OSS 客户端
     */
    private OSS getClient() {
        OSS local = ossClient;
        if (local == null) {
            synchronized (this) {
                local = ossClient;
                if (local == null) {
                    validateConfigured();
                    local = new OSSClientBuilder().build(
                            ossProperties.getEndpoint(), ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
                    ossClient = local;
                }
            }
        }
        return local;
    }

    /**
     * 校验 OSS 配置是否完整。
     *
     * @throws BusinessException 配置缺失时抛出 OSS_ERROR
     */
    private void validateConfigured() {
        if (isBlank(ossProperties.getEndpoint()) || isBlank(ossProperties.getBucket())
                || isBlank(ossProperties.getAccessKeyId()) || isBlank(ossProperties.getAccessKeySecret())) {
            throw new BusinessException(ErrorCode.OSS_ERROR, "OSS 尚未配置，请检查相关环境变量");
        }
    }

    /**
     * 去掉 endpoint 中的协议前缀，用于拼接直传地址。
     *
     * @return 纯域名形式的 endpoint
     */
    private String endpointHost() {
        String endpoint = ossProperties.getEndpoint();
        if (endpoint.startsWith("http://")) {
            return endpoint.substring("http://".length());
        }
        if (endpoint.startsWith("https://")) {
            return endpoint.substring("https://".length());
        }
        return endpoint;
    }

    /**
     * 归一化 key 前缀目录：去除首尾斜杠，保证以斜杠结尾。
     *
     * @param dir 原始目录
     * @return 归一化后的目录（空串表示不限目录）
     */
    private String normalizeDir(String dir) {
        String trimmed = dir == null ? "" : dir.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        return trimmed.isEmpty() ? "" : trimmed + "/";
    }

    /**
     * 将过期时间戳格式化为 OSS 要求的 ISO8601（UTC）。
     *
     * @param expireEndTime 过期时间戳（秒）
     * @return ISO8601 字符串
     */
    private String isoExpiration(long expireEndTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        return sdf.format(new Date(expireEndTime * 1000L));
    }

    /**
     * HMAC-SHA1 签名，结果 Base64 编码。
     *
     * @param key  密钥
     * @param data 待签名字符串
     * @return Base64 签名
     * @throws Exception 算法不支持或密钥非法时抛出
     */
    private String hmacSha1(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 判断字符串是否空白。
     *
     * @param s 待判断字符串
     * @return 是否为空白
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
