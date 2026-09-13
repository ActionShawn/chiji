package com.chiji.module.message.service;

import com.chiji.enums.WearReminderTypeEnum;

import java.time.LocalDate;

/**
 * 提醒消息写入网关（佩戴时长等其它模块依赖的唯一写信箱入口）。
 * <p>
 * 按通知设置门控写入并自动去重：
 * <ol>
 *   <li>总开关关闭或该类型关闭 → 跳过，返回 false</li>
 *   <li>同 (user, type, sceneDate) 已存在消息 → 跳过，返回 false</li>
 *   <li>否则落一条信箱消息（分类/优先级由 {@code type} 推导），返回 true</li>
 * </ol>
 * DND（勿扰）只抑制即时弹窗，不影响信箱落库。
 */
public interface ReminderNotifyService {

    /**
     * 生成并归档一条提醒消息。
     *
     * @param userId    接收用户 ID
     * @param type      提醒类型（WEAR_GUARD 不写信箱，将直接返回 false）
     * @param sceneDate 场景日期（同类型同日期去重）
     * @param title     标题
     * @param body      正文
     * @return 是否真正落库生成
     */
    boolean notify(Long userId, WearReminderTypeEnum type, LocalDate sceneDate, String title, String body);

    /**
     * 回写消息推送状态为已推送（{@code push_status='PUSHED'} + {@code pushed_at=now}）。
     * <p>
     * 供订阅消息下发成功（微信 {@code errcode == 0}）后调用；消息行按
     * {@code (userId, type, sceneDate)} 定位（唯一索引保证至多一条）。
     *
     * @param userId    接收用户 ID
     * @param type      提醒类型
     * @param sceneDate 场景日期
     */
    void markPushed(Long userId, WearReminderTypeEnum type, LocalDate sceneDate);
}
