package com.chiji.module.adjustment.service;

import com.chiji.module.adjustment.dto.AdjustmentSaveRequest;
import com.chiji.module.adjustment.vo.AdjustmentPageVO;
import com.chiji.module.adjustment.vo.AdjustmentVO;
import com.chiji.module.adjustment.vo.FixedAdjustmentPanelVO;

/**
 * 复诊调整记录服务（固定托槽第 1 批，REQ-FB-004）。
 * <p>
 * 实现同事务双写 timeline_record（badge_type=ADJUSTMENT、text 存加力内容拼接摘要、mediaKind=TEXT）
 * 与 {@code adjustment_record} 表，删除侧按评审 4.1 要点做双向联动。
 * 门控由 {@link com.chiji.module.stage.support.TreatmentGateSupport} 唯一事实源承担。
 */
public interface AdjustmentRecordService {

    /**
     * 新增复诊调整记录（同事务双写时光轴）。
     *
     * @param userId  当前用户 ID
     * @param request 新增/编辑请求（复用）
     * @return 保存后的记录 VO
     */
    AdjustmentVO create(Long userId, AdjustmentSaveRequest request);

    /**
     * 编辑复诊调整记录（同步刷新时光轴 text 摘要）。
     *
     * @param userId  当前用户 ID
     * @param id      记录 ID
     * @param request 编辑请求
     * @return 更新后的记录 VO
     */
    AdjustmentVO update(Long userId, Long id, AdjustmentSaveRequest request);

    /**
     * 删除复诊调整记录（先逻辑删除时光轴记录，再删自身）。
     *
     * @param userId 当前用户 ID
     * @param id     记录 ID
     */
    void delete(Long userId, Long id);

    /**
     * 分页查询当前用户的复诊调整记录（adjust_date 倒序、同日 createdAt 倒序）。
     *
     * @param userId 当前用户 ID
     * @param page  页码（1-based，< 1 视为 1）
     * @param size   每页条数（<=0 或超 50 视为 20）
     * @return 分页 VO
     */
    AdjustmentPageVO list(Long userId, Integer page, Integer size);

    /**
     * 时光轴删除联动回查：当 timeline_record 被逻辑删除时，若其 badge_type=ADJUSTMENT，
     * 反查本表一并逻辑删除（由 {@link com.chiji.module.stage.service.impl.RecordServiceImpl} 删除链路回调）。
     *
     * @param timelineRecordId 时光轴记录 ID
     */
    void deleteByTimelineRecordId(Long timelineRecordId);

    /**
     * 首页固定面板数据（并入 GET /api/home/summary，BE-FB-09，零新增请求红线）。
     * <p>
     * 返回累计调整次数与最近 10 条调整记录（adjust_date 倒序）；nextVisitDate 恒为 null，
     * 由 HomeController 并入小齿档案口径的下一次复诊日期（本模块不跨模块依赖 clinic）。
     *
     * @param userId 当前用户 ID
     * @return 固定面板 VO（total + items）
     */
    FixedAdjustmentPanelVO panel(Long userId);
}
