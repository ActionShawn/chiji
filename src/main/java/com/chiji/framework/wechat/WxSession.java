// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\wechat\WxSession.java
package com.chiji.framework.wechat;

/**
 * 微信 {@code jscode2session} 接口返回。
 * <p>
 * 成功时 errcode 为 0（缺失视为成功），openid 为用户的唯一标识；
 * 失败时 errcode 非 0，errmsg 携带错误说明。
 *
 * @param openid     用户唯一标识
 * @param sessionKey 会话密钥
 * @param unionid    用户在开放平台下的唯一标识（未绑定开放平台时为 null）
 * @param errcode    错误码
 * @param errmsg     错误信息
 */
public record WxSession(String openid, String sessionKey, String unionid, Integer errcode, String errmsg) {
}
