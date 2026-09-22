package com.chiji.module.clinic.service.strategy;

import java.time.LocalDate;

/**
 * 预约提醒策略接口（类型相关增强，呼应 ReminderStrategy 方向）。
 * <p>
 * 预约提醒是纯计算型、无状态提醒，不占 {@code clinic_visit} 表：
 * 「该预约复诊了」先于「明天复诊」，不同牙套类型的预约锚点不同——
 * 隐形用户是进度驱动（最终副计划结束日），传统用户是日历驱动（上次复诊 + 固定周期，二期）。
 * <p>
 * 扫描入口按用户治疗类型分发到首个 {@code supports} 命中的策略，加类型 = 加策略类。
 */
public interface BookingRemindStrategy {

    /**
     * 是否支持该治疗类型（值域为 UserTreatmentTypeEnum.name()）。
     *
     * @param treatmentType 治疗类型编码
     * @return 支持返回 true
     */
    boolean supports(String treatmentType);

    /**
     * 计算预约提醒日（今天 == 提醒日 时命中）。
     *
     * @param userId     用户 ID
     * @param offsetDays 提前天数 0~3（0=戴完当天提醒）
     * @return 提醒日；当前不在预约窗口（无 ACTIVE 阶段 / 非最终副 / 无计划数据）返回 null
     */
    LocalDate computeRemindDate(Long userId, int offsetDays);

    /**
     * 计算预约窗口锚点日（隐形 = 最终副计划结束日；传统用户二期 = 推算的下次复诊日）。
     * <p>
     * 供月视图渲染「预计戴完日」标记与预约提醒卡窗口判定复用，与提醒日解耦。
     *
     * @param userId 用户 ID
     * @return 锚点日；不在窗口返回 null
     */
    LocalDate computeWindowAnchor(Long userId);

    /**
     * 计算预约窗口锚点日（指定阶段版本）：仅当该阶段处于 ACTIVE 且佩戴最终副时返回其计划结束日。
     * <p>
     * 供月视图按「用户当前选中的阶段」渲染预约卡；不传 stageId 时回退 ACTIVE 阶段。
     *
     * @param userId  用户 ID
     * @param stageId 指定阶段 ID（null 回退 ACTIVE 阶段）
     * @return 锚点日；不在窗口返回 null
     */
    default LocalDate computeWindowAnchor(Long userId, Long stageId) {
        return computeWindowAnchor(userId);
    }

    /**
     * 计算阶段预计完成日 = 阶段最后一副的计划结束日（与当前佩戴到第几副无关）。
     * <p>
     * 语义与首页「预计完成日」一致（最后一副 endDate），供月历全周期标记；
     * 预约提醒仍以 {@link #computeWindowAnchor}（仅最终副）为准，中途不打扰。
     *
     * @param userId 用户 ID
     * @return 阶段预计完成日；无 ACTIVE 阶段 / 无副数据返回 null
     */
    default LocalDate computeStageExpectedEnd(Long userId) {
        return null;
    }

    /**
     * 计算阶段预计完成日（指定阶段版本）：首页下拉框选中阶段即「当前选中阶段」，
     * 选中历史阶段时返回该阶段最后一副计划结束日；不传 stageId 时回退 ACTIVE 阶段。
     *
     * @param userId  用户 ID
     * @param stageId 指定阶段 ID（null 回退 ACTIVE 阶段）
     * @return 阶段预计完成日；阶段不存在 / 无副数据返回 null
     */
    default LocalDate computeStageExpectedEnd(Long userId, Long stageId) {
        return computeStageExpectedEnd(userId);
    }
}
