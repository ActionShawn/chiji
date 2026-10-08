package com.chiji.module.delay.support;

/**
 * 延迟任务状态机（delay_task.status 列取值）。
 * <p>
 * 单向流转：PENDING → RUNNING → DONE / FAILED；PENDING/RUNNING → CANCELLED。
 * 终态行（DONE/CANCELLED/FAILED）由 submit 覆盖式重投重置回 PENDING。
 */
public enum DelayTaskStatus {

    /** 待触发 */
    PENDING,

    /** 执行中（持有租约） */
    RUNNING,

    /** 执行完成（含业务性跳过） */
    DONE,

    /** 已取消 */
    CANCELLED,

    /** 重试耗尽，终态失败 */
    FAILED;

    /** 是否为终态（不再被泵调度，仅可覆盖式重投） */
    public boolean isTerminal() {
        return this == DONE || this == CANCELLED || this == FAILED;
    }
}
