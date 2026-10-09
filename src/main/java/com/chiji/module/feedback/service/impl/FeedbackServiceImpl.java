package com.chiji.module.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.page.CursorPage;
import com.chiji.entity.Feedback;
import com.chiji.entity.FeedbackComment;
import com.chiji.entity.FeedbackImage;
import com.chiji.enums.FeedbackStatusEnum;
import com.chiji.module.feedback.dto.AddCommentRequest;
import com.chiji.module.feedback.dto.SubmitFeedbackRequest;
import com.chiji.module.feedback.mapper.FeedbackCommentMapper;
import com.chiji.module.feedback.mapper.FeedbackImageMapper;
import com.chiji.module.feedback.mapper.FeedbackMapper;
import com.chiji.module.feedback.service.FeedbackCommentLoader;
import com.chiji.module.feedback.service.FeedbackService;
import com.chiji.module.feedback.support.FeedbackCommentSupport;
import com.chiji.module.feedback.vo.AddCommentResultVO;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.chiji.module.feedback.vo.FeedbackStatusVO;
import com.chiji.module.feedback.vo.FeedbackThreadVO;
import com.chiji.module.feedback.vo.FeedbackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 意见反馈服务实现（用户端：提交/历史/对话线程/闭环）。
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    /** 大陆手机号：1 开头，第二位 3-9，共 11 位 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** 常规邮箱格式 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** 每页条数上限 */
    private static final int LIMIT_MAX = 50;
    /** 闭环撤回窗口（用户确认拍板：5 秒） */
    private static final Duration REVERT_WINDOW = Duration.ofSeconds(5);

    private final FeedbackMapper feedbackMapper;
    private final FeedbackImageMapper feedbackImageMapper;
    private final FeedbackCommentMapper feedbackCommentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO submit(Long userId, SubmitFeedbackRequest request) {
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容最多500字");
        }

        // 联系方式：留空或「手机号 / 邮箱」二选一
        String contactType = request.contactType();
        String contact = request.contact();
        if (contact != null && !contact.isBlank()) {
            contact = contact.trim();
            if ("PHONE".equals(contactType)) {
                if (!PHONE_PATTERN.matcher(contact).matches()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
                }
            } else if ("EMAIL".equals(contactType)) {
                if (!EMAIL_PATTERN.matcher(contact).matches()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式不正确");
                }
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "联系方式类型不正确");
            }
        } else {
            contact = null;
            contactType = null;
        }

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedback.setContactType(contactType);
        feedback.setContact(contact);
        feedback.setStatus(FeedbackStatusEnum.WAIT_ADMIN.name());
        feedbackMapper.insert(feedback);

        List<String> imageUrls = request.imageUrls() == null ? List.of() : request.imageUrls();
        List<String> savedImages = new ArrayList<>();
        int sortOrder = 0;
        for (String url : imageUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String trimmed = url.trim();
            savedImages.add(trimmed);
            FeedbackImage image = new FeedbackImage();
            image.setFeedbackId(feedback.getId());
            image.setUrl(trimmed);
            image.setSortOrder(sortOrder++);
            feedbackImageMapper.insert(image);
        }

        return toVO(feedback, savedImages, false);
    }

    @Override
    public CursorPage<FeedbackVO> listByUser(Long userId, int limit, Long cursor) {
        int pageSize = Math.min(Math.max(limit, 1), LIMIT_MAX);

        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursor != null) {
            wrapper.lt(Feedback::getId, cursor);
        }

        List<Feedback> rows = feedbackMapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageSize;
        List<Feedback> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        if (pageRows.isEmpty()) {
            return CursorPage.empty();
        }

        // 批量查询图片，按 feedbackId 分组
        List<Long> feedbackIds = pageRows.stream().map(Feedback::getId).collect(Collectors.toList());
        Map<Long, List<String>> imageMap = loadImages(feedbackIds);
        // 批量判定未读运营回复（一次查询，避免逐条 count 的 N+1）
        Set<Long> unreadIds = feedbackCommentMapper.selectUnreadFeedbackIds(userId, feedbackIds);

        List<FeedbackVO> vos = pageRows.stream()
                .map(f -> toVO(f, imageMap.getOrDefault(f.getId(), Collections.emptyList()),
                        unreadIds.contains(f.getId())))
                .collect(Collectors.toList());

        Long nextLastId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new CursorPage<>(vos, nextLastId, hasMore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackThreadVO<FeedbackVO> thread(Long userId, Long feedbackId) {
        Feedback feedback = requireOwned(userId, feedbackId);
        // 先清未读再拉取：晚于本次请求到达的新评论仍算未读（宁可多亮一次红点，不可漏消息）
        feedbackMapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getId, feedback.getId())
                .set(Feedback::getUserReadAt, LocalDateTime.now()));
        feedback.setUserReadAt(LocalDateTime.now());

        FeedbackVO feedbackVO = toVO(feedback, loadImages(List.of(feedback.getId()))
                .getOrDefault(feedback.getId(), Collections.emptyList()), false);
        CursorPage<FeedbackCommentVO> page = FeedbackCommentLoader.loadPage(
                feedbackCommentMapper, feedback.getId(), LIMIT_MAX, null);
        return new FeedbackThreadVO<>(feedbackVO, page.items(), page.nextLastId(), page.hasMore());
    }

    @Override
    public CursorPage<FeedbackCommentVO> comments(Long userId, Long feedbackId, int limit, Long cursor) {
        requireOwned(userId, feedbackId);
        return FeedbackCommentLoader.loadPage(feedbackCommentMapper, feedbackId, limit, cursor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AddCommentResultVO addComment(Long userId, Long feedbackId, AddCommentRequest request) {
        Feedback feedback = requireOwned(userId, feedbackId);

        String content = FeedbackCommentSupport.normalizeContent(request.content());
        List<String> images = FeedbackCommentSupport.normalizeImages(request.imageUrls());
        FeedbackCommentSupport.requireMessage(content, images);

        FeedbackComment comment = insertComment(feedbackId, userId, FeedbackCommentRole.USER, content, images);

        // 用户发言 → 等运营回复（WAIT_USER/CLOSED 下均为追问，CLOSED 自动复活）
        updateStatus(feedbackId, FeedbackStatusEnum.WAIT_ADMIN, null);
        return new AddCommentResultVO(FeedbackCommentSupport.toVO(comment),
                FeedbackStatusEnum.WAIT_ADMIN.getCode(), FeedbackStatusEnum.WAIT_ADMIN.getDesc());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackStatusVO resolve(Long userId, Long feedbackId) {
        Feedback feedback = requireOwned(userId, feedbackId);
        FeedbackStatusEnum current = FeedbackStatusEnum.getByCode(feedback.getStatus());
        if (current == FeedbackStatusEnum.CLOSED) {
            // 幂等：已闭环直接返回（不刷新闭环时刻，避免撤回窗口被反复延长）
            return statusVO(FeedbackStatusEnum.CLOSED, null);
        }
        updateStatus(feedbackId, FeedbackStatusEnum.CLOSED, LocalDateTime.now());
        return statusVO(FeedbackStatusEnum.CLOSED, REVERT_WINDOW.toSeconds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackStatusVO revertResolve(Long userId, Long feedbackId) {
        Feedback feedback = requireOwned(userId, feedbackId);
        if (FeedbackStatusEnum.getByCode(feedback.getStatus()) != FeedbackStatusEnum.CLOSED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前不在闭环状态");
        }
        if (feedback.getClosedAt() == null
                || Duration.between(feedback.getClosedAt(), LocalDateTime.now()).compareTo(REVERT_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已超过撤回时间");
        }
        // 撤回回闭环前的状态：有过运营消息 → 待你确认；从未回复过 → 等回复
        FeedbackStatusEnum prev = hasAdminMessage(feedback)
                ? FeedbackStatusEnum.WAIT_USER : FeedbackStatusEnum.WAIT_ADMIN;
        updateStatus(feedbackId, prev, null);
        return statusVO(prev, null);
    }

    @Override
    public long unreadCount(Long userId) {
        return feedbackCommentMapper.countUserUnread(userId);
    }

    // ── 内部工具 ────────────────────────────────────────────

    /** 发言人角色（与库内 role 字段值一致，避免循环引入独立枚举类） */
    private enum FeedbackCommentRole {
        USER, ADMIN
    }

    /** 校验反馈存在且归属当前用户 */
    private Feedback requireOwned(Long userId, Long feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null || !userId.equals(feedback.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "反馈不存在");
        }
        return feedback;
    }

    /** 插入一条评论消息 */
    private FeedbackComment insertComment(Long feedbackId, Long senderUserId, FeedbackCommentRole role,
                                          String content, List<String> images) {
        FeedbackComment comment = new FeedbackComment();
        comment.setFeedbackId(feedbackId);
        comment.setUserId(role == FeedbackCommentRole.ADMIN ? 0L : senderUserId);
        comment.setRole(role.name());
        comment.setContent(content == null || content.isBlank() ? null : content);
        comment.setImages(images == null || images.isEmpty() ? "" : FeedbackCommentSupport.toJson(images));
        feedbackCommentMapper.insert(comment);
        return comment;
    }

    /** 条件更新状态（仅动状态列与闭环时刻，避免整行覆盖与并发评论流转互相踩踏） */
    private void updateStatus(Long feedbackId, FeedbackStatusEnum status, LocalDateTime closedAt) {
        LambdaUpdateWrapper<Feedback> wrapper = new LambdaUpdateWrapper<Feedback>()
                .eq(Feedback::getId, feedbackId)
                .set(Feedback::getStatus, status.getCode());
        if (closedAt != null) {
            wrapper.set(Feedback::getClosedAt, closedAt);
        } else if (status != FeedbackStatusEnum.CLOSED) {
            wrapper.set(Feedback::getClosedAt, null);
        }
        feedbackMapper.update(null, wrapper);
    }

    /** 是否已有运营消息（旧镜像 reply 或 ADMIN 评论） */
    private boolean hasAdminMessage(Feedback feedback) {
        if (feedback.getReply() != null && !feedback.getReply().isBlank()) {
            return true;
        }
        return feedbackCommentMapper.selectCount(new LambdaQueryWrapper<FeedbackComment>()
                .eq(FeedbackComment::getFeedbackId, feedback.getId())
                .eq(FeedbackComment::getRole, FeedbackCommentRole.ADMIN.name())) > 0;
    }

    private FeedbackStatusVO statusVO(FeedbackStatusEnum status, Long revertSeconds) {
        return new FeedbackStatusVO(status.getCode(), status.getDesc(), revertSeconds);
    }

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

    private FeedbackVO toVO(Feedback feedback, List<String> images, boolean unread) {
        FeedbackStatusEnum status = FeedbackStatusEnum.getByCode(feedback.getStatus());
        return new FeedbackVO(
                feedback.getId(),
                feedback.getContent(),
                feedback.getContactType(),
                feedback.getContact(),
                feedback.getStatus(),
                status == null ? feedback.getStatus() : status.getDesc(),
                feedback.getReply(),
                feedback.getRepliedAt(),
                images,
                feedback.getCreatedAt(),
                unread);
    }
}
