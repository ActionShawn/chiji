package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.Stage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 阶段表 Mapper。
 * <p>
 * 由 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 */
@Mapper
public interface StageMapper extends BaseMapper<Stage> {

    /**
     * 查询指定用户的最大排序权重（用于新建阶段时追加到末尾）。
     *
     * @param userId 用户 ID
     * @return 最大 sort_order，无数据时返回 null
     */
    @Select("SELECT MAX(sort_order) FROM stage WHERE user_id = #{userId} AND deleted = 0")
    Integer selectMaxSortOrder(@Param("userId") Long userId);
}
