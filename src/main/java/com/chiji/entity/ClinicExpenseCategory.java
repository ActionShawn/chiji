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
 * {@code userId=0} 为系统内置分类（隐形五类 牙套费/复诊费/保持器/洁牙/其他 + 固定五类
 * 托槽/弓丝更换/橡皮筋/复诊挂号/保持器，migration 预置），不可改删；用户自定义分类同用户下
 * 名称唯一（uk_clinic_cat_user_name）。
 * 第 2 批 OPT-FB-03：mode 列承载模式分档（NULL 通用 / FIXED_BRACKET 固定专属），
 * 服务层按用户 treatment_type 过滤——隐形用户分类列表与存量记账数据零变化。
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

    /** 分类适用模式：NULL 通用（隐形存量行）/ FIXED_BRACKET 固定托槽专属（OPT-FB-03） */
    private String mode;

    /** 排序权重，越小越靠前 */
    private Integer sortOrder;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
