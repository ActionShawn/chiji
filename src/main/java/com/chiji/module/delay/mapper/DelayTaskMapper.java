package com.chiji.module.delay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.DelayTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 延迟任务表 Mapper。
 * <p>
 * 除 BaseMapper 通用方法外，提供调度泵的原子取任务 / 租约回收、状态机流转与运维统计 SQL。
 * 全部时间比较以 DB {@code NOW(3)} 为基准；执行完成类写回带
 * {@code lease_until} 乐观围栏——租约被回收重投后，旧执行的迟到结果自动作废。
 * 仅允许 delay 模块 service 层访问（ArchUnit 约束）。
 */
@Mapper
public interface DelayTaskMapper extends BaseMapper<DelayTask> {

    /**
     * 取出到期任务并加行锁（SKIP LOCKED：多实例并发取任务互不阻塞、不重复抢占）。
     * <p>
     * 须在 service 层事务内调用，配合 {@link #markRunning(List, int)} 同事务内置 RUNNING。
     *
     * @param limit 单轮最大取出数
     * @return 到期任务行（持锁状态，含完整字段）
     */
    @Select("SELECT * FROM delay_task "
            + "WHERE status = 'PENDING' AND execute_at <= NOW(3) "
            + "ORDER BY execute_at LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<DelayTask> selectDueForUpdate(@Param("limit") int limit);

    /**
     * 取出的任务置 RUNNING 并发放租约（同一事务内，记 executed_at）。
     *
     * @param ids          本轮取出的任务 id
     * @param leaseSeconds 租约时长（秒），须大于 handler 最大执行时长
     * @return 影响行数
     */
    @Update("<script>UPDATE delay_task "
            + "SET status = 'RUNNING', lease_until = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND), executed_at = NOW(3) "
            + "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int markRunning(@Param("ids") List<Long> ids, @Param("leaseSeconds") int leaseSeconds);

    /**
     * 回收超时租约：执行中实例崩溃/卡死超过 lease-seconds 的任务重新置 PENDING 并计一次重试。
     *
     * @return 回收行数
     */
    @Update("UPDATE delay_task "
            + "SET status = 'PENDING', retry_count = retry_count + 1, lease_until = NULL "
            + "WHERE status = 'RUNNING' AND lease_until < NOW(3)")
    int reclaimExpiredLeases();

    /**
     * 兜底：租约回收导致 retry_count 超过上限的 PENDING 行直接置 FAILED
     * （防「handler 挂起 → 回收 → 重投 → 再挂起」无限循环；正常失败重试流程不会产生此类行）。
     *
     * @param maxRetry 最大重试次数
     * @return 置 FAILED 行数
     */
    @Update("UPDATE delay_task SET status = 'FAILED', finished_at = NOW(3), "
            + "last_error = '租约到期回收后重试次数达上限' "
            + "WHERE status = 'PENDING' AND retry_count > #{maxRetry}")
    int failExhaustedPending(@Param("maxRetry") int maxRetry);

    /**
     * 业务取消（按 biz_key）：PENDING/RUNNING → CANCELLED，天然幂等。
     *
     * @return 影响行数：1 已取消；0 行不存在或已是终态
     */
    @Update("UPDATE delay_task SET status = 'CANCELLED', finished_at = NOW(3) "
            + "WHERE biz_key = #{bizKey} AND status IN ('PENDING', 'RUNNING')")
    int cancelByBizKey(@Param("bizKey") String bizKey);

    /**
     * 业务改期（按 biz_key）：仅 PENDING 允许。
     *
     * @return 影响行数：1 已改期；0 行不存在或非 PENDING
     */
    @Update("UPDATE delay_task SET execute_at = #{newExecuteAt} "
            + "WHERE biz_key = #{bizKey} AND status = 'PENDING'")
    int rescheduleByBizKey(@Param("bizKey") String bizKey, @Param("newExecuteAt") LocalDateTime newExecuteAt);

    /**
     * 覆盖式重投：终态行（DONE/CANCELLED/FAILED）重置回 PENDING 并以本次投递参数全量覆盖。
     * <p>
     * submit 撞 biz_key 唯一约束且既有行为终态时调用；仅终态行可覆盖（WHERE 兜底），
     * 与并发场景下已被他方重新投递的行互不干扰（影响 0 行即放弃）。
     *
     * @return 影响行数：1 重投成功；0 状态已变化（幂等放弃）
     */
    @Update("UPDATE delay_task SET task_type = #{taskType}, owner_module = #{ownerModule}, "
            + "summary = #{summary}, payload = #{payload}, execute_at = #{executeAt}, status = 'PENDING', "
            + "retry_count = 0, lease_until = NULL, executed_at = NULL, finished_at = NULL, last_error = NULL "
            + "WHERE biz_key = #{bizKey} AND status IN ('DONE', 'CANCELLED', 'FAILED')")
    int overwriteResubmit(@Param("taskType") String taskType, @Param("ownerModule") String ownerModule,
                          @Param("bizKey") String bizKey, @Param("summary") String summary,
                          @Param("payload") String payload, @Param("executeAt") LocalDateTime executeAt);

    /**
     * 执行完成置 DONE（含业务性跳过，原因记 last_error）。
     * 带 lease_until 乐观围栏：租约已被回收/重投时影响 0 行，旧执行结果作废。
     *
     * @return 影响行数：1 完成；0 本次执行已过期
     */
    @Update("UPDATE delay_task SET status = 'DONE', finished_at = NOW(3), last_error = #{lastError} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_until = #{leaseUntil}")
    int completeSuccess(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil,
                        @Param("lastError") String lastError);

    /**
     * 执行失败进入重试：置回 PENDING、retry_count+1、execute_at 顺延退避间隔。
     *
     * @return 影响行数：1 重试排期；0 本次执行已过期
     */
    @Update("UPDATE delay_task SET status = 'PENDING', retry_count = #{nextRetry}, "
            + "execute_at = DATE_ADD(NOW(3), INTERVAL #{backoffSeconds} SECOND), "
            + "lease_until = NULL, last_error = #{lastError} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_until = #{leaseUntil}")
    int retryLater(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil,
                   @Param("nextRetry") int nextRetry, @Param("backoffSeconds") long backoffSeconds,
                   @Param("lastError") String lastError);

    /**
     * 重试耗尽置 FAILED（终态）。
     *
     * @return 影响行数：1 置 FAILED；0 本次执行已过期
     */
    @Update("UPDATE delay_task SET status = 'FAILED', retry_count = #{nextRetry}, finished_at = NOW(3), "
            + "last_error = #{lastError} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_until = #{leaseUntil}")
    int completeFailure(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil,
                        @Param("nextRetry") int nextRetry, @Param("lastError") String lastError);

    /**
     * 执行中业务性改期（handler 抛 {@link com.chiji.module.delay.handler.DelayTaskRescheduleException}）：
     * RUNNING 行原子置回 PENDING 并改写 execute_at（非终态、不消耗重试）；
     * {@code payload} 非空时一并改写任务 payload（如跨零点重排把到点下发文案换为跨零点可见性版）。
     * 带 lease_until 乐观围栏：租约已被回收/重投时影响 0 行，本次改期作废（防与重执行双写）。
     *
     * @return 影响行数：1 改期成功；0 本次执行已过期
     */
    @Update("<script>UPDATE delay_task SET status = 'PENDING', execute_at = #{newExecuteAt}, "
            + "lease_until = NULL, last_error = #{reason} "
            + "<if test='payload != null'>, payload = #{payload}</if> "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_until = #{leaseUntil}</script>")
    int rescheduleRunning(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil,
                          @Param("newExecuteAt") LocalDateTime newExecuteAt,
                          @Param("reason") String reason, @Param("payload") String payload);

    // ─────────────── 运维动作（管理端） ───────────────

    /**
     * 手动重试：FAILED → PENDING，retry_count 清零、execute_at 立即到期、终态字段复位。
     *
     * @return 影响行数：1 已重投；0 非 FAILED 行
     */
    @Update("UPDATE delay_task SET status = 'PENDING', retry_count = 0, execute_at = NOW(3), "
            + "lease_until = NULL, executed_at = NULL, finished_at = NULL, last_error = NULL "
            + "WHERE id = #{id} AND status = 'FAILED'")
    int adminRetry(@Param("id") Long id);

    /**
     * 手动取消：PENDING/RUNNING → CANCELLED。
     *
     * @return 影响行数：1 已取消；0 非 PENDING/RUNNING 行
     */
    @Update("UPDATE delay_task SET status = 'CANCELLED', finished_at = NOW(3) "
            + "WHERE id = #{id} AND status IN ('PENDING', 'RUNNING')")
    int adminCancel(@Param("id") Long id);

    // ─────────────── 清理与健康统计 ───────────────

    /**
     * 清理超过保留期的终态行（CleanJob 每日执行，物理删除）。
     *
     * @return 删除行数
     */
    @Delete("DELETE FROM delay_task WHERE finished_at < DATE_SUB(NOW(3), INTERVAL #{retentionDays} DAY) "
            + "AND status IN ('DONE', 'CANCELLED', 'FAILED')")
    int cleanExpiredRows(@Param("retentionDays") int retentionDays);

    /** 五状态计数（GROUP BY status，无行的状态由调用方补 0） */
    @Select("SELECT status, COUNT(*) AS cnt FROM delay_task GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /** 今日投递数（Asia/Shanghai 自然日，session 时区） */
    @Select("SELECT COUNT(*) FROM delay_task WHERE created_at >= CURDATE()")
    long countTodaySubmitted();

    /** 今日触发数（按终态时间口径） */
    @Select("SELECT COUNT(*) FROM delay_task WHERE finished_at >= CURDATE()")
    long countTodayFinished();

    /**
     * 积压告警数：PENDING 且 execute_at 已过期超过 1 分钟的行数（泵停摆/批量到期时升高）。
     */
    @Select("SELECT COUNT(*) FROM delay_task WHERE status = 'PENDING' "
            + "AND execute_at < DATE_SUB(NOW(3), INTERVAL 60 SECOND)")
    long countOverdueBacklog();

    /**
     * 触发偏差（秒）：finished_at − execute_at，仅统计最近 24h 完成且 retry_count = 0 的 DONE 行
     * （重试行因退避顺延 execute_at 口径失真，排除；CANCELLED/未完成行不参与）。
     *
     * @return avgSeconds / maxSeconds 两键（无行时为 0）
     */
    @Select("SELECT COALESCE(AVG(TIMESTAMPDIFF(MICROSECOND, execute_at, finished_at)) / 1000000, 0) AS avgSeconds, "
            + "COALESCE(MAX(TIMESTAMPDIFF(MICROSECOND, execute_at, finished_at)) / 1000000, 0) AS maxSeconds "
            + "FROM delay_task WHERE status = 'DONE' AND retry_count = 0 "
            + "AND finished_at >= DATE_SUB(NOW(3), INTERVAL 24 HOUR)")
    Map<String, Object> selectDeviationSeconds();
}
