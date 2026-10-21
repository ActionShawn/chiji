package com.chiji.module.adjustment.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.adjustment.dto.AdjustmentSaveRequest;
import com.chiji.module.adjustment.service.AdjustmentRecordService;
import com.chiji.module.adjustment.vo.AdjustmentPageVO;
import com.chiji.module.adjustment.vo.AdjustmentVO;
import com.chiji.module.auth.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 复诊调整记录接口（固定托槽第 1 批，REQ-FB-004）。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 仅治疗方式为 FIXED_BRACKET 的用户可访问（服务层门控 403）。
 */
@Tag(name = "复诊调整", description = "固定托槽复诊调整记录的增删改查")
@RestController
@RequestMapping("/api/adjustments")
@RequiredArgsConstructor
public class AdjustmentController {

    private final AdjustmentRecordService adjustmentRecordService;

    /**
     * 分页查询复诊调整记录列表。
     *
     * @param page 页码（1-based，默认 1）
     * @param size 每页条数（默认 20，上限 50）
     * @return 分页 VO（adjust_date 倒序、同日 createdAt 倒序）
     */
    @Operation(summary = "分页查询复诊调整列表", description = "按调整日期倒序、同日创建时间倒序分页")
    @GetMapping
    public R<AdjustmentPageVO> list(@RequestParam(required = false, defaultValue = "1") Integer page,
                                    @RequestParam(required = false, defaultValue = "20") Integer size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(adjustmentRecordService.list(userId, page, size));
    }

    /**
     * 新增复诊调整记录（同事务双写时光轴）。
     *
     * @param request 新增/编辑请求
     * @return 保存后的记录 VO
     */
    @Operation(summary = "新增复诊调整记录", description = "同事务双写 timeline_record(badge=ADJUSTMENT)，门控 403")
    @PostMapping
    public R<AdjustmentVO> create(@Valid @RequestBody AdjustmentSaveRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        AdjustmentVO vo = adjustmentRecordService.create(userId, request);
        return R.ok(vo, "已保存");
    }

    /**
     * 编辑复诊调整记录（同步刷新时光轴 text 摘要）。
     *
     * @param id      记录 ID
     * @param request 编辑请求
     * @return 更新后的记录 VO
     */
    @Operation(summary = "编辑复诊调整记录", description = "同步刷新时光轴 text 摘要与 recordDate")
    @PutMapping("/{id}")
    public R<AdjustmentVO> update(@PathVariable Long id, @Valid @RequestBody AdjustmentSaveRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        AdjustmentVO vo = adjustmentRecordService.update(userId, id, request);
        return R.ok(vo, "已更新");
    }

    /**
     * 删除复诊调整记录（先删时光轴，再删自身，同事务）。
     *
     * @param id 记录 ID
     * @return 操作结果
     */
    @Operation(summary = "删除复诊调整记录", description = "逻辑删除时光轴记录与本表行，同事务")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        adjustmentRecordService.delete(userId, id);
        return R.ok(null, "已删除");
    }
}
