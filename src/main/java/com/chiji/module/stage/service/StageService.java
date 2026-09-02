package com.chiji.module.stage.service;

import com.chiji.module.stage.dto.CreateStageRequest;
import com.chiji.module.stage.dto.UpdateStageStatusRequest;
import com.chiji.module.stage.vo.AlignerNodeVO;
import com.chiji.module.stage.vo.StageVO;

import java.util.List;

/**
 * 矫正阶段服务。
 * <p>
 * 阶段状态为两态：同一时间至多一个启用阶段，新建时自动结束旧启用阶段。
 * 用户可在下拉框中任意切换查看不同阶段（含已结束）的牙套节点，
 * 也可手动调整阶段状态（启用 / 结束，双向）或删除阶段（逻辑删除）。
 */
public interface StageService {

    /**
     * 开启新阶段。
     * <p>
     * 创建一个 ACTIVE 状态的阶段（自动结束旧的启用阶段），排序权重追加到列表末尾。
     *
     * @param userId  当前用户 ID
     * @param request 创建请求
     * @return 新阶段的展示 VO
     */
    StageVO createStage(Long userId, CreateStageRequest request);

    /**
     * 查询指定阶段的牙套节点列表。
     * <p>
     * 用于首页切换阶段时按需加载该阶段的 alignerNodes。会校验阶段归属，防止越权。
     *
     * @param userId  当前用户 ID
     * @param stageId 阶段 ID
     * @return 牙套节点列表（按 num 升序）
     */
    List<AlignerNodeVO> getStageAligners(Long userId, Long stageId);

    /**
     * 更新阶段状态。
     * <p>
     * 支持 ACTIVE ↔ ENDED 双向切换：
     * <ul>
     *   <li>切到 ACTIVE：自动把当前用户其它 ACTIVE 阶段置为 ENDED，保持唯一启用</li>
     *   <li>切到 ENDED：仅本阶段状态变化，不影响其它阶段</li>
     * </ul>
     * 结束的阶段可重新启用，继续往里写入记录。
     *
     * @param userId  当前用户 ID
     * @param stageId 阶段 ID
     * @param request 状态更新请求
     * @return 更新后的阶段展示 VO
     */
    StageVO updateStageStatus(Long userId, Long stageId, UpdateStageStatusRequest request);

    /**
     * 删除阶段（逻辑删除）。
     * <p>
     * 标记阶段 deleted=1，并连带逻辑删除其下所有 aligner（数据保留可恢复，前端不再展示）。
     * 启用阶段的删除不做限制（前端弹二次确认）。
     *
     * @param userId  当前用户 ID
     * @param stageId 阶段 ID
     */
    void deleteStage(Long userId, Long stageId);
}
