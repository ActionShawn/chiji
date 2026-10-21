package com.chiji.module.stage.vo;

import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import com.chiji.module.wear.vo.WearTopVO;
import com.chiji.module.adjustment.vo.FixedAdjustmentPanelVO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

/**
 * 首页聚合数据 VO。
 * <p>
 * 一次请求返回首页所需的全部数据：阶段列表、当前阶段的牙套节点、鼓励横幅。
 * 数据按记录模式过滤（模式即工作台），对应接口 {@code GET /api/home/summary?mode=}。
 * <p>
 * 固定托槽专属字段（BE-FB-09）：仅 {@code user.treatment_type = FIXED_BRACKET} 时由
 * HomeController 装填，两个新组件标注 {@code NON_NULL}——隐形用户响应保持与改动前
 * 逐字节等价（回归红线，隐形路径零新增字段）。
 *
 * @param banner             鼓励横幅
 * @param stages             当前模式下的阶段列表（按 sort_order 升序）
 * @param activeStageIndex   当前阶段在 stages 数组中的下标（无 current 阶段时为 -1）
 * @param activeAlignerIndex 当前佩戴副在 alignerNodes 数组中的下标（无时为 -1，供首页直定位当前副）
 * @param alignerNodes       当前阶段的牙套节点列表（按 num 升序）
 * @param wearTop            佩戴时长概览（由 HomeController 合并佩戴模块数据；无 ACTIVE 副时为 null）
 * @param rubberBand         橡皮筋今日状态 + 近 7 天点阵（仅固定托槽用户返回）
 * @param fixedAdjustments   复诊调整面板：累计次数 + 最近 10 条 + 下次复诊日期（仅固定托槽用户返回）
 */
@Builder
public record HomeSummaryVO(
        HomeBannerVO banner,
        List<StageVO> stages,
        Integer activeStageIndex,
        Integer activeAlignerIndex,
        List<AlignerNodeVO> alignerNodes,
        WearTopVO wearTop,
        @JsonInclude(JsonInclude.Include.NON_NULL) RubberBandRecentVO rubberBand,
        @JsonInclude(JsonInclude.Include.NON_NULL) FixedAdjustmentPanelVO fixedAdjustments
) {
}
