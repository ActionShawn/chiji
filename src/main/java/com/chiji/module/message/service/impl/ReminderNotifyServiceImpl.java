package com.chiji.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.entity.Message;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.message.mapper.MessageMapper;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 提醒消息写入网关实现：总开关/类型开关/去重门控 + 落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderNotifyServiceImpl implements ReminderNotifyService {

    /** 推送状态：已推送（订阅消息下发成功）。 */
    private static final String PUSH_STATUS_PUSHED = "PUSHED";

    private final MessageMapper messageMapper;
    private final NotificationSettingService notificationSettingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean notify(Long userId, WearReminderTypeEnum type, LocalDate sceneDate, String title, String body) {
        if (type == null || type == WearReminderTypeEnum.WEAR_GUARD) {
            // WEAR_GUARD（状态守护）只在打卡页做页内弹窗，不进信箱
            return false;
        }
        // 门控：总开关 + 类型开关
        if (!notificationSettingService.isReminderAllowed(userId, type)) {
            return false;
        }
        // 去重：同 (user, type, sceneDate) 至多一条
        if (notificationSettingService.existsSceneMessage(userId, type, sceneDate)) {
            return false;
        }

        Message m = new Message();
        m.setUserId(userId);
        m.setCategory(type.toCategory().getCode());
        m.setReminderType(type.getCode());
        m.setSceneDate(sceneDate);
        m.setPriority(type.getPriority());
        m.setPushStatus("NONE");
        m.setTitle(title);
        m.setBody(body);
        m.setRead(false);
        try {
            messageMapper.insert(m);
        } catch (DuplicateKeyException e) {
            // 兜底并发去重（唯一索引 uk_message_user_type_date）
            log.info("提醒消息并发去重跳过, userId={}, type={}, sceneDate={}", userId, type.getCode(), sceneDate);
            return false;
        }
        log.info("提醒消息已归档, userId={}, type={}, sceneDate={}, msgId={}", userId, type.getCode(), sceneDate, m.getId());
        return true;
    }

    @Override
    public void markPushed(Long userId, WearReminderTypeEnum type, LocalDate sceneDate) {
        if (userId == null || type == null || sceneDate == null) {
            return;
        }
        int updated = messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getReminderType, type.getCode())
                .eq(Message::getSceneDate, sceneDate)
                .set(Message::getPushStatus, PUSH_STATUS_PUSHED)
                .set(Message::getPushedAt, WearTimes.now()));
        if (updated <= 0) {
            log.warn("回写消息推送状态未命中, userId={}, type={}, sceneDate={}", userId, type.getCode(), sceneDate);
        }
    }
}
