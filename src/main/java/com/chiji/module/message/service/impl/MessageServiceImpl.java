package com.chiji.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Message;
import com.chiji.module.message.mapper.MessageMapper;
import com.chiji.module.message.service.MessageService;
import com.chiji.module.message.vo.MessagePageVO;
import com.chiji.module.message.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 信箱消息服务实现（只读 + 已读/删除，写入统一走 {@code ReminderNotifyService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    /** 每页条数上限。 */
    private static final int PAGE_SIZE_MAX = 50;
    /** 每页条数缺省。 */
    private static final int PAGE_SIZE_DEFAULT = 20;

    private final MessageMapper messageMapper;

    @Override
    public MessagePageVO listMessages(Long userId, Long cursor, Integer pageSize, String category) {
        int limit = pageSize == null || pageSize <= 0 ? PAGE_SIZE_DEFAULT : Math.min(pageSize, PAGE_SIZE_MAX);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(category != null && !category.isBlank(), Message::getCategory, category)
                .lt(cursor != null, Message::getId, cursor)
                .orderByDesc(Message::getId)
                .last("LIMIT " + (limit + 1));
        List<Message> rows = messageMapper.selectList(wrapper);

        boolean hasMore = rows.size() > limit;
        List<Message> page = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        List<MessageVO> items = page.stream().map(MessageVO::from).toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId() : null;

        long unread = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getRead, false)
                .eq(category != null && !category.isBlank(), Message::getCategory, category));
        return new MessagePageVO(items, nextCursor, hasMore, (int) unread);
    }

    @Override
    public long countUnread(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getRead, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long messageId) {
        Message m = messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getUserId, userId));
        if (m == null) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "消息不存在");
        }
        if (!Boolean.TRUE.equals(m.getRead())) {
            messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                    .eq(Message::getId, messageId)
                    .set(Message::getRead, true));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getUserId, userId)
                .eq(Message::getRead, false)
                .set(Message::getRead, true));
        log.info("信箱全部已读, userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long userId, Long messageId) {
        int affected = messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getUserId, userId));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "消息不存在");
        }
    }
}
