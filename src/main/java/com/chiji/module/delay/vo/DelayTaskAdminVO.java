package com.chiji.module.delay.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 延迟任务运维列表/详情 VO。
 * <p>
 * 详情与列表共用（详情多 payload 全文与完整时间线字段，列表仅多返回无妨）。
 * id 为雪花 Long，经全局 JacksonConfig 序列化为字符串避免前端精度丢失。
 */
@Data
public class DelayTaskAdminVO {

    /** 任务 id */
    private Long id;

    /** 任务类型 */
    private String taskType;

    /** 归属模块 */
    private String ownerModule;

    /** 业务幂等键 */
    private String bizKey;

    /** 人类可读摘要 */
    private String summary;

    /** 任务参数 JSON 全文（详情页高亮展示） */
    private String payload;

    /** 状态：PENDING/RUNNING/DONE/CANCELLED/FAILED */
    private String status;

    /** 计划触发时间 */
    private LocalDateTime executeAt;

    /** RUNNING 租约到期时间 */
    private LocalDateTime leaseUntil;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最近一次实际开始执行时间 */
    private LocalDateTime executedAt;

    /** 终态时间 */
    private LocalDateTime finishedAt;

    /** 失败原因 / 业务跳过原因 */
    private String lastError;

    /** 投递时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 触发偏差（秒）= finished_at − execute_at，仅 DONE 且 retry_count=0 的行有值
     * （重试行因退避顺延 execute_at，口径失真不展示）。
     */
    private Long deviationSeconds;
}
