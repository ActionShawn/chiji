package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.Stage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 查询指定用户全部阶段的 {id → mode} 映射（含已逻辑删除的阶段，REQ-FB-008 时光轴模式过滤）。
     * <p>
     * 自定义 SQL 刻意绕过 {@code @TableLogic}：StageServiceImpl.deleteStage 只级联逻辑删除 aligner
     * 而保留 timeline_record，阶段删除后其记录仍需按阶段原 mode 归属过滤（评审 3.3 雷 1），
     * 因此过滤用的 mode 映射必须能取到已删除阶段的 mode。
     *
     * @param userId 用户 ID
     * @return 该用户全部阶段（仅填充 id/mode，其余字段为 null；含已删除阶段）
     */
    @Select("SELECT id, mode FROM stage WHERE user_id = #{userId}")
    List<Stage> selectAllModesByUserId(@Param("userId") Long userId);
}
