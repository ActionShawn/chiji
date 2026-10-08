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
 * 统一延迟任务实体（chiji-delay 组件）。
 * <p>
 * 一行一个业务任务（biz_key 唯一约束），行状态机循环复用：
 * PENDING → RUNNING → DONE / FAILED，CANCELLED 可由 PENDING/RUNNING 单向进入；
 * 终态行可被覆盖式重投（重置回 PENDING）。表结构见 db/schema.sql delay_task。
 * <p>
 * 本实体无逻辑删除列（终态行由 CleanJob 物理清理），created_at/updatedAt 由
 * MyMetaObjectHandler 自动填充。delay 模块不解释 payload 业务字段，由各 handler 自行解析。
 */
@Getter
@Setter
@ToString
@TableName("delay_task")
public class DelayTask {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务类型，对应 {@code DelayTaskHandler#type()} */
    private String taskType;

    /** 归属模块（wear/clinic/...），取自 {@code DelayTaskHandler#owner()}，运维页按模块筛选 */
    private String ownerModule;

    /** 业务幂等键，如 takeoff-timeout:{sessionId}，同 biz_key 至多一行 */
    private String bizKey;

    /** 人类可读摘要，运维列表直读（投递方传入，可空） */
    private String summary;

    /** 任务参数 JSON 字符串，handler 自行解析（可空） */
    private String payload;

    /** 计划触发时间（应用时钟计算，与 DB NOW(3) 偏差同 region 秒级） */
    private LocalDateTime executeAt;

    /** 状态：PENDING/RUNNING/DONE/CANCELLED/FAILED（见 DelayTaskStatus） */
    private String status;

    /** RUNNING 租约到期时间，超时由泵回收重投 */
    private LocalDateTime leaseUntil;

    /** 已重试次数（租约回收与失败重试 +1，手动重试清零） */
    private Integer retryCount;

    /** 最近一次实际开始执行时间（取出置 RUNNING 时由泵写入） */
    private LocalDateTime executedAt;

    /** 终态时间（DONE/FAILED/CANCELLED 均记录） */
    private LocalDateTime finishedAt;

    /** 最近一次失败原因（含业务跳过原因备注，如「无额度」） */
    private String lastError;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
