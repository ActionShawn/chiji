package com.chiji.module.clinic.service;

/**
 * 小齿档案提醒扫描服务（每小时整点由 ReminderScheduledJob 调用）。
 * <p>
 * 两条提醒链共用 {@code isClinicRemindHour} 的整点门控（时刻存
 * {@code user_setting.clinic_remind_time}，就诊/预约共用）：
 * <ul>
 *   <li>就诊提醒（全类型）：已确认 PLANNED 日程到期前 N 天（默认当天），每条仅一次</li>
 *   <li>预约提醒（仅隐形最终副）：最终副计划结束日前 N 天（默认前 3 天），每阶段仅一次；
 *       已存在未来 PLANNED 日程则跳过（预约已落实，不再催）</li>
 * </ul>
 * 双通道：信箱站内必达（notify 内置总开关/类型开关门控 + (user,type,sceneDate) 去重）；
 * 订阅消息额度 &gt; 0 才发，成功扣额度并回写推送状态。
 */
public interface ClinicReminderService {

    /**
     * 就诊提醒扫描：命中「今天 == visit_date − 提前天数」的最近一条 PLANNED 则提醒。
     *
     * @param userId 用户 ID
     * @return 是否产生提醒
     */
    boolean visitRemind(Long userId);

    /**
     * 预约提醒扫描：命中「今天 == 窗口锚点 − 提前天数」且无未来 PLANNED 日程则提醒。
     *
     * @param userId 用户 ID
     * @return 是否产生提醒
     */
    boolean bookRemind(Long userId);
}
