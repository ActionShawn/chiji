package com.chiji.module.stage.vo;

import lombok.Builder;

import java.util.List;

/**
 * 首页聚合数据 VO。
 * <p>
 * 一次请求返回首页所需的全部数据：阶段列表、当前阶段的牙套节点、鼓励横幅。
 * 数据按记录模式过滤（模式即工作台），对应接口 {@code GET /api/home/summary?mode=}。
 *
 * @param banner             鼓励横幅
 * @param stages             当前模式下的阶段列表（按 sort_order 升序）
 * @param activeStageIndex   当前阶段在 stages 数组中的下标（无 current 阶段时为 -1）
 * @param activeAlignerIndex 当前佩戴副在 alignerNodes 数组中的下标（无时为 -1，供首页直定位当前副）
 * @param alignerNodes       当前阶段的牙套节点列表（按 num 升序）
 */
@Builder
public record HomeSummaryVO(
        HomeBannerVO banner,
        List<StageVO> stages,
        Integer activeStageIndex,
        Integer activeAlignerIndex,
        List<AlignerNodeVO> alignerNodes
) {
}
