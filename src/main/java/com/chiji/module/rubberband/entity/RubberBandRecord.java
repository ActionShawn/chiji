package com.chiji.module.rubberband.entity;

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
 * 橡皮筋每日打卡实体（固定托槽第 1 批 REQ-FB-006 → 第 2 批 OPT-FB-04 次数计数）。
 * <p>
 * 唯一键 {@code uk_rb_user_date(user_id, record_date)} 不含 deleted：业务层
 * <b>禁逻辑删除行</b>（评审红线 R7——若对行做逻辑删除，唯一键槽位仍被占用，
 * 同日再计数 INSERT 会撞唯一键）。第 2 批 done 语义升级为 count：
 * increment 走 {@code INSERT ... ON DUPLICATE KEY UPDATE count = count + 1} 原子累加；
 * done 列冻结废弃（迁移回填 count=done，仅回滚兜底，业务读写全部走 count）。
 */
@Getter
@Setter
@ToString
@TableName("rubber_band_record")
public class RubberBandRecord {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户 ID（唯一键 uk_rb_user_date 前缀） */
    private Long userId;

    /** 打卡日（设备本地时区口径；仅当日，服务端取 now，不接受前端传日期） */
    private LocalDate recordDate;

    /** 当日累计更换次数（OPT-FB-04：done 语义升级，≥1 即当日打卡；写入路径唯一事实） */
    private Integer count;

    /** 第 1 批勾选语义（冻结废弃：仅迁移回填与回滚兜底，业务不再读写） */
    private Integer done;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记仅规范兼容，业务恒 0（禁止 deleteById，会占唯一键槽位） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
