// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\module\stage\vo\MediaItemVO.java
package com.chiji.module.stage.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 记录媒体展示 VO（RecordVO.medias 数组的元素）。
 * <p>
 * 含 type/url/duration/sortOrder，供前端渲染缩略图与播放；type 区分 IMAGE/VIDEO，
 * 为后续 AI 多模态上下文按类型分类组装提供结构化数据。
 */
@Getter
@Builder
public class MediaItemVO {
    /** 媒体 ID */
    private final Long id;
    /** 媒体类型：IMAGE / VIDEO */
    private final String type;
    /** 可访问 URL */
    private final String url;
    /** 视频时长（秒），图片为 null */
    private final Integer duration;
    /** 展示排序，越小越靠前 */
    private final Integer sortOrder;
}
