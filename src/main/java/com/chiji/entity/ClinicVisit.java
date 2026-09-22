package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chiji.common.core.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 复诊档案实体（已确认日程 + 就诊记录同表，status 区分）。
 * <p>
 * PLANNED = 待复诊日程（日历实心标记、到期就诊提醒）；DONE = 已完成就诊记录
 * （补录内容/照片，同步投影一条时光轴 FOLLOW_UP 记录）。
 * {@code stageId}/{@code alignerId} 为创建时快照（可空，仅展示用，不随阶段变化回写）；
 * {@code nextVisitDate} 非空时完成动作自动生成下一条 PLANNED 日程。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("clinic_visit")
public class ClinicVisit extends BaseEntity {

    /** 所属用户 ID */
    private Long userId;

    /** 复诊日期 */
    private LocalDate visitDate;

    /** 状态：ClinicVisitStatusEnum.name()，PLANNED(待复诊)/DONE(已完成) */
    private String status;

    /** 诊所/医院名称（可空，预填上次值） */
    private String clinicName;

    /** 医生姓名（可空） */
    private String doctorName;

    /** 就诊内容摘要（完成后补录） */
    private String content;

    /** 医生约定的下次复诊日期（填写即自动生成下一条 PLANNED） */
    private LocalDate nextVisitDate;

    /** 创建时关联阶段 ID（可空，仅展示用） */
    private Long stageId;

    /** 创建时关联牙套副 ID（可空，仅隐形有值，仅展示用） */
    private Long alignerId;

    /** 完成时同步生成的时光轴记录 ID（可空） */
    private Long timelineRecordId;

    /** 就诊提醒提前天数（0~3，null=用用户默认配置） */
    private Integer remindOffsetDays;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
