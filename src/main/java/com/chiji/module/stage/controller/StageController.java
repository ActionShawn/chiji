package com.chiji.module.stage.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.stage.dto.CreateStageRequest;
import com.chiji.module.stage.dto.UpdateStageStatusRequest;
import com.chiji.module.stage.service.StageService;
import com.chiji.module.stage.vo.AlignerNodeVO;
import com.chiji.module.stage.vo.StageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 阶段管理接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 阶段状态为两态：启用 / 结束，同一时间至多一个启用阶段。
 */
@Tag(name = "阶段", description = "矫正阶段的创建与管理")
@RestController
@RequestMapping("/api/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageService stageService;

    /**
     * 开启新阶段。
     * <p>
     * 创建一个 ACTIVE 状态的新阶段，自动结束旧的启用阶段，sort_order 追加到末尾。
     *
     * @param request 创建请求体
     * @return 新阶段的展示 VO
     */
    @Operation(summary = "开启新阶段", description = "创建一个 ACTIVE 状态的新阶段，自动结束旧启用阶段，sort_order 追加到末尾")
    @PostMapping
    public R<StageVO> createStage(@Valid @RequestBody CreateStageRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(stageService.createStage(userId, request), "新阶段已开启");
    }

    /**
     * 查询指定阶段的牙套节点列表。
     * <p>
     * 首页切换阶段时按需加载该阶段的 alignerNodes。会校验阶段归属，越权返回空列表。
     *
     * @param id 阶段 ID
     * @return 牙套节点列表（按 num 升序）
     */
    @Operation(summary = "查询阶段牙套节点", description = "切换阶段时按需加载该阶段的 alignerNodes")
    @GetMapping("/{id}/aligners")
    public R<List<AlignerNodeVO>> getStageAligners(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(stageService.getStageAligners(userId, id));
    }

    /**
     * 更新阶段状态。
     * <p>
     * 支持 ACTIVE ↔ ENDED 双向切换：切到 ACTIVE 时自动把旧启用阶段置为 ENDED（保持唯一启用）。
     *
     * @param id      阶段 ID
     * @param request 状态更新请求
     * @return 更新后的阶段展示 VO
     */
    @Operation(summary = "更新阶段状态", description = "启用 / 结束双向切换，切到 ACTIVE 自动结束旧启用阶段")
    @PutMapping("/{id}/status")
    public R<StageVO> updateStageStatus(@PathVariable Long id, @Valid @RequestBody UpdateStageStatusRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        StageVO vo = stageService.updateStageStatus(userId, id, request);
        String msg = "ACTIVE".equalsIgnoreCase(request.status()) ? "阶段已启用" : "阶段已结束";
        return R.ok(vo, msg);
    }

    /**
     * 删除阶段（逻辑删除）。
     * <p>
     * 标记阶段及其牙套副 deleted=1，数据保留可恢复，前端不再展示。
     * 启用阶段也可删除（前端需二次确认）。
     *
     * @param id 阶段 ID
     * @return 空响应
     */
    @Operation(summary = "删除阶段", description = "逻辑删除阶段及其牙套副，数据保留可恢复")
    @DeleteMapping("/{id}")
    public R<Void> deleteStage(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        stageService.deleteStage(userId, id);
        return R.ok(null, "阶段已删除");
    }
}
