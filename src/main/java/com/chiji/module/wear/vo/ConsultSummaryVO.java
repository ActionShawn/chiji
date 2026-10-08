package com.chiji.module.wear.vo;

import java.util.List;

/**
 * 复诊小结统计 VO（BE-8，供复诊准备页 / 医患沟通页展示）。
 * <p>
 * 三态由后端权威判定（{@code viewState}）：DATA（周期内有佩戴记录）/
 * STAGE_NO_RECORD（有阶段但所选周期内零打卡）/ NO_STAGE（从未创建阶段，含 stageId 非本人
 * 且无任何副）。{@code empty} 为兼容保留字段，语义收窄为 {@code viewState != DATA}。
 *
 * @param period     统计周期：ALIGNER（本副）/ STAGE（阶段）/ LAST30（近 30 天）
 * @param viewState  后端权威三态：DATA / STAGE_NO_RECORD / NO_STAGE
 * @param empty      兼容保留：viewState != DATA 时为 true
 * @param startDate  周期起点 yyyy-MM-dd；NO_STAGE / 周期内无数据为 null
 * @param endDate    周期终点 yyyy-MM-dd；进行中 / 统计含今日为 null
 * @param recordDays 周期内有佩戴记录的天数（含补录；与 aligner.wornDays 的副自然日口径不同名不混用）
 * @param stage      解析到的所属阶段（ALIGNER / STAGE 档）；LAST30 恒 null；NO_STAGE 为 null
 * @param totalSec   周期内总佩戴秒
 * @param avgSec     日均佩戴秒（按「有佩戴日」日均，与既有统计口径一致）
 * @param underCount 未达标日计数（未达标 = 当日实际 &lt; 个人目标，无「接近」档）
 * @param underDays  未达标日列表（目标基准日之前的日子不评判不纳入，见 PRD 边界）
 * @param inProgress 真实判定：统计窗口含今日（已结束阶段 / 已收口窗口为 false）
 * @param aligner    目标副进度（当前副 / 本阶段最后一副 / 最近一副，按档位回落）；无任何副为 null
 */
public record ConsultSummaryVO(
        String period,
        String viewState,
        boolean empty,
        String startDate,
        String endDate,
        int recordDays,
        StageInfo stage,
        long totalSec,
        long avgSec,
        int underCount,
        List<UnderDay> underDays,
        boolean inProgress,
        AlignerProgress aligner
) {

    /** 三态：有数据。 */
    public static final String STATE_DATA = "DATA";
    /** 三态：有阶段但所选周期内零打卡（含新阶段未开始佩戴、已结束阶段无记录）。 */
    public static final String STATE_STAGE_NO_RECORD = "STAGE_NO_RECORD";
    /** 三态：从未创建阶段（含 stageId 非本人且无任何副）。 */
    public static final String STATE_NO_STAGE = "NO_STAGE";

    /**
     * 阶段信息（态C 阶段卡 / 区间展示）。
     *
     * @param id        阶段 ID
     * @param name      阶段名称
     * @param startDate 阶段开始日期 yyyy-MM-dd（创建时未设置则为 null）
     * @param endDate   阶段结束日期（无独立列，由阶段内最后一副结束日派生；进行中阶段为 null）
     */
    public record StageInfo(Long id, String name, String startDate, String endDate) {
    }

    /**
     * 未达标日明细。
     *
     * @param date    自然日 yyyy-MM-dd
     * @param wearSec 当日实际佩戴秒（跨零点会话已按自然日切分归属）
     * @param goalSec 当日评判所用目标秒（当前目标值）
     */
    public record UnderDay(String date, long wearSec, long goalSec) {
    }

    /**
     * 副进度。
     *
     * @param alignerId   目标副 ID
     * @param stageId     所属阶段 ID
     * @param num         第 X 副（1-based）
     * @param total       该阶段共 Y 副（双模含软/硬全部节点）
     * @param wornDays    本副开始日起至今自然日数（含今日；既有口径，不随副收口变化）
     * @param plannedDays 本副计划天数
     * @param startDate   本副开始日期 yyyy-MM-dd（未排期为 null）
     * @param endDate     本副结束日期 yyyy-MM-dd；进行中副为 null
     */
    public record AlignerProgress(Long alignerId, Long stageId, int num, int total,
                                  int wornDays, int plannedDays,
                                  String startDate, String endDate) {
    }
}
