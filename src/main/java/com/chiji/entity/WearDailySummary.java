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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日佩戴结算实体（物化）。
 * <p>
 * 某用户某自然日（Asia/Shanghai）的佩戴结算结果，由 00:30 定时 job 与客户端懒结算（00:05 后
 * 打开补昨）写入，用户补录历史日期后对该日重算覆盖。{@code wearSec} = {@code manualSec} +
 * {@code makeupSec}（达标基数，含补录）；{@code goalSec} 为结算当日目标快照，保证历史达标口径
 * 稳定；{@code level} 为达标等级（FULL/PARTIAL/NONE）。连续达标天数不落库，由本表逐日派生。
 */
@Getter
@Setter
@ToString
@TableName("wear_daily_summary")
public class WearDailySummary {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（唯一索引 uk_wear_summary_user_date 前缀） */
    private Long userId;

    /** 结算自然日（Asia/Shanghai） */
    private LocalDate summaryDate;

    /** 当日实际佩戴总秒（MANUAL + MAKEUP，达标基数） */
    private Integer wearSec;

    /** 其中手工打卡/晨起补记（MANUAL）秒数 */
    private Integer manualSec;

    /** 其中校准补录（MAKEUP）秒数（展示与正常分开统计） */
    private Integer makeupSec;

    /** 结算时当日目标快照（秒），保证历史达标口径稳定 */
    private Integer goalSec;

    /** 达标等级：WearLevelEnum.name()，FULL(≥目标)/PARTIAL(≥目标-4h)/NONE(<目标-4h) */
    private String level;

    /** 结算/最近重算时间 */
    private LocalDateTime settledAt;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
