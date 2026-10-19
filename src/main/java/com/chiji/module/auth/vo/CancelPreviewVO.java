package com.chiji.module.auth.vo;

/**
 * 注销挽留屏数据盘点 VO（只读，供「你即将失去什么」的具象化）。
 * <p>
 * 四字段口径见方案 §5.1；前端为 0 的片段不渲染。{@code daysWithUs} 最小取 1
 * （当天注册当天注销也显示「陪伴 1 天」）。
 */
public record CancelPreviewVO(

        /** 已记录天数：COUNT(DISTINCT record_date) */
        long recordDays,

        /** 照片/视频数：record_media 行数 */
        long mediaCount,

        /** 对比印记数 */
        long milestoneCount,

        /** 陪伴天数：DATEDIFF(CURDATE(), DATE(created_at))，最小 1 */
        long daysWithUs
) {
}
