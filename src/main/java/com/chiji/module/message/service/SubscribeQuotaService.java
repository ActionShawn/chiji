package com.chiji.module.message.service;

/**
 * 微信一次性订阅消息额度记账服务。
 * <p>
 * 微信不提供剩余额度查询接口，故额度由本地表 {@code user_subscribe_quota} 累加：
 * 用户授权（accept）一次 +1，微信下发成功一次 -1；下发失败不扣减。
 */
public interface SubscribeQuotaService {

    /**
     * 记录一次用户授权（accept）。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景（提醒类型 code，当前仅 ALIGNER_CHANGE）
     */
    void recordAccept(Long userId, String scene);

    /**
     * 消费一次额度（仅在有额度且下发成功后调用）。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景
     * @return 扣减成功返回 true；无可用额度返回 false
     */
    boolean consumeIfAvailable(Long userId, String scene);

    /**
     * 查询剩余额度。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景
     * @return 剩余可下发次数，无记录返回 0
     */
    int remain(Long userId, String scene);
}
