package com.chiji.module.message.service;

/**
 * 摘下超时提醒下发与留痕服务（BE-6 下发 handler 的执行终端）。
 * <p>
 * 职责：取 openid → 组装模板数据（thing1 固定 / thing4 按 remindMode / time6 摘下时刻）→
 * 调 {@code WxSubscribeClient#sendTakeoffTimeoutMessage} 下发 → 复用 {@code message} 表留痕
 * （scene_date 置 NULL 避开 (user_id, reminder_type, scene_date) 唯一键：当日多次摘下各自成条；
 * {@code push_status = NONE} 先落，下发成功回写 {@code PUSHED + pushed_at}，失败保留 NONE）。
 */
public interface TakeoffNotifyService {

    /**
     * 下发命令（payload 解析结果，handler 组装）。
     *
     * @param userId       用户 ID
     * @param remindMode   提醒方式（HALF_HOUR/ONE_HOUR/SMART，仅用于日志追溯；文案已在 payload 定稿）
     * @param takeoffAtText 摘下时刻文本（{@code yyyy-MM-dd HH:mm}，模板 time6）
     * @param thing4       thing4 最终文案（payload 预先定稿，handler 不再改写）
     */
    record SendCommand(Long userId, String remindMode, String takeoffAtText, String thing4) {
    }

    /**
     * 下发结果。
     *
     * @param success true=微信 errcode==0
     * @param errcode 微信 errcode（本地异常为 -1；0=成功）
     * @param errmsg  微信 errmsg 或本地异常描述
     */
    record SendOutcome(boolean success, int errcode, String errmsg) {
    }

    /**
     * 下发摘下超时提醒订阅消息并留痕。
     * <p>
     * 调用前置条件（由 handler 保证）：会话围栏通过、当日额度已预扣成功。本方法失败
     * <b>不回补额度</b>（预扣模型：尝试即扣）。无论下发成败均落一条 message 留痕。
     *
     * @param cmd 下发命令
     * @return 下发结果（openid 缺失 / token 失败 / 微信拒绝均以 success=false 返回，不抛异常）
     */
    SendOutcome sendAndArchive(SendCommand cmd);
}
