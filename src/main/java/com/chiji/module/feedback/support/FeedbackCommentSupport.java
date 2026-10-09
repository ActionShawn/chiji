package com.chiji.module.feedback.support;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.FeedbackComment;
import com.chiji.module.feedback.vo.FeedbackCommentVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 反馈评论公共校验/序列化工具（用户端与管理端共用，避免两处实现漂移）。
 */
public final class FeedbackCommentSupport {

    /** 评论正文最大字数（与提交反馈一致） */
    public static final int CONTENT_MAX = 500;

    /** 单条评论图片上限 */
    public static final int IMAGE_MAX = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeedbackCommentSupport() {
    }

    /**
     * 归一并校验正文：trim 后返回（null → 空串）。
     *
     * @throws BusinessException 超过 {@link #CONTENT_MAX} 字
     */
    public static String normalizeContent(String content) {
        String value = content == null ? "" : content.trim();
        if (value.length() > CONTENT_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容最多" + CONTENT_MAX + "字");
        }
        return value;
    }

    /**
     * 归一并校验图片：逐个 trim、丢弃空白项。
     *
     * @throws BusinessException 超过 {@link #IMAGE_MAX} 张
     */
    public static List<String> normalizeImages(List<String> urls) {
        List<String> cleaned = new ArrayList<>();
        if (urls == null) {
            return cleaned;
        }
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            cleaned.add(url.trim());
        }
        if (cleaned.size() > IMAGE_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "最多上传" + IMAGE_MAX + "张图片");
        }
        return cleaned;
    }

    /**
     * 正文与图片至少一项非空（允许仅图片）。
     *
     * @throws BusinessException 两者都为空
     */
    public static void requireMessage(String content, List<String> images) {
        if ((content == null || content.isBlank()) && (images == null || images.isEmpty())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入内容或添加图片");
        }
    }

    /** 图片列表 → JSON 数组字符串（空列表返回空串） */
    public static String toJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(images);
        } catch (Exception e) {
            throw new IllegalStateException("图片列表序列化失败", e);
        }
    }

    /** JSON 数组字符串 → 图片列表（null/空/格式异常均返回空列表，容错不抛错） */
    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 评论实体 → VO（图片 JSON 解析容错） */
    public static FeedbackCommentVO toVO(FeedbackComment comment) {
        String content = comment.getContent();
        return new FeedbackCommentVO(
                comment.getId(),
                comment.getRole(),
                content == null || content.isBlank() ? null : content,
                fromJson(comment.getImages()),
                comment.getCreatedAt());
    }

}
