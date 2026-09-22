package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chiji.common.core.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 矫正花费流水实体。
 * <p>
 * 独立费用流水（非就诊从表）：{@code visitId} 关联复诊记录（可空），
 * 「复诊费一键带出」时回填以便溯源；删除分类时流水保留、仅分类逻辑删。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("clinic_expense")
public class ClinicExpense extends BaseEntity {

    /** 所属用户 ID */
    private Long userId;

    /** 分类 ID（clinic_expense_category） */
    private Long categoryId;

    /** 关联复诊记录 ID（可空；复诊费一键生成时回填） */
    private Long visitId;

    /** 花费日期 */
    private LocalDate expenseDate;

    /** 金额（元，两位小数） */
    private BigDecimal amount;

    /** 备注（可空） */
    private String note;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
