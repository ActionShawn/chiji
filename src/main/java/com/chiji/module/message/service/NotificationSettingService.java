package com.chiji.module.message.service;

import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.message.dto.NotificationUpdateRequest;
import com.chiji.module.message.vo.NotificationSettingsVO;

import java.time.LocalDate;

/**
 * 通知设置服务：单值偏好 + 每类型开关明细的读写与预设推导。
 * <p>
 * 单值偏好（总开关/弹窗/红点/预设/勿扰时段）与目标等共存于 {@code user_setting}；
 * 每类型开关明细存 {@code user_notification_config}（未落行的类型按
 * {@link WearReminderTypeEnum#isDefaultOn()} 默认值呈现）。本服务同时向同模块的
 * {@link ReminderNotifyService} 提供开关门控判定。
 */
public interface NotificationSettingService {

    /**
     * 整读通知设置。
     *
     * @param userId 当前用户 ID
     * @return 通知设置整读 VO（行不存在时按默认值返回，不写库）
     */
    NotificationSettingsVO getSettings(Long userId);

    /**
     * 局部更新通知设置并返回更新后的整读。
     *
     * @param userId 当前用户 ID
     * @param req    更新请求（未提供字段保持不变；preset 一键应用 / types 逐项覆盖）
     * @return 更新后的整读 VO
     */
    NotificationSettingsVO updateSettings(Long userId, NotificationUpdateRequest req);

    /**
     * 通知生成门控：总开关开启 <b>且</b> 该提醒类型开关开启时才应生成/归档消息。
     * <p>
     * WEAR_GUARD（状态守护）不进信箱，是否弹窗由调用方另按本判定 + 弹窗开关 + 勿扰判断。
     *
     * @param userId 用户 ID
     * @param type   提醒类型
     * @return true 表示允许生成
     */
    boolean isReminderAllowed(Long userId, WearReminderTypeEnum type);

    /**
     * 场景日期内是否已存在该类型消息（去重；供 ReminderNotifyService 复用）。
     *
     * @param userId  用户 ID
     * @param type    提醒类型
     * @param sceneDate 场景日期
     * @return 是否已存在
     */
    boolean existsSceneMessage(Long userId, WearReminderTypeEnum type, LocalDate sceneDate);
}
