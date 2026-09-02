// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\oss\OssPostPolicy.java
package com.chiji.framework.oss;

/**
 * OSS 直传 POST 签名参数。
 * <p>
 * 前端拿到本参数后，以 multipart/form-data 向 {@link #host()} 提交直传请求，
 * 表单字段依次为 {@code key}、{@code policy}、{@code OSSAccessKeyId}、{@code signature}。
 *
 * @param accessId  AccessKeyId
 * @param host      直传地址（https://bucket.endpoint）
 * @param policy    经 Base64 编码的 policy 文档
 * @param signature HMAC-SHA1 签名
 * @param expire    policy 过期时间戳（秒）
 * @param dir       允许上传的 key 前缀目录
 */
public record OssPostPolicy(String accessId, String host, String policy, String signature, Long expire, String dir) {
}
