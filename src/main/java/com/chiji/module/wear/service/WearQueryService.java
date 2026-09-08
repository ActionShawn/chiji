package com.chiji.module.wear.service;

import java.time.LocalDate;
import java.util.List;

/**
 * 佩戴批处理查询（供定时任务等非请求线程使用；绕过此层直接访问 mapper 会破坏分层）。
 */
public interface WearQueryService {

    /**
     * 某自然日「可能佩戴过」的用户 ID 列表（存在与该日有交集的会话，去重）。
     * <p>
     * 供 00:30 每日结算扫描目标：只结算这些用户，避免枚举全体用户制造 0 佩戴噪音。
     *
     * @param date 结算目标自然日（完整自然日）
     * @return 去重后的用户 ID
     */
    List<Long> listCandidatesForSettle(LocalDate date);

    /**
     * 当前是否有佩戴中会话（睡前提醒等过滤已戴上用户）。
     *
     * @param userId 用户 ID
     * @return 是否有未结束会话
     */
    boolean hasOpenSession(Long userId);
}
