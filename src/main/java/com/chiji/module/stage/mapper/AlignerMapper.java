package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.Aligner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 牙套副表 Mapper。
 * <p>
 * 由 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 */
@Mapper
public interface AlignerMapper extends BaseMapper<Aligner> {

    /**
     * 逻辑删除指定阶段下的全部牙套副（deleted 置 1）。
     * <p>
     * 用于删除阶段时连带清理牙套副，数据保留可恢复，查询时自动过滤。
     *
     * @param stageId 阶段 ID
     * @return 影响行数
     */
    @Update("UPDATE aligner SET deleted = 1 WHERE stage_id = #{stageId} AND deleted = 0")
    int logicDeleteByStageId(@Param("stageId") Long stageId);
}
