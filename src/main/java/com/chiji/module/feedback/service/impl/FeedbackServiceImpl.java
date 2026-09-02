package com.chiji.module.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.page.CursorPage;
import com.chiji.entity.Feedback;
import com.chiji.entity.FeedbackImage;
import com.chiji.enums.FeedbackStatusEnum;
import com.chiji.module.feedback.dto.SubmitFeedbackRequest;
import com.chiji.module.feedback.mapper.FeedbackImageMapper;
import com.chiji.module.feedback.mapper.FeedbackMapper;
import com.chiji.module.feedback.service.FeedbackService;
import com.chiji.module.feedback.vo.FeedbackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 意见反馈服务实现。
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

    private final FeedbackMapper feedbackMapper;
    private final FeedbackImageMapper feedbackImageMapper;

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
        feedback.setStatus(FeedbackStatusEnum.PENDING.name());
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

        return toVO(feedback, savedImages);
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

        List<FeedbackVO> vos = pageRows.stream()
                .map(f -> toVO(f, imageMap.getOrDefault(f.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        Long nextLastId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new CursorPage<>(vos, nextLastId, hasMore);
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

    private FeedbackVO toVO(Feedback feedback, List<String> images) {
        FeedbackStatusEnum status = FeedbackStatusEnum.getByCode(feedback.getStatus());
        return new FeedbackVO(
                feedback.getId(),
                feedback.getContent(),
                feedback.getContactType(),
                feedback.getContact(),
                feedback.getStatus(),
                status == null ? feedback.getStatus() : status.getDesc(),
                images,
                feedback.getCreatedAt());
    }
}
