package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 慢请求/错误明细实体（阈值采集）。
 * <p>
 * 仅耗时超过阈值（默认 500ms）或失败的请求异步单条写入，正常请求零落库；
 * 供监控页慢接口 TopN 与明细下钻。保留 90 天定期物理清理。
 */
@Getter
@Setter
@ToString
@TableName("slow_api_log")
public class SlowApiLog {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 归一化接口模板 */
    private String api;

    /** 登录用户 ID（未登录请求为 NULL） */
    private Long userId;

    /** 耗时 ms */
    private Integer costMs;

    /** 1=成功 0=失败 */
    private Integer success;

    /** 异常摘要（截断 500，成功慢请求为 NULL） */
    private String errorMsg;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
