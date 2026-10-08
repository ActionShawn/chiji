package com.chiji.module.delay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chiji.entity.DelayTask;
import com.chiji.module.delay.config.DelayQueueProperties;
import com.chiji.module.delay.handler.DelayTaskHandler;
import com.chiji.module.delay.handler.DelayTaskSkipException;
import com.chiji.module.delay.handler.HandlerRegistry;
import com.chiji.module.delay.mapper.DelayTaskMapper;
import com.chiji.module.delay.service.DelayTaskService;
import com.chiji.module.delay.support.DelayTaskStatus;
import com.chiji.module.delay.vo.DelayTaskAdminVO;
import com.chiji.module.delay.vo.DelayTaskPageVO;
import com.chiji.module.delay.vo.DelayTaskStatsVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一延迟任务队列实现。
 * <p>
 * 核心约定（详见 docs/待开发/延迟队列组件设计方案.md）：
 * <ul>
 *   <li>时间基准：execute_at 由业务传入（应用时钟），泵与状态流转比较一律 DB NOW(3)；</li>
 *   <li>幂等：biz_key 唯一约束 + PENDING/RUNNING 幂等保留 + 终态覆盖式重投；
 *       完成写回带 lease_until 乐观围栏（租约被回收重投后旧执行结果作废）；</li>
 *   <li>业务性跳过（DelayTaskSkipException）→ DONE 记原因不耗重试；
 *       执行失败 → 退避重试（base × 2^(k-1)），retry_count 超过 max-retry 置 FAILED
 *       （单任务最多执行 max-retry + 1 次）；</li>
 *   <li>enabled=false：submit 静默跳过（组件回滚语义，业务动作不得因队列停用而失败）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelayTaskServiceImpl implements DelayTaskService {

    /** 触发偏差黄色告警阈值（秒），见设计方案 9.3 */
    private static final long DEVIATION_WARN_SECONDS = 30;

    /** 过期积压黄色告警阈值（条），见设计方案 9.3 */
    private static final long BACKLOG_WARN_THRESHOLD = 100;

    /** last_error 列上限 */
    private static final int LAST_ERROR_MAX_LENGTH = 512;

    private final DelayTaskMapper delayTaskMapper;
    private final HandlerRegistry handlerRegistry;
    private final DelayQueueProperties properties;
    private final ObjectMapper objectMapper;

    // ─────────────── 业务 API ───────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String taskType, String bizKey, LocalDateTime executeAt, String summary, Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            log.warn("延迟队列已停用（delay.queue.enabled=false），跳过投递: type={}, bizKey={}", taskType, bizKey);
            return;
        }
        if (taskType == null || taskType.isBlank() || bizKey == null || bizKey.isBlank() || executeAt == null) {
            throw new IllegalArgumentException("taskType/bizKey/executeAt 不能为空");
        }
        if (!handlerRegistry.hasHandler(taskType)) {
            throw new IllegalArgumentException("task_type 未注册 handler，拒绝投递: " + taskType);
        }
        String payloadJson = toJson(payload);
        String ownerModule = handlerRegistry.get(taskType).owner();

        DelayTask row = new DelayTask();
        row.setTaskType(taskType);
        row.setOwnerModule(ownerModule);
        row.setBizKey(bizKey);
        row.setSummary(summary);
        row.setPayload(payloadJson);
        row.setExecuteAt(executeAt);
        row.setStatus(DelayTaskStatus.PENDING.name());
        try {
            delayTaskMapper.insert(row);
            return;
        } catch (DuplicateKeyException e) {
            // 同 biz_key 已存在：走下方幂等/覆盖逻辑
        }

        DelayTask existing = delayTaskMapper.selectOne(new LambdaQueryWrapper<DelayTask>()
                .eq(DelayTask::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (existing == null) {
            // 并发极小概率场景（他方事务刚回滚等）：本次投递放弃，重试由调用方业务语义决定
            log.warn("延迟任务投递撞唯一键后查不到行，放弃本次投递: bizKey={}", bizKey);
            return;
        }
        DelayTaskStatus existingStatus = DelayTaskStatus.valueOf(existing.getStatus());
        if (!existingStatus.isTerminal()) {
            log.info("延迟任务重复投递，保留原计划: bizKey={}, status={}, executeAt={}",
                    bizKey, existingStatus, existing.getExecuteAt());
            return;
        }
        // 终态行覆盖式重投：以本次参数重置回 PENDING
        delayTaskMapper.overwriteResubmit(taskType, ownerModule, bizKey, summary, payloadJson, executeAt);
        log.info("延迟任务终态行覆盖式重投: bizKey={}, 原状态={}, 新 executeAt={}", bizKey, existingStatus, executeAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            return;
        }
        if (delayTaskMapper.cancelByBizKey(bizKey) > 0) {
            log.info("延迟任务已取消: bizKey={}", bizKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reschedule(String bizKey, LocalDateTime newExecuteAt) {
        if (bizKey == null || bizKey.isBlank() || newExecuteAt == null) {
            return false;
        }
        return delayTaskMapper.rescheduleByBizKey(bizKey, newExecuteAt) > 0;
    }

    // ─────────────── 调度泵支撑 ───────────────

    @Override
    public int reclaimExpiredLeases() {
        return delayTaskMapper.reclaimExpiredLeases();
    }

    @Override
    public int failExhaustedPending() {
        return delayTaskMapper.failExhaustedPending(properties.getMaxRetry());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DelayTask> claimDueTasks(int batchSize) {
        List<DelayTask> due = delayTaskMapper.selectDueForUpdate(batchSize);
        if (due.isEmpty()) {
            return List.of();
        }
        List<Long> ids = due.stream().map(DelayTask::getId).toList();
        delayTaskMapper.markRunning(ids, properties.getLeaseSeconds());
        // 重新水合：取回置 RUNNING 后的行（DB 生成的 lease_until/executed_at 是完成写回的乐观围栏）
        return delayTaskMapper.selectBatchIds(ids);
    }

    @Override
    public void executeClaimed(DelayTask task) {
        DelayTaskHandler handler = handlerRegistry.get(task.getTaskType());
        if (handler == null) {
            // submit 已 fail-fast 校验，理论不可达；handler 被下线时按业务跳过处理避免重试空转
            log.warn("延迟任务 handler 未注册（可能已下线），按跳过处理: bizKey={}, type={}",
                    task.getBizKey(), task.getTaskType());
            completeSuccess(task, "handler 未注册，跳过执行");
            return;
        }
        try {
            handler.execute(task);
            completeSuccess(task, null);
        } catch (DelayTaskSkipException skip) {
            completeSuccess(task, skip.getMessage());
        } catch (Exception e) {
            retryOrFail(task, e);
        }
    }

    // ─────────────── 运维 API ───────────────

    @Override
    public DelayTaskPageVO page(int page, int size, String status, String taskType, String owner,
                                String keyword, LocalDateTime beginTime, LocalDateTime endTime) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<DelayTask> qw = new LambdaQueryWrapper<DelayTask>()
                .eq(hasText(status), DelayTask::getStatus, status)
                .eq(hasText(taskType), DelayTask::getTaskType, taskType)
                .eq(hasText(owner), DelayTask::getOwnerModule, owner)
                .and(hasText(keyword), w -> w.like(DelayTask::getBizKey, keyword)
                        .or().like(DelayTask::getSummary, keyword))
                .ge(beginTime != null, DelayTask::getExecuteAt, beginTime)
                .le(endTime != null, DelayTask::getExecuteAt, endTime)
                .orderByDesc(DelayTask::getCreatedAt)
                .orderByDesc(DelayTask::getId);
        Page<DelayTask> result = delayTaskMapper.selectPage(new Page<>(safePage, safeSize), qw);

        DelayTaskPageVO vo = new DelayTaskPageVO();
        vo.setTotal(result.getTotal());
        vo.setPage(safePage);
        vo.setSize(safeSize);
        vo.setItems(result.getRecords().stream().map(this::toAdminVo).toList());
        return vo;
    }

    @Override
    public DelayTaskAdminVO detail(Long id) {
        if (id == null) {
            return null;
        }
        DelayTask task = delayTaskMapper.selectById(id);
        return task == null ? null : toAdminVo(task);
    }

    @Override
    public DelayTaskStatsVO stats() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (DelayTaskStatus s : DelayTaskStatus.values()) {
            statusCounts.put(s.name(), 0L);
        }
        for (Map<String, Object> row : delayTaskMapper.countByStatus()) {
            Object status = row.get("status");
            Object cnt = row.get("cnt");
            if (status != null && cnt != null) {
                statusCounts.put(status.toString(), ((Number) cnt).longValue());
            }
        }

        DelayTaskStatsVO vo = new DelayTaskStatsVO();
        vo.setStatusCounts(statusCounts);
        vo.setTodaySubmitted(delayTaskMapper.countTodaySubmitted());
        vo.setTodayFinished(delayTaskMapper.countTodayFinished());
        vo.setOverdueBacklog(delayTaskMapper.countOverdueBacklog());

        Map<String, Object> deviation = delayTaskMapper.selectDeviationSeconds();
        vo.setAvgDeviationSeconds(((Number) deviation.getOrDefault("avgSeconds", 0)).doubleValue());
        vo.setMaxDeviationSeconds(((Number) deviation.getOrDefault("maxSeconds", 0)).doubleValue());

        long failed = statusCounts.getOrDefault(DelayTaskStatus.FAILED.name(), 0L);
        if (failed > 0) {
            vo.setHealthLevel("RED");
            log.warn("延迟队列健康度 RED: FAILED 行数={}", failed);
        } else if (vo.getAvgDeviationSeconds() > DEVIATION_WARN_SECONDS
                || vo.getOverdueBacklog() > BACKLOG_WARN_THRESHOLD) {
            vo.setHealthLevel("YELLOW");
            log.warn("延迟队列健康度 YELLOW: 平均偏差={}s, 过期积压={}条",
                    vo.getAvgDeviationSeconds(), vo.getOverdueBacklog());
        } else {
            vo.setHealthLevel("GREEN");
        }
        return vo;
    }

    @Override
    public boolean retryFailed(Long id) {
        return id != null && delayTaskMapper.adminRetry(id) > 0;
    }

    @Override
    public boolean cancelById(Long id) {
        return id != null && delayTaskMapper.adminCancel(id) > 0;
    }

    @Override
    public int cleanExpiredRows() {
        return delayTaskMapper.cleanExpiredRows(properties.getRetentionDays());
    }

    // ─────────────── 私有：终态置位与重试 ───────────────

    private void completeSuccess(DelayTask task, String skipReason) {
        int updated = delayTaskMapper.completeSuccess(task.getId(), task.getLeaseUntil(), truncate(skipReason));
        if (updated == 0) {
            log.info("延迟任务终态写回未命中（租约已被回收/重投），本次结果作废: bizKey={}", task.getBizKey());
        }
    }

    private void retryOrFail(DelayTask task, Exception e) {
        String lastError = truncate(e.getClass().getSimpleName() + ": " + e.getMessage());
        int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        if (nextRetry > properties.getMaxRetry()) {
            delayTaskMapper.completeFailure(task.getId(), task.getLeaseUntil(), nextRetry, lastError);
            log.warn("延迟任务重试耗尽置 FAILED: bizKey={}, retryCount={}", task.getBizKey(), nextRetry, e);
            return;
        }
        long backoffSeconds = nextBackoffSeconds(properties.getRetryBackoffBaseSeconds(), nextRetry);
        int updated = delayTaskMapper.retryLater(task.getId(), task.getLeaseUntil(), nextRetry, backoffSeconds, lastError);
        if (updated == 0) {
            log.info("延迟任务重试排期未命中（租约已被回收/重投），本次结果作废: bizKey={}", task.getBizKey());
        } else {
            log.warn("延迟任务执行失败，{}s 后第 {} 次重试: bizKey={}", backoffSeconds, nextRetry, task.getBizKey(), e);
        }
    }

    /**
     * 第 k 次重试的退避秒数：base × 2^(k-1)，即 base / 2base / 4base...
     * 包级可见供单元测试。
     */
    static long nextBackoffSeconds(int baseSeconds, int retryNo) {
        return (long) baseSeconds << (Math.max(retryNo, 1) - 1);
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("延迟任务 payload 序列化失败", e);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= LAST_ERROR_MAX_LENGTH ? text : text.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    private DelayTaskAdminVO toAdminVo(DelayTask task) {
        DelayTaskAdminVO vo = new DelayTaskAdminVO();
        vo.setId(task.getId());
        vo.setTaskType(task.getTaskType());
        vo.setOwnerModule(task.getOwnerModule());
        vo.setBizKey(task.getBizKey());
        vo.setSummary(task.getSummary());
        vo.setPayload(task.getPayload());
        vo.setStatus(task.getStatus());
        vo.setExecuteAt(task.getExecuteAt());
        vo.setLeaseUntil(task.getLeaseUntil());
        vo.setRetryCount(task.getRetryCount());
        vo.setExecutedAt(task.getExecutedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setLastError(task.getLastError());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        // 触发偏差仅一次成功的 DONE 行有口径意义（重试行 execute_at 已被退避顺延）
        if (task.getFinishedAt() != null && task.getExecuteAt() != null
                && DelayTaskStatus.DONE.name().equals(task.getStatus())
                && task.getRetryCount() != null && task.getRetryCount() == 0) {
            vo.setDeviationSeconds(Duration.between(task.getExecuteAt(), task.getFinishedAt()).getSeconds());
        }
        return vo;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
