package com.chiji.module.message.service;

/**
 * 微信一次性订阅消息额度记账服务（记账模型按场景分流）。
 * <p>
 * 微信不提供剩余额度查询接口，额度由本地表 {@code user_subscribe_quota} 记账，两类模型：
 * <ul>
 *   <li><b>常驻场景</b>（ALIGNER_CHANGE / CLINIC_VISIT_REMIND / CLINIC_BOOK_REMIND）——生命周期累计：
 *       授权（accept）一次 +1，微信下发成功一次 -1，下发失败不扣减，额度跨授权长期累积</li>
 *   <li><b>{@link #SCENE_TAKEOFF_TIMEOUT}</b>（摘下超时提醒）——当日预扣模型（PRD 4.1，2026-10-08 修订）：
 *       <ul>
 *         <li>授权 +1 记入当日；下发<b>尝试即预扣</b>（{@code remain - 1}），下发失败<b>不回补</b></li>
 *         <li>跨自然日（Asia/Shanghai）惰性清零：{@code quota_date} 非当日或 NULL 的行在下一次记账 /
 *             预扣触达时原子重置（{@code remain = 0, quota_date = 当日}），存量 NULL 行视为「需重置」而非报错</li>
 *         <li>授权调起判断口径为<b>当日累计授权数 &lt; {@link #TAKEOFF_DAILY_ACCEPT_LIMIT}</b>
 *             （每日上限约束的是累计授权数而非剩余额度，防止预扣后剩余额度回升导致重复弹窗，
 *             2026-10-08 排期评审 C1 拍板）；剩余额度仅用于通知设置页「今日剩余提醒次数」展示</li>
 *       </ul></li>
 * </ul>
 */
public interface SubscribeQuotaService {

    /** 摘下超时提醒订阅场景（当日预扣模型，与其余常驻场景记账策略不同，见类注释） */
    String SCENE_TAKEOFF_TIMEOUT = "TAKEOFF_TIMEOUT";

    /** 摘下超时提醒每日授权上限：当日累计授权数达到该值后前端不再调起授权弹窗（PRD 默认 3） */
    int TAKEOFF_DAILY_ACCEPT_LIMIT = 3;

    /**
     * TAKEOFF_TIMEOUT 当日额度快照（单查询同时返回两口径 + 当日授权计数）。
     *
     * @param remainToday   当日剩余可下发额度（授权后未预扣的次数；通知设置页「今日剩余提醒次数」展示用，
     *                      跨天 / 无记录按 0）
     * @param acceptedToday 当日累计授权数（授权调起判断口径：{@code acceptedToday < TAKEOFF_DAILY_ACCEPT_LIMIT}；
     *                      跨天 / 无记录按 0）
     */
    record TakeoffDailyQuota(int remainToday, int acceptedToday) {
    }

    /**
     * 记录一次用户授权（accept）。
     * <p>
     * 常驻场景：生命周期累计 +1；{@link #SCENE_TAKEOFF_TIMEOUT}：+1 记入当日（跨天先惰性清零再计 1）。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景（{@link WearReminderTypeEnum} code 或 {@link #SCENE_TAKEOFF_TIMEOUT}）
     */
    void recordAccept(Long userId, String scene);

    /**
     * 消费一次额度。
     * <p>
     * 常驻场景：仅在微信下发成功后调用（失败不扣）；{@link #SCENE_TAKEOFF_TIMEOUT}：下发前<b>预扣</b>，
     * 下发尝试即扣减、失败不回补。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景
     * @return 扣减成功返回 true；无可用额度返回 false
     */
    boolean consumeIfAvailable(Long userId, String scene);

    /**
     * 查询剩余额度。
     * <p>
     * 常驻场景：生命周期剩余；{@link #SCENE_TAKEOFF_TIMEOUT}：当日剩余可下发额度（跨天 / 无记录返回 0）。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景
     * @return 剩余可下发次数，无记录返回 0
     */
    int remain(Long userId, String scene);

    /**
     * 查询 {@link #SCENE_TAKEOFF_TIMEOUT} 当日额度快照（两口径一次返回，见 {@link TakeoffDailyQuota}）。
     * <p>
     * 只读不落库：跨天 / 存量 NULL 行按「需重置」折算为双 0，物理重置延迟至下一次记账 / 预扣触达。
     *
     * @param userId 用户 ID
     * @return 当日额度快照；入参非法按双 0 处理
     */
    TakeoffDailyQuota takeoffDailyQuota(Long userId);
}
