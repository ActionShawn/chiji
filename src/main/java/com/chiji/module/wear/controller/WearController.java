package com.chiji.module.wear.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.wear.dto.WearMakeupRequest;
import com.chiji.module.wear.dto.WearMorningBackfillRequest;
import com.chiji.module.wear.dto.WearPunchRequest;
import com.chiji.module.wear.dto.GoalUpdateRequest;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.vo.GoalVO;
import com.chiji.module.wear.vo.TodayWearVO;
import com.chiji.module.wear.vo.WearAlignerSummaryVO;
import com.chiji.module.wear.vo.WearStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 佩戴时长接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。覆盖「佩戴」页的打卡/撤销/补录/晨起补记、
 * 目标设置与统计，以及首页联动的副维度汇总。
 */
@Tag(name = "佩戴时长", description = "打卡/补录/目标/统计")
@RestController
@RequestMapping("/api/wear")
@RequiredArgsConstructor
public class WearController {

    private final WearService wearService;

    @Operation(summary = "今日工作台", description = "实时佩戴量/达标档/佩戴中状态/时间线")
    @GetMapping("/today")
    public R<TodayWearVO> today() {
        return R.ok(wearService.today(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "打卡", description = "body.action = WEAR_ON(戴上)/WEAR_OFF(摘下)；body.mode = 当前记录模式，用于会话归属当前阶段当前副")
    @PostMapping("/punch")
    public R<TodayWearVO> punch(@Valid @RequestBody WearPunchRequest request) {
        return R.ok(wearService.punch(SecurityUtil.getCurrentUserId(), request.action(), request.mode()));
    }

    @Operation(summary = "撤销佩戴中会话", description = "误触戴上后回退当前佩戴中会话")
    @PostMapping("/active-session/cancel")
    public R<TodayWearVO> cancelActive() {
        return R.ok(wearService.cancelActive(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "校准补录", description = "为最近 7 天的某自然日补录忘记打卡的时间段")
    @PostMapping("/makeup")
    public R<TodayWearVO> makeup(@Valid @RequestBody WearMakeupRequest request) {
        return R.ok(wearService.makeup(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "晨起补记", description = "昨晚就戴上了今早补记（昨晚 date=昨天）")
    @PostMapping("/morning")
    public R<TodayWearVO> morning(@Valid @RequestBody WearMorningBackfillRequest request) {
        return R.ok(wearService.morningBackfill(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "读取每日目标", description = "未设置返回默认 20h")
    @GetMapping("/goal")
    public R<GoalVO> getGoal() {
        return R.ok(wearService.getGoal(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "更新每日目标", description = "goalHours ∈ [18.0, 22.0]，0.5 步进")
    @PutMapping("/goal")
    public R<GoalVO> updateGoal(@Valid @RequestBody GoalUpdateRequest request) {
        return R.ok(wearService.updateGoal(SecurityUtil.getCurrentUserId(), request.goalHours()));
    }

    @Operation(summary = "佩戴统计", description = "range = LAST_7 / CURRENT_MONTH / LAST_30（含今天）")
    @GetMapping("/stats")
    public R<WearStatsVO> stats(@RequestParam(required = false) String range) {
        return R.ok(wearService.stats(SecurityUtil.getCurrentUserId(), range));
    }

    @Operation(summary = "某副牙套佩戴汇总", description = "换副历史/阶段详情：该副产生的总时长与逐日量")
    @GetMapping("/aligner/{alignerId}/summary")
    public R<WearAlignerSummaryVO> alignerSummary(@PathVariable Long alignerId) {
        return R.ok(wearService.alignerSummary(SecurityUtil.getCurrentUserId(), alignerId));
    }
}
