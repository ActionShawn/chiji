package com.chiji.module.stage.vo;

import lombok.Builder;

/**
 * 牙套副缩略展示 VO（项目卡片下方的印记预览）。
 * <p>
 * 对应前端 {@code alignerNodes[].thumbs[]} 的单条元素。
 *
 * @param recordId 记录 ID（雪花算法，序列化为字符串），点击缩略图可拉取完整记录
 * @param type     缩略类型：photo / text
 * @param text     文字摘要（type=text 时有值）
 * @param date     日期文案（如「08.23 14:30」）
 * @param url      媒体 URL（type=photo 时有值）
 */
@Builder
public record AlignerThumbVO(Long recordId, String type, String text, String date, String url) {
}
