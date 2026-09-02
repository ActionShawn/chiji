package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.RecordTagRelation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 记录-标签关联表 Mapper。
 * <p>
 * 联合主键 (record_id, tag_id) 无独立 id 列，不能使用 BaseMapper.insert
 * （MyBatis-Plus 会尝试为不存在的 id 列生成主键导致 SQL 错误），
 * 故所有写入走原生 SQL。查询仍可用 BaseMapper 的 selectList。
 */
@Mapper
public interface RecordTagRelationMapper extends BaseMapper<RecordTagRelation> {

    /**
     * 批量插入记录-标签关联（原生 SQL，直接用 long 值避免实体映射干扰）。
     *
     * @param relations 关联列表（recordId, tagId 对）
     * @return 插入行数
     */
    @Insert({
            "<script>",
            "INSERT INTO record_tag_relation (record_id, tag_id) VALUES ",
            "<foreach collection='list' item='item' separator=','>",
            "(#{item.recordId}, #{item.tagId})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("list") List<RecordTagRelation> relations);

    /**
     * 按记录 ID 查询关联的标签 ID 列表。
     *
     * @param recordId 记录 ID
     * @return 标签 ID 列表
     */
    @Select("SELECT tag_id FROM record_tag_relation WHERE record_id = #{recordId}")
    List<Long> selectTagIdsByRecordId(@Param("recordId") Long recordId);
}



