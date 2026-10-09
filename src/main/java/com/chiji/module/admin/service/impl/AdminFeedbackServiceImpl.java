package com.chiji.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.page.CursorPage;
import com.chiji.entity.Feedback;
import com.chiji.entity.FeedbackComment;
import com.chiji.entity.FeedbackImage;
import com.chiji.entity.User;
import com.chiji.enums.FeedbackStatusEnum;
import com.chiji.module.admin.dto.AdminReplyFeedbackRequest;
import com.chiji.module.admin.service.AdminFeedbackService;
import com.chiji.module.admin.vo.AdminFeedbackVO;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.feedback.dto.AddCommentRequest;
import com.chiji.module.feedback.mapper.FeedbackCommentMapper;
import com.chiji.module.feedback.mapper.FeedbackImageMapper;
import com.chiji.module.feedback.mapper.FeedbackMapper;
import com.chiji.module.feedback.service.FeedbackCommentLoader;
import com.chiji.module.feedback.support.FeedbackCommentSupport;
import com.chiji.module.feedback.vo.AddCommentResultVO;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.chiji.module.feedback.vo.FeedbackThreadVO;
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
 * <p>
 * 2026-10-08 对话化改造：回复 = 追加 ADMIN 评论（并镜像 reply 供历史版本客户端展示）；
 * 关闭/重开已禁用（闭环权归用户）。
 */
@Service
@RequiredArgsConstructor
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    /** 每页条数上限 */
    private static final int LIMIT_MAX = 50;

    private final FeedbackMapper feedbackMapper;
    private final FeedbackImageMapper feedbackImageMapper;
    private final FeedbackCommentMapper feedbackCommentMapper;
    private final UserMapper userMapper;

    @Override
    public CursorPage<AdminFeedbackVO> list(String status, int limit, Long cursor) {
        int pageSize = Math.min(Math.max(limit, 1), LIMIT_MAX);

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .orderByDesc(Feedback::getId)
                .last("LIMIT " + (pageSize + 1));
        applyStatusFilter(wrapper, status);
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

    /** 状态筛选：新三态直接匹配；兼容历史版本客户端传入的 PENDING/PROCESSED */
    private void applyStatusFilter(LambdaQueryWrapper<Feedback> wrapper, String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        if ("PENDING".equals(status)) {
            wrapper.eq(Feedback::getStatus, FeedbackStatusEnum.WAIT_ADMIN.getCode());
            return;
        }
        if ("PROCESSED".equals(status)) {
            wrapper.in(Feedback::getStatus, FeedbackStatusEnum.WAIT_USER.getCode(),
                    FeedbackStatusEnum.CLOSED.getCode());
            return;
        }
        FeedbackStatusEnum statusEnum = FeedbackStatusEnum.getByCode(status);
        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态参数不正确");
        }
        wrapper.eq(Feedback::getStatus, statusEnum.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackThreadVO<AdminFeedbackVO> thread(Long feedbackId) {
        Feedback feedback = requireFeedback(feedbackId);
        // 先清管理侧未读再拉取：晚于本次请求到达的新评论仍算未读
        feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getId, feedback.getId())
                .set(Feedback::getAdminReadAt, LocalDateTime.now()));
        feedback.setAdminReadAt(LocalDateTime.now());

        AdminFeedbackVO feedbackVO = toVO(feedback,
                loadUsers(Set.of(feedback.getUserId())).get(feedback.getUserId()),
                loadImages(List.of(feedback.getId()))
                        .getOrDefault(feedback.getId(), Collections.emptyList()));
        CursorPage<FeedbackCommentVO> page = FeedbackCommentLoader.loadPage(
                feedbackCommentMapper, feedback.getId(), LIMIT_MAX, null);
        return new FeedbackThreadVO<>(feedbackVO, page.items(), page.nextLastId(), page.hasMore());
    }

    @Override
    public CursorPage<FeedbackCommentVO> comments(Long feedbackId, int limit, Long cursor) {
        requireFeedback(feedbackId);
        return FeedbackCommentLoader.loadPage(feedbackCommentMapper, feedbackId, limit, cursor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddCommentResultVO addComment(Long feedbackId, AddCommentRequest request) {
        Feedback feedback = requireFeedback(feedbackId);
        if (FeedbackStatusEnum.getByCode(feedback.getStatus()) == FeedbackStatusEnum.CLOSED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该反馈已闭环，需用户追问后才能继续回复");
        }

        String content = FeedbackCommentSupport.normalizeContent(request.content());
        List<String> images = FeedbackCommentSupport.normalizeImages(request.imageUrls());
        FeedbackCommentSupport.requireMessage(content, images);

        FeedbackComment comment = new FeedbackComment();
        comment.setFeedbackId(feedbackId);
        comment.setUserId(0L);
        comment.setRole("ADMIN");
        comment.setContent(content.isBlank() ? null : content);
        comment.setImages(images.isEmpty() ? "" : FeedbackCommentSupport.toJson(images));
        feedbackCommentMapper.insert(comment);

        // 运营回复 → 待用户确认；同时镜像 reply/replied_at 供历史版本客户端展示
        feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getId, feedbackId)
                .set(Feedback::getStatus, FeedbackStatusEnum.WAIT_USER.getCode())
                .set(Feedback::getReply, content.isBlank() ? feedback.getReply() : content)
                .set(Feedback::getRepliedAt, comment.getCreatedAt() != null ? comment.getCreatedAt() : LocalDateTime.now())
                .set(Feedback::getClosedAt, null));
        return new AddCommentResultVO(FeedbackCommentSupport.toVO(comment),
                FeedbackStatusEnum.WAIT_USER.getCode(), FeedbackStatusEnum.WAIT_USER.getDesc());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminFeedbackVO reply(Long feedbackId, AdminReplyFeedbackRequest request) {
        // 历史接口兼容：等价于纯文本追加评论
        addComment(feedbackId, new AddCommentRequest(request.reply(), null));
        return assembleVO(requireFeedback(feedbackId));
    }

    @Override
    @Deprecated
    public AdminFeedbackVO close(Long feedbackId) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "闭环由用户点「已解决」完成，运营不可单方关闭");
    }

    @Override
    @Deprecated
    public AdminFeedbackVO reopen(Long feedbackId) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "状态流转由用户追问驱动，运营不可重开");
    }

    @Override
    public long unreadCount() {
        return feedbackCommentMapper.countAdminUnread();
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
