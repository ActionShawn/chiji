package com.chiji.module.clinic.service.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.entity.User;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.enums.StageStatusEnum;
import com.chiji.enums.TreatmentTypeEnum;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.support.StageModeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 隐形牙套预约提醒策略（一期）。
 * <p>
 * 仅在用户佩戴「最终副」（当前副 num == 阶段总副数，双模按节点数折算）时处于预约窗口，
 * 提醒日 = 最终副计划结束日 − offsetDays；中途副、无计划数据一律返回 null（不打扰）。
 * 延期佩戴由计划结束日实时重算自然覆盖，无残留提醒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClearAlignerBookingRemindStrategy implements BookingRemindStrategy {

    private final UserMapper userMapper;
    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;

    @Override
    public boolean supports(String treatmentType) {
        return TreatmentTypeEnum.CLEAR_ALIGNER.name().equals(treatmentType);
    }

    @Override
    public LocalDate computeRemindDate(Long userId, int offsetDays) {
        LocalDate anchor = computeWindowAnchor(userId);
        return anchor == null ? null : anchor.minusDays(Math.max(0, offsetDays));
    }

    @Override
    public LocalDate computeWindowAnchor(Long userId) {
        return computeWindowAnchor(userId, null);
    }

    @Override
    public LocalDate computeWindowAnchor(Long userId, Long stageId) {
        User user = userMapper.selectById(userId);
        if (user == null || !supports(user.getTreatmentType())) {
            return null;
        }
        Stage stage = resolveStage(userId, stageId);
        // 仅 ACTIVE 阶段存在「预约窗口」：历史阶段已结束，不渲染预约卡
        if (stage == null || !StageStatusEnum.ACTIVE.name().equals(stage.getStatus())) {
            return null;
        }
        Aligner active = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stage.getId())
                .eq(Aligner::getState, AlignerStateEnum.ACTIVE.name())
                .orderByDesc(Aligner::getId)
                .last("LIMIT 1"));
        if (active == null || active.getNum() == null) {
            return null;
        }
        Integer totalNodes = StageModeSupport.resolveTotalNodes(stage);
        if (totalNodes == null || active.getNum() < totalNodes) {
            // 非最终副：中途不打扰
            return null;
        }
        return plannedEndDate(active);
    }

    @Override
    public LocalDate computeStageExpectedEnd(Long userId) {
        return computeStageExpectedEnd(userId, null);
    }

    @Override
    public LocalDate computeStageExpectedEnd(Long userId, Long stageId) {
        User user = userMapper.selectById(userId);
        if (user == null || !supports(user.getTreatmentType())) {
            return null;
        }
        Stage stage = resolveStage(userId, stageId);
        if (stage == null) {
            return null;
        }
        // 阶段最后一副（不限佩戴状态，FUTURE 副同样有计划结束日），与首页「预计完成日」同口径
        Aligner last = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stage.getId())
                .orderByDesc(Aligner::getNum)
                .orderByDesc(Aligner::getId)
                .last("LIMIT 1"));
        return last == null ? null : plannedEndDate(last);
    }

    /**
     * 解析目标阶段：stageId 非空按 ID 查（校验归属该用户，防越权）；
     * 为空回退用户当前 ACTIVE 阶段（服务端扫描等无选中语境的场景）。
     */
    private Stage resolveStage(Long userId, Long stageId) {
        if (stageId != null) {
            Stage stage = stageMapper.selectById(stageId);
            return stage == null || !userId.equals(stage.getUserId()) ? null : stage;
        }
        return activeStage(userId);
    }

    /** 用户当前 ACTIVE 阶段（不存在返回 null）。 */
    private Stage activeStage(Long userId) {
        return stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getUserId, userId)
                .eq(Stage::getStatus, StageStatusEnum.ACTIVE.name())
                .orderByDesc(Stage::getId)
                .last("LIMIT 1"));
    }

    /**
     * 取 ACTIVE 副计划结束日：优先显式结束日期，缺失时按起始日 + 总天数 − 1 推算。
     */
    private LocalDate plannedEndDate(Aligner active) {
        if (active.getEndDate() != null) {
            return active.getEndDate();
        }
        if (active.getStartDate() != null && active.getTotalDays() != null && active.getTotalDays() > 0) {
            return active.getStartDate().plusDays(active.getTotalDays() - 1L);
        }
        return null;
    }
}
