package com.chiji.module.stage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 标签表 Mapper。
 * <p>
 * 查询用户可用标签（含 user_id=0 的系统预设 + 用户自定义）。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 按标签名查找系统预设或用户自定义标签（原生 SQL，避免 MyBatis-Plus 条件构造器对 user_id=0 的兼容问题）。
     *
     * @param name   标签名
     * @param userId 用户 ID
     * @return 标签实体，未找到返回 null
     */
    @Select("SELECT * FROM tag WHERE name = #{name} AND (user_id = 0 OR user_id = #{userId}) AND deleted = 0 LIMIT 1")
    Tag findByName(@Param("name") String name, @Param("userId") Long userId);
}

