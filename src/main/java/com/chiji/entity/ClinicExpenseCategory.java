package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chiji.common.core.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 矫正花费分类实体。
 * <p>
 * {@code userId=0} 为系统内置五类（牙套费/复诊费/保持器/洁牙/其他，migration 预置），
 * 不可改删；用户自定义分类同用户下名称唯一（uk_clinic_cat_user_name）。
 */
@Getter
@Setter
@ToString(callSuper = true)
@TableName("clinic_expense_category")
public class ClinicExpenseCategory extends BaseEntity {

    /** 所属用户 ID；0 = 系统内置预设 */
    private Long userId;

    /** 分类名（同一用户下唯一） */
    private String name;

    /** 排序权重，越小越靠前 */
    private Integer sortOrder;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
