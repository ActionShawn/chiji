package com.chiji.module.wear.service;

import com.chiji.module.wear.dto.WearMakeupRequest;
import com.chiji.module.wear.dto.WearMorningBackfillRequest;
import com.chiji.module.wear.vo.GoalVO;
import com.chiji.module.wear.vo.TodayWearVO;
import com.chiji.module.wear.vo.WearAlignerSummaryVO;
import com.chiji.module.wear.vo.WearStatsVO;
import com.chiji.module.wear.vo.WearTopVO;

/**
 * 佩戴时长模块核心服务（隐形矫正工作台的「佩戴」页与首页联动）。
 * <p>
 * 会话模型：每段「戴上→摘下」一行（{@code wear_session}），跨午夜不预拆，按自然日
 * （Asia/Shanghai）切分归属；每日佩戴量实时由会话推导，「达标/部分/未达标」档位与
 * 连续达标由结算表（{@code wear_daily_summary}）物化后只读派生。本服务依赖 stage
 * 模块的 {@code AlignerService} 标记会话归属副、message 模块的
 * {@code ReminderNotifyService} 在首次触发达标时归档庆祝消息。
 */
public interface WearService {

    /**
     * 读取「佩戴」页今日工作台（实时）。会触发昨日懒结算（过了次日 00:05 且昨日未结算）。
     *
     * @param userId 用户 ID
     * @return 今日工作台视图
     */
    TodayWearVO today(Long userId);

    /**
     * 打卡：戴上（WEAR_ON）/ 摘下（WEAR_OFF）。
     * <ul>
     *   <li>WEAR_ON：当前无佩戴中会话时新建；已有则幂等返回（不重复开段）</li>
     *   <li>WEAR_OFF：结束当前佩戴中会话；无佩戴中会话则报 {@code WEAR_ACTIVE_SESSION_NOT_FOUND}</li>
     * </ul>
     * 摘下后若今日已达目标，首次（当日一次）归档「达标庆祝」消息并置 newlyFull。
     *
     * @param userId 用户 ID
     * @param action WEAR_ON / WEAR_OFF
     * @return 操作后的今日工作台
     */
    TodayWearVO punch(Long userId, String action);

    /**
     * 撤销当前佩戴中会话（误触「戴上」后回退）。
     * <p>
     * 仅允许撤销存在中的 MANUAL 会话；无佩戴中会话报 {@code WEAR_ACTIVE_SESSION_NOT_FOUND}。
     *
     * @param userId 用户 ID
     * @return 撤销后的今日工作台
     */
    TodayWearVO cancelActive(Long userId);

    /**
     * 校准/补录：为某历史自然日补录忘记打卡的时间段（MAKEUP，最近 7 天）。
     * <p>
     * 补录段按该日全额归属、与已有段互不重叠、每段 ≤24h、同日内总时长 ≤24h；
     * 补录后对该日重算结算（覆盖）。成功返回今日工作台（补录可能不含今天，页面自行刷新对应日）。
     *
     * @param userId 用户 ID
     * @param req    补录请求
     * @return 操作后的今日工作台
     */
    TodayWearVO makeup(Long userId, WearMakeupRequest req);

    /**
     * 晨起补记：昨晚睡前就戴上、今早补记（MANUAL 真实佩戴）。
     * <p>
     * 会话从昨晚 {@code date} 的 {@code startTime} 起；仍戴着则留佩戴中会话，否则到今日
     * {@code endTime} 结束。归属按自然日切分，不计入 makeup_sec。
     *
     * @param userId 用户 ID
     * @param req    补记请求
     * @return 操作后的今日工作台
     */
    TodayWearVO morningBackfill(Long userId, WearMorningBackfillRequest req);

    /**
     * 读取当前每日目标（未设置返回默认 20h）。
     */
    GoalVO getGoal(Long userId);

    /**
     * 更新每日目标（18.0–22.0h，0.5 步进，越界报 {@code WEAR_GOAL_OUT_OF_RANGE}）。
     *
     * @param userId    用户 ID
     * @param goalHours 目标小时
     * @return 更新后的目标视图
     */
    GoalVO updateGoal(Long userId, Double goalHours);

    /**
     * 佩戴统计（LAST_7 / LAST_30 天窗口，含今天）。
     *
     * @param userId 用户 ID
     * @param range  LAST_7 / LAST_30
     * @return 统计视图
     */
    WearStatsVO stats(Long userId, String range);

    /**
     * 某副牙套的佩戴汇总（换副历史 / 阶段详情）。
     * <p>
     * 仅统计 aligner_id 归属该副的会话，跨日会话按自然日切分；无贡献返回空列表。
     *
     * @param userId    用户 ID
     * @param alignerId 牙套副 ID
     * @return 该副佩戴汇总
     */
    WearAlignerSummaryVO alignerSummary(Long userId, Long alignerId);

    /**
     * 首页聚合用「佩戴概览」（轻量，不写庆祝/不懒结算；无 ACTIVE 副时返回 null）。
     *
     * @param userId 用户 ID
     * @return 顶部概览；无当前佩戴副时 null
     */
    WearTopVO wearTop(Long userId);
}
