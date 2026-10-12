package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 咬胶会话实体（一次咬胶行为，一行一段）。
 * <p>
 * 独立于佩戴会话 {@link WearSession}，语义 = 一次咬胶行为。归属三元组
 * {@code userId + stageId + alignerId}，其中 {@code stageId}/{@code alignerId} 为「开始时刻」
 * 的当前 ACTIVE 阶段/副快照（防换副归属漂移，允许 NULL = 未归属档），写死后续换副不回溯修改。
 * <p>
 * 净时长 = {@code (endedAt − startedAt) − pauseSec}，由服务端派生、不落库；跨日整段归
 * {@code startedAt} 所在自然日，不拆分。{@code pausedAt} 为当前暂停起点（null = 未暂停/运行中），
 * 恢复或结束时把 {@code now − pausedAt} 一次性结算进 {@code pauseSec}（自然幂等）。
 */
@Getter
@Setter
@ToString
@TableName("chew_session")
public class ChewSession {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（索引 idx_chew_user_time 前缀） */
    private Long userId;

    /** 开始时刻阶段快照（当前 ACTIVE 阶段；无则 NULL = 未归属档） */
    private Long stageId;

    /** 开始时刻牙套副快照（当前 ACTIVE 副；无则 NULL = 未归属档） */
    private Long alignerId;

    /** 开始时刻（服务端生成；「重来」重置为最新起点） */
    private LocalDateTime startedAt;

    /** 结束时刻（进行中/暂停中未结束为 null） */
    private LocalDateTime endedAt;

    /** 已结算累计暂停秒（不含 pausedAt 尚未结算的当前暂停段） */
    private Integer pauseSec;

    /** 当前暂停起点（null = 未暂停/运行中；恢复或结束时结算进 pauseSec） */
    private LocalDateTime pausedAt;

    /** 目标时长快照（秒）：咬胶默认 600；与每日建议 goal(1200) 为两个独立口径 */
    private Integer targetSec;

    /** 来源：ChewSourceEnum.name()，REALTIME(实时计时)/MANUAL(手动补录，预留) */
    private String source;

    /** 是否被手动修正过 */
    private Integer edited;

    /** 前端幂等键（唯一键 uk_chew_client_session；重复开始/补报防重） */
    private String clientSessionId;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 正常 / 1 已删除（含误触近零会话，默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
