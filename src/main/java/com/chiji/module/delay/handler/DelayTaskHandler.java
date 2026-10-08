package com.chiji.module.delay.handler;

import com.chiji.entity.DelayTask;

/**
 * 延迟任务处理器 SPI。
 * <p>
 * 业务模块实现本接口并注册为 Spring Bean，delay 模块启动时由
 * {@link HandlerRegistry} 收集（type 重复即启动失败）。实现约定：
 * <ul>
 *   <li><b>业务性跳过</b>（如超时前已戴回、开关已关、无额度）：抛
 *       {@link DelayTaskSkipException}，任务置 DONE 并记录跳过原因，不消耗重试；</li>
 *   <li><b>执行失败</b>（网络/下游接口异常）：抛任意其他异常，按退避重试，
 *       超过 max-retry 置 FAILED；</li>
 *   <li><b>幂等</b>：租约回收、多实例并发等极小概率场景可能重复执行，
 *       handler 内必须对业务结果做二次校验兜底；</li>
 *   <li>handler 不得直接操作 delay_task 表（终态置位由组件统一维护），
 *       遵守 ArchUnit mapper 访问约束。</li>
 * </ul>
 */
public interface DelayTaskHandler {

    /** 任务类型（全局唯一），对应 delay_task.task_type */
    String type();

    /** 归属模块标识（wear/clinic/...），投递时自动填充 owner_module，运维页按模块筛选 */
    String owner();

    /**
     * 执行任务。
     *
     * @param task 任务行（含 payload JSON，handler 自行解析）
     * @throws DelayTaskSkipException 业务性跳过（正常完成，记录原因）
     * @throws Exception              执行失败（进入退避重试）
     */
    void execute(DelayTask task) throws Exception;
}
