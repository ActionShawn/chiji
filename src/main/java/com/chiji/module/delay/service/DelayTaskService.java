package com.chiji.module.delay.service;

import com.chiji.entity.DelayTask;
import com.chiji.module.delay.vo.DelayTaskAdminVO;
import com.chiji.module.delay.vo.DelayTaskPageVO;
import com.chiji.module.delay.vo.DelayTaskStatsVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统一延迟任务队列服务：业务投递/取消/改期 + 调度泵支撑 + 运维查询。
 * <p>
 * submit/cancel/reschedule 设计为在业务事务内调用（与业务动作同事务，业务回滚任务也回滚）。
 */
public interface DelayTaskService {

    // ─────────────── 业务 API（业务模块事务内调用） ───────────────

    /**
     * 投递延迟任务。同 biz_key 存在 PENDING/RUNNING 行时幂等保留原计划；
     * 终态行（DONE/CANCELLED/FAILED）覆盖式重投。summary 为运维页展示用人类可读摘要，可空；
     * owner 由 HandlerRegistry 按 taskType 自动填充，task_type 无注册 handler 时 fail-fast
     * （保证投递必有实现）。enabled=false 时静默跳过（warn 日志，不抛异常）。
     *
     * @param taskType  任务类型（须已注册 handler）
     * @param bizKey    业务幂等键（全局唯一，如 takeoff-timeout:{sessionId}）
     * @param executeAt 期望触发时刻
     * @param summary   运维摘要（可空）
     * @param payload   任务参数（可空，序列化为 JSON 存储）
     */
    void submit(String taskType, String bizKey, LocalDateTime executeAt, String summary, Map<String, Object> payload);

    /**
     * 取消：PENDING/RUNNING → CANCELLED（记录 finished_at）；DONE/FAILED/CANCELLED 幂等忽略。
     * 戴回、换副拆会话等多个入口可安全调用。
     *
     * @param bizKey 业务幂等键
     */
    void cancel(String bizKey);

    /**
     * 改期：仅 PENDING 允许。
     *
     * @return true 改期成功；false 行不存在或非 PENDING
     */
    boolean reschedule(String bizKey, LocalDateTime newExecuteAt);

    // ─────────────── 调度泵支撑（PollerJob 调用） ───────────────

    /** 回收超时租约：RUNNING 且 lease_until 过期 → PENDING（retry_count+1） */
    int reclaimExpiredLeases();

    /** 兜底：租约回收导致 retry_count 超上限的 PENDING 行置 FAILED，防无限回收循环 */
    int failExhaustedPending();

    /**
     * 原子取出到期任务并置 RUNNING（SKIP LOCKED 抢占 + 发放租约，单事务）。
     *
     * @param batchSize 单轮最大取出数
     * @return 已置 RUNNING 的任务行（含租约信息，供完成写回乐观围栏）
     */
    List<DelayTask> claimDueTasks(int batchSize);

    /**
     * 执行单个已领取任务（虚拟线程调用）：路由 handler，区分业务性跳过/执行失败，
     * 终态置位带租约围栏（执行期间租约被回收重投则本次结果作废）。
     */
    void executeClaimed(DelayTask task);

    // ─────────────── 运维 API（DelayTaskAdminController 调用） ───────────────

    /**
     * 分页筛选任务列表。
     *
     * @param page      页码（1 起）
     * @param size      每页条数（1-100）
     * @param status    状态筛选（可空）
     * @param taskType  任务类型筛选（可空）
     * @param owner     归属模块筛选（可空）
     * @param keyword   biz_key / summary 模糊匹配（可空）
     * @param beginTime 计划触发时间范围起（可空）
     * @param endTime   计划触发时间范围止（可空）
     */
    DelayTaskPageVO page(int page, int size, String status, String taskType, String owner,
                         String keyword, LocalDateTime beginTime, LocalDateTime endTime);

    /** 任务详情（含 payload 全文与完整时间线），不存在返回 null */
    DelayTaskAdminVO detail(Long id);

    /** 健康度统计（五状态计数/今日投递触发/积压/触发偏差/红黄绿） */
    DelayTaskStatsVO stats();

    /** 手动重试：FAILED → PENDING（retry_count 清零、立即到期） */
    boolean retryFailed(Long id);

    /** 手动取消：PENDING/RUNNING → CANCELLED */
    boolean cancelById(Long id);

    /** 清理超保留期终态行（CleanJob 每日调用，物理删除） */
    int cleanExpiredRows();
}
