package com.chiji.module.delay.handler;

import java.time.LocalDateTime;

/**
 * 业务性改期信号：handler 判定本次不应执行，但任务须按新时刻重排（非终态、非重试）。
 * <p>
 * 与 {@link DelayTaskSkipException}（终态 DONE + last_error）不同，本异常由调度泵捕获后将
 * RUNNING 行原子置回 PENDING 并改写 {@code execute_at}（带租约乐观围栏）——任务行不复制、
 * biz_key 不变，既有「按业务键取消」与执行围栏对新时刻依然有效。
 * <p>
 * 典型场景：摘下超时提醒智能模式跨零点重算延期（PRD 2026-10-08 修订：可摘下时间仅针对当日，
 * 跨日到点时按新一天账本重排，而非下发失真的昨日快照文案）。
 */
public class DelayTaskRescheduleException extends Exception {

    /** 重排后的期望触发时刻 */
    private final LocalDateTime newExecuteAt;

    /** 改期原因（记入任务 last_error，运维可读） */
    private final String reason;

    /** 改期时随行更新的任务 payload（可空：null 表示保留原 payload 不改写）。
     * 典型用途：跨零点重排把 payload 内的下发文案换为跨零点可见性版并打重排标记 */
    private final String newPayload;

    public DelayTaskRescheduleException(LocalDateTime newExecuteAt, String reason) {
        this(newExecuteAt, reason, null);
    }

    public DelayTaskRescheduleException(LocalDateTime newExecuteAt, String reason, String newPayload) {
        super(reason);
        this.newExecuteAt = newExecuteAt;
        this.reason = reason;
        this.newPayload = newPayload;
    }

    public LocalDateTime getNewExecuteAt() {
        return newExecuteAt;
    }

    public String getReason() {
        return reason;
    }

    public String getNewPayload() {
        return newPayload;
    }
}
