package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端慢请求/错误明细 VO（slow_api_log，游标分页）。
 */
@Data
@Builder
public class SlowApiLogVO {

    /** 明细 ID */
    private Long id;

    /** 接口模板 */
    private String api;

    /** 登录用户 ID（可空） */
    private Long userId;

    /** 耗时 ms */
    private int costMs;

    /** 是否成功 */
    private boolean success;

    /** 异常摘要 */
    private String errorMsg;

    /** 发生时刻 */
    private LocalDateTime createdAt;
}
