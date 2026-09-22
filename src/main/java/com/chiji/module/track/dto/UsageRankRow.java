package com.chiji.module.track.dto;

import lombok.Data;

/**
 * 使用时长排行投影行（管理端运营看板用）。
 * <p>
 * 列别名 userId / totalSec 对应驼峰字段；昵称由 service 二次查询后脱敏。
 */
@Data
public class UsageRankRow {

    /** 用户 ID */
    private Long userId;

    /** 区间内使用时长总和（秒） */
    private Long totalSec;
}
