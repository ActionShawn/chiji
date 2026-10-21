package com.chiji.module.rubberband.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.module.rubberband.dto.RubberBandDayRow;
import com.chiji.module.rubberband.entity.RubberBandRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 橡皮筋每日打卡表 Mapper。
 * <p>
 * 由 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 * toggle 幂等走原生 {@code INSERT ... ON DUPLICATE KEY UPDATE}（评审 4.1 拍板形态）；
 * 近 7 天点阵走 uk_rb_user_date 前缀范围查询，一条 SQL 取齐（BE-FB-05 验收）。
 */
@Mapper
public interface RubberBandRecordMapper extends BaseMapper<RubberBandRecord> {

    /**
     * 读取某用户某日打卡状态。
     *
     * @param userId     用户 ID
     * @param recordDate 打卡日
     * @return done 值（1 已打卡 / 0 已取消）；无行返回 null
     */
    @Select("SELECT done FROM rubber_band_record WHERE user_id = #{userId} AND record_date = #{recordDate}")
    Integer selectDone(@Param("userId") Long userId, @Param("recordDate") LocalDate recordDate);

    /**
     * 幂等 upsert：同日无行则插入，有行则翻转 done 为目标值（唯一键 uk_rb_user_date 兜底并发双写）。
     * <p>
     * 注意：本表禁逻辑删除行（deleted 恒 0），翻转更新不删除行，否则唯一键槽位被占。
     *
     * @param id         雪花 ID（仅首插分支生效；冲突更新分支保留原 id）
     * @param userId     用户 ID
     * @param recordDate 打卡日（服务端当日，不接受前端传值）
     * @param done       目标状态（1 打卡 / 0 取消）
     * @return 影响行数（1 插入 / 2 更新，MySQL upsert 语义）
     */
    @Insert("INSERT INTO rubber_band_record (id, user_id, record_date, done, created_at, updated_at, deleted) "
            + "VALUES (#{id}, #{userId}, #{recordDate}, #{done}, NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE done = VALUES(done), updated_at = NOW()")
    int upsertDone(@Param("id") Long id,
                   @Param("userId") Long userId,
                   @Param("recordDate") LocalDate recordDate,
                   @Param("done") Integer done);

    /**
     * 查询某用户日期区间内的打卡行（近 7 天点阵数据源，一条 SQL 走 uk_rb_user_date 前缀）。
     *
     * @param userId    用户 ID
     * @param startDate 起始日（含，today-6）
     * @param endDate   结束日（含，today）
     * @return 日期状态行（date=打卡日，done=状态值），按日期升序
     */
    @Select("SELECT record_date AS date, done AS done FROM rubber_band_record "
            + "WHERE user_id = #{userId} AND record_date >= #{startDate} AND record_date <= #{endDate} "
            + "AND deleted = 0 ORDER BY record_date ASC")
    List<RubberBandDayRow> selectRange(@Param("userId") Long userId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);
}
