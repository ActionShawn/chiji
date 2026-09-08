package com.chiji.module.message.vo;

import java.time.ZoneId;
import com.chiji.entity.Message;

/**
 * 信箱消息 VO。
 *
 * @param id           消息 ID
 * @param category     分类（MessageCategoryEnum code，大写：SYSTEM_CARE / WEAR 等）
 * @param title        标题
 * @param body         正文
 * @param read         是否已读（对应前端 status unread/read）
 * @param createdAt    创建时间（epoch 毫秒，Asia/Shanghai），前端按天分组
 * @param reminderType 提醒类型（WearReminderTypeEnum code，存量消息为 null）
 * @param sceneDate    场景日期（yyyy-MM-dd，去重维度；存量消息为 null）
 * @param priority     优先级 0 低 / 1 普通 / 2 高
 */
public record MessageVO(
        Long id,
        String category,
        String title,
        String body,
        Boolean read,
        Long createdAt,
        String reminderType,
        String sceneDate,
        Integer priority
) {

    /** 由消息实体组装。 */
    public static MessageVO from(Message m) {
        Long created = m.getCreatedAt() == null ? null
                : m.getCreatedAt().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        return new MessageVO(
                m.getId(),
                m.getCategory(),
                m.getTitle(),
                m.getBody(),
                m.getRead(),
                created,
                m.getReminderType(),
                m.getSceneDate() == null ? null : m.getSceneDate().toString(),
                m.getPriority());
    }
}
