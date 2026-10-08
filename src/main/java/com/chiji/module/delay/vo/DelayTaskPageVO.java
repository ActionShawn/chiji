package com.chiji.module.delay.vo;

import lombok.Data;

import java.util.List;

/**
 * 延迟任务运维分页结果（offset 分页，便于运维页跳页）。
 */
@Data
public class DelayTaskPageVO {

    /** 命中总数 */
    private long total;

    /** 当前页码（1 起） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 当前页任务列表 */
    private List<DelayTaskAdminVO> items;
}
