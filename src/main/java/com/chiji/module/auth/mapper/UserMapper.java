package com.chiji.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户表 Mapper。
 * <p>
 * 由 {@link org.springframework.context.annotation.Configuration @SpringBootApplication}
 * 上的 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按日新增用户数（管理端看板趋势，原生 SQL 需显式排除逻辑删除）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每日计数行
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS value "
            + "FROM user WHERE created_at >= #{start} AND created_at < #{end} AND deleted = 0 "
            + "GROUP BY DATE(created_at)")
    List<DayValueRow> countNewUsersByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
