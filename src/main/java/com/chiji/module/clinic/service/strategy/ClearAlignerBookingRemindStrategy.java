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
        User user = userMapper.selectById(userId);
        if (user == null || !supports(user.getTreatmentType())) {
            return null;
        }
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getUserId, userId)
                .eq(Stage::getStatus, StageStatusEnum.ACTIVE.name())
                .orderByDesc(Stage::getId)
                .last("LIMIT 1"));
        if (stage == null) {
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
