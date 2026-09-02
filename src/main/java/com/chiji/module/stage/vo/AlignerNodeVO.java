package com.chiji.module.stage.vo;

import lombok.Builder;

import java.util.List;

/**
 * 牙套副节点 VO（项目卡片的迷你时间轴节点）。
 * <p>
 * 对应前端 {@code alignerNodes[]} 的单条元素，字段由后端从 {@link com.chiji.entity.Aligner} 派生。
 *
 * @param id            牙套副 ID（前端换副接口 /api/aligners/{id}/finish|revert 依赖此字段）
 * @param idx           数组下标（0-based，对应前端 wx:for index）
 * @param label         节点标签（单模「第8副」；双模「第4组·软膜」/「第4组·硬膜」）
 * @param num           副序号（1-based，双模下全局连续：软奇硬偶）
 * @param groupNum      组号（双模 = ceil(num/2)；单模 = num）
 * @param filmType      膜片类型（SOFT/HARD，仅双模阶段有值，单模 null）
 * @param state         佩戴状态小写：done / active / future
 * @param startDate     佩戴开始日期（ISO yyyy-MM-dd；未排期/未开始可为 null，时间调整页依赖）
 * @param endDate       佩戴结束日期（ISO yyyy-MM-dd；进行中/未排期可为 null，时间调整页依赖）
 * @param dates         日期范围文案（如「03.10–03.16」或「08.01–今天」），future 为 null
 * @param day           当前佩戴天数（仅 active 有值）
 * @param total         应佩戴总天数（active/done 有值，future 可有值用于展示）
 * @param pct           进度百分比（仅 active 有值，0-100）
 * @param progressLabel 进度文案（如「第6/7天」，仅 active 有值）
 * @param thumbs        印记缩略列表（首期空数组）
 */
@Builder
public record AlignerNodeVO(
        Long id,
        Integer idx,
        String label,
        Integer num,
        Integer groupNum,
        String filmType,
        String state,
        String startDate,
        String endDate,
        String dates,
        Integer day,
        Integer total,
        Integer pct,
        String progressLabel,
        List<AlignerThumbVO> thumbs
) {
}
