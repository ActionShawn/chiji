package com.chiji.module.stage.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.adjustment.service.AdjustmentRecordService;
import com.chiji.module.adjustment.vo.FixedAdjustmentPanelVO;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.clinic.service.ClinicVisitService;
import com.chiji.module.rubberband.service.RubberBandService;
import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import com.chiji.module.stage.service.HomeService;
import com.chiji.module.stage.support.TreatmentGateSupport;
import com.chiji.module.stage.vo.HomeSummaryVO;
import com.chiji.module.treatment.service.TreatmentService;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.vo.WearTopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 */
@Tag(name = "首页", description = "首页聚合数据")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final WearService wearService;
    private final TreatmentService treatmentService;
    private final RubberBandService rubberBandService;
    private final AdjustmentRecordService adjustmentRecordService;
    private final ClinicVisitService clinicVisitService;

    /**
     * 获取首页聚合数据（按记录模式过滤）。
     * <p>
     * 一次返回该模式下的阶段列表、当前阶段的牙套节点、鼓励横幅，供小程序首页渲染。
     * 记录模式即工作台：单模 / 双模的阶段数据彼此独立，模式由前端切换传入。
     * <p>
     * 固定托槽用户（BE-FB-09）额外并入橡皮筋面板与复诊调整面板（零新增请求红线）；
     * 门控以 {@code user.treatment_type} 为唯一事实源，隐形用户响应无新增字段（NON_NULL 不序列化）。
     *
     * @param mode 记录模式编码（CLEAR_SINGLE / CLEAR_DUAL / FIXED_BRACKET，缺省 CLEAR_SINGLE）
     * @return 首页聚合数据
     */
    @Operation(summary = "首页聚合数据", description = "按记录模式过滤，一次返回阶段、牙套节点、横幅；固定托槽并入固定面板")
    @GetMapping("/summary")
    public R<HomeSummaryVO> getHomeSummary(@RequestParam(required = false) String mode) {
        Long userId = SecurityUtil.getCurrentUserId();
        HomeSummaryVO base = homeService.getHomeSummary(userId, mode);
        // 合并佩戴时长概览：无 ACTIVE 副时 wearTop 为 null（前端不渲染佩戴条）
        WearTopVO wearTop = wearService.wearTop(userId);

        // 固定托槽面板并入（门控唯一事实源；隐形用户两字段保持 null，响应无新增字段）
        RubberBandRecentVO rubberBand = null;
        FixedAdjustmentPanelVO fixedAdjustments = null;
        if (TreatmentGateSupport.isFixedBracket(treatmentService.getUserTreatmentType(userId))) {
            rubberBand = rubberBandService.recent(userId);
            FixedAdjustmentPanelVO panel = adjustmentRecordService.panel(userId);
            fixedAdjustments = new FixedAdjustmentPanelVO(
                    panel.total(), clinicVisitService.nextVisitDate(userId), panel.items());
        }

        return R.ok(new HomeSummaryVO(
                base.banner(),
                base.stages(),
                base.activeStageIndex(),
                base.activeAlignerIndex(),
                base.alignerNodes(),
                wearTop,
                rubberBand,
                fixedAdjustments));
    }
}
