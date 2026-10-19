package com.chiji.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.common.core.dto.LabelValueRow;
import com.chiji.entity.User;
import org.apache.ibatis.annotations.Delete;
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

    /**
     * 按小时新增用户数（管理端看板当日趋势，原生 SQL 需显式排除逻辑删除）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每小时计数行（label=两位小时「09」）
     */
    @Select("SELECT DATE_FORMAT(created_at, '%H') AS label, COUNT(*) AS value "
            + "FROM user WHERE created_at >= #{start} AND created_at < #{end} AND deleted = 0 "
            + "GROUP BY label")
    List<LabelValueRow> countNewUsersByHour(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按月新增用户数（管理端看板全部趋势，原生 SQL 需显式排除逻辑删除）。
     *
     * @param start 起始时刻（含）
     * @param end   结束时刻（不含）
     * @return 每月计数行（label=「2026-09」）
     */
    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m') AS label, COUNT(*) AS value "
            + "FROM user WHERE created_at >= #{start} AND created_at < #{end} AND deleted = 0 "
            + "GROUP BY label")
    List<LabelValueRow> countNewUsersByMonth(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 物理删除用户行（账号注销专用）。
     * <p>
     * 必须用原生 SQL 而非 {@code deleteById()}：{@link User} 带 {@code @TableLogic} 且全局配置
     * {@code logic-delete-field: deleted}，MyBatis-Plus 生成的删除会退化成
     * {@code UPDATE user SET deleted = 1}。逻辑删除会保留 {@code uk_user_openid} 唯一键槽位，
     * 导致同一微信再次登录时 {@code selectOne(openid)} 被 {@code deleted = 0} 过滤查不到 → 走 insert
     * → 撞唯一键 → {@link org.springframework.dao.DuplicateKeyException}。
     * 物理删除后 openid 槽位释放，同一微信重登得到全新空账号。
     *
     * @param id 用户 ID
     * @return 影响行数：1 删除成功；0 该行已不存在（幂等）
     */
    @Delete("DELETE FROM user WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
