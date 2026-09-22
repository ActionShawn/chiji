package com.chiji.module.clinic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.ClinicExpenseCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 矫正花费分类 Mapper。
 * <p>
 * 列表查询 = 系统内置（user_id=0）+ 当前用户自定义，Service 侧合并排序。
 * {@code deleted} 走 MyBatis-Plus 逻辑删除；唯一键 {@code uk(user_id, name)} 不含
 * deleted，逻辑删行仍占唯一键，故同名新建的查重与复活需绕过逻辑删（自定义 SQL）。
 */
@Mapper
public interface ClinicExpenseCategoryMapper extends BaseMapper<ClinicExpenseCategory> {

    /**
     * 按用户+名称查分类（含逻辑删行，供同名复活判定）。
     *
     * @param userId 用户 ID
     * @param name   分类名
     * @return 命中行（可能 deleted=1）；未命中返回 null
     */
    @Select("SELECT * FROM clinic_expense_category WHERE user_id = #{userId} AND name = #{name} LIMIT 1")
    ClinicExpenseCategory selectAnyByName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * 复活逻辑删行（置回正常并重排序；绕过 @TableLogic 的 update 自动条件）。
     *
     * @param id    分类 ID
     * @param order 新排序权重
     * @return 影响行数
     */
    @Update("UPDATE clinic_expense_category SET deleted = 0, sort_order = #{order}, updated_at = NOW() WHERE id = #{id}")
    int revive(@Param("id") Long id, @Param("order") int order);
}
