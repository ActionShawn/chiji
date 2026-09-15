package com.chiji.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.page.CursorPage;
import com.chiji.entity.Feedback;
import com.chiji.entity.FeedbackImage;
import com.chiji.entity.User;
import com.chiji.enums.FeedbackStatusEnum;
import com.chiji.module.admin.dto.AdminReplyFeedbackRequest;
import com.chiji.module.admin.service.AdminFeedbackService;
import com.chiji.module.admin.vo.AdminFeedbackVO;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.feedback.mapper.FeedbackImageMapper;
import com.chiji.module.feedback.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端反馈服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    /** 每页条数上限 */
    private static final int LIMIT_MAX = 50;

    private final FeedbackMapper feedbackMapper;
    private final FeedbackImageMapper feedbackImageMapper;
    private final UserMapper userMapper;

    @Override
    public CursorPage<AdminFeedbackVO> list(String status, int limit, Long cursor) {
        int pageSize = Math.min(Math.max(limit, 1), LIMIT_MAX);

        FeedbackStatusEnum statusEnum = null;
        if (status != null && !status.isBlank()) {
            statusEnum = FeedbackStatusEnum.getByCode(status);
            if (statusEnum == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "状态参数不正确");
            }
        }

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .orderByDesc(Feedback::getId)
                .last("LIMIT " + (pageSize + 1));
        if (statusEnum != null) {
            wrapper.eq(Feedback::getStatus, statusEnum.getCode());
        }
        if (cursor != null) {
            wrapper.lt(Feedback::getId, cursor);
        }

        List<Feedback> rows = feedbackMapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageSize;
        List<Feedback> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        if (pageRows.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> feedbackIds = pageRows.stream().map(Feedback::getId).collect(Collectors.toList());
        Map<Long, List<String>> imageMap = loadImages(feedbackIds);
        Map<Long, User> userMap = loadUsers(pageRows.stream().map(Feedback::getUserId).collect(Collectors.toSet()));

        List<AdminFeedbackVO> vos = pageRows.stream()
                .map(f -> toVO(f, userMap.get(f.getUserId()), imageMap.getOrDefault(f.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        Long nextLastId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new CursorPage<>(vos, nextLastId, hasMore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminFeedbackVO reply(Long feedbackId, AdminReplyFeedbackRequest request) {
        Feedback feedback = requireFeedback(feedbackId);

        String reply = request.reply() == null ? "" : request.reply().trim();
        if (reply.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "回复内容不能为空");
        }

        feedback.setReply(reply);
        feedback.setRepliedAt(LocalDateTime.now());
        feedback.setStatus(FeedbackStatusEnum.PROCESSED.name());
        feedbackMapper.updateById(feedback);

        return assembleVO(feedback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminFeedbackVO close(Long feedbackId) {
        Feedback feedback = requireFeedback(feedbackId);
        feedback.setStatus(FeedbackStatusEnum.PROCESSED.name());
        feedbackMapper.updateById(feedback);
        return assembleVO(feedback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminFeedbackVO reopen(Long feedbackId) {
        Feedback feedback = requireFeedback(feedbackId);
        feedback.setStatus(FeedbackStatusEnum.PENDING.name());
        feedbackMapper.updateById(feedback);
        return assembleVO(feedback);
    }

    private Feedback requireFeedback(Long feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "反馈不存在");
        }
        return feedback;
    }

    /** 组装单条 VO（含图片与用户信息）。 */
    private AdminFeedbackVO assembleVO(Feedback feedback) {
        Map<Long, List<String>> imageMap = loadImages(List.of(feedback.getId()));
        Map<Long, User> userMap = loadUsers(Set.of(feedback.getUserId()));
        return toVO(feedback, userMap.get(feedback.getUserId()),
                imageMap.getOrDefault(feedback.getId(), Collections.emptyList()));
    }

    /** 批量查询反馈图片，按 feedbackId 分组（按 sortOrder 升序）。 */
    private Map<Long, List<String>> loadImages(List<Long> feedbackIds) {
        if (feedbackIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<FeedbackImage> images = feedbackImageMapper.selectList(
                new LambdaQueryWrapper<FeedbackImage>()
                        .in(FeedbackImage::getFeedbackId, feedbackIds)
                        .orderByAsc(FeedbackImage::getSortOrder)
                        .orderByAsc(FeedbackImage::getId));
        return images.stream().collect(Collectors.groupingBy(
                FeedbackImage::getFeedbackId,
                Collectors.mapping(FeedbackImage::getUrl, Collectors.toList())));
    }

    /** 批量查询提交用户（昵称/openid），按 userId 索引。 */
    private Map<Long, User> loadUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    /** openid 脱敏：保留前4后4，如 wx_o***xyz；过短时仅保留前2位。 */
    private String maskOpenid(String openid) {
        if (openid == null || openid.isEmpty()) {
            return null;
        }
        if (openid.length() <= 8) {
            return openid.substring(0, Math.min(2, openid.length())) + "***";
        }
        return openid.substring(0, 4) + "***" + openid.substring(openid.length() - 4);
    }

    private AdminFeedbackVO toVO(Feedback feedback, User user, List<String> images) {
        FeedbackStatusEnum status = FeedbackStatusEnum.getByCode(feedback.getStatus());
        return new AdminFeedbackVO(
                feedback.getId(),
                feedback.getUserId(),
                user == null ? null : user.getNickname(),
                user == null ? null : maskOpenid(user.getOpenid()),
                feedback.getContent(),
                feedback.getStatus(),
                status == null ? feedback.getStatus() : status.getDesc(),
                feedback.getReply(),
                feedback.getRepliedAt(),
                images,
                feedback.getCreatedAt());
    }
}
