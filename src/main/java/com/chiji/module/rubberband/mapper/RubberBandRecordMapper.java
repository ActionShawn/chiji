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
 * 第 2 批 OPT-FB-04 次数语义：increment 走原生
 * {@code INSERT ... ON DUPLICATE KEY UPDATE count = count + 1}（行锁保证原子累加，并发不丢更新）；
 * 近 7 天点阵走 uk_rb_user_date 前缀范围查询，一条 SQL 取齐。
 * done 列冻结废弃：仅 insert 分支兜底写 1（保持「count&gt;0 当日已打卡」的回滚一致性），业务读写全部走 count。
 */
@Mapper
public interface RubberBandRecordMapper extends BaseMapper<RubberBandRecord> {

    /**
     * 读取某用户某日累计次数。
     *
     * @param userId     用户 ID
     * @param recordDate 记录日
     * @return 当日累计次数（0 = 无记录或历史取消行）；无行返回 null
     */
    @Select("SELECT count FROM rubber_band_record WHERE user_id = #{userId} AND record_date = #{recordDate}")
    Integer selectCount(@Param("userId") Long userId, @Param("recordDate") LocalDate recordDate);

    /**
     * 计数 upsert：同日无行则插入 count=1，有行则原子累加 1（唯一键 uk_rb_user_date 兜底并发双写，
     * 行锁保证并发不丢更新）。
     * <p>
     * 注意：本表禁逻辑删除行（deleted 恒 0）；done 列冻结，仅首插分支写 1 兜底回滚语义。
     *
     * @param id         雪花 ID（仅首插分支生效；冲突更新分支保留原 id）
     * @param userId     用户 ID
     * @param recordDate 记录日（服务端当日，不接受前端传值）
     * @return 影响行数（1 插入 / 2 更新，MySQL upsert 语义）
     */
    @Insert("INSERT INTO rubber_band_record (id, user_id, record_date, count, done, created_at, updated_at, deleted) "
            + "VALUES (#{id}, #{userId}, #{recordDate}, 1, 1, NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE count = count + 1, updated_at = NOW()")
    int incrementOnce(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("recordDate") LocalDate recordDate);

    /**
     * 查询某用户日期区间内的计数行（近 7 天点阵数据源，一条 SQL 走 uk_rb_user_date 前缀）。
     *
     * @param userId    用户 ID
     * @param startDate 起始日（含，today-6）
     * @param endDate   结束日（含，today）
     * @return 日期计数行（date=记录日，count=当日次数），按日期升序
     */
    @Select("SELECT record_date AS date, count AS count FROM rubber_band_record "
            + "WHERE user_id = #{userId} AND record_date >= #{startDate} AND record_date <= #{endDate} "
            + "AND deleted = 0 ORDER BY record_date ASC")
    List<RubberBandDayRow> selectRange(@Param("userId") Long userId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    /**
     * 查询某用户全部 count&gt;0 的记录日（连续天数 streak 聚合数据源，日期降序）。
     * <p>
     * 单用户每日最多一行，数据量以使用天数计（个人应用量级小），一次取齐后内存算连续段。
     *
     * @param userId 用户 ID
     * @return count&gt;0 的记录日列表，按日期降序（最新在前）
     */
    @Select("SELECT record_date FROM rubber_band_record "
            + "WHERE user_id = #{userId} AND count > 0 AND deleted = 0 ORDER BY record_date DESC")
    List<LocalDate> selectPositiveDatesDesc(@Param("userId") Long userId);

    /**
     * 本周累计更换次数（SUM(count)，周一起点口径与前端 §4.4 一致，走 uk_rb_user_date 前缀）。
     *
     * @param userId    用户 ID
     * @param startDate 本周周一（含）
     * @param endDate   今天（含）
     * @return 本周 SUM(count)；无行返回 0
     */
    @Select("SELECT COALESCE(SUM(count), 0) FROM rubber_band_record "
            + "WHERE user_id = #{userId} AND record_date >= #{startDate} AND record_date <= #{endDate} "
            + "AND deleted = 0")
    Integer sumRange(@Param("userId") Long userId,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);
}
