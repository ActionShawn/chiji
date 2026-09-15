package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 管理端接口小时视图 VO（单接口某日 24 小时分布，无数据小时补 0）。
 */
@Data
@Builder
public class ApiHourlyVO {

    /** 接口模板 */
    private String api;

    /** 统计日期（yyyy-MM-dd） */
    private String date;

    /** 24 个小时分布（0-23） */
    private List<Hour> hours;

    /**
     * 单小时数据。
     */
    @Data
    @Builder
    public static class Hour {

        /** 小时 0-23 */
        private int hour;

        /** 调用数 */
        private long cnt;

        /** 失败数 */
        private long errCnt;

        /** 慢请求数 */
        private long slowCnt;

        /** 最大耗时 ms */
        private int maxMs;
    }
}
