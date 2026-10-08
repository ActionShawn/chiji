package com.chiji.module.wear.service;

import com.chiji.module.wear.dto.WearMakeupRequest;
import com.chiji.module.wear.dto.WearMorningBackfillRequest;
import com.chiji.module.wear.dto.WearSessionEditRequest;
import com.chiji.module.wear.vo.ConsultSummaryVO;
import com.chiji.module.wear.vo.GoalVO;
import com.chiji.module.wear.vo.TodaySessionVO;
import com.chiji.module.wear.vo.TodayWearVO;
import com.chiji.module.wear.vo.WearAlignerSummaryVO;
import com.chiji.module.wear.vo.WearStatsVO;
import com.chiji.module.wear.vo.WearTopVO;

import java.time.LocalDate;
import java.util.List;

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
     * @param mode   当前记录模式（CLEAR_SINGLE / CLEAR_DUAL）：WEAR_ON 时据此把会话归属到
     *               当前选择模式的 ACTIVE 阶段当前副；为空则不区分模式
     * @return 操作后的今日工作台
     */
    TodayWearVO punch(Long userId, String action, String mode);

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
     * 佩戴统计（含今天；口径见 range）。
     *
     * @param userId 用户 ID
     * @param range  LAST_7（近 7 天）/ CURRENT_MONTH（自然月 1 号→今天）/ LAST_30（近 30 天）
     * @return 统计视图
     */
    WearStatsVO stats(Long userId, String range);

    /**
     * 某副牙套的佩戴汇总（换副历史 / 阶段详情）。
     * <p>
     * 按会话的 {@code aligner_id} 归属统计：仅累计归属该副的会话，跨日会话按
     * {@code [当日00:00, 次日00:00)} 逐日切分；佩戴中会话计入截至当前；无贡献返回空列表。
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

    /**
     * 查询某自然日的所有佩戴会话（含跨夜会话在该日的裁剪段），按开始时间升序。
     * <p>
     * 即为「时长校准」Sheet 该日列表的数据源；空态返回空列表。
     *
     * @param userId 用户 ID
     * @param date   目标自然日 yyyy-MM-dd
     * @return 当日会话列表（升序）
     */
    List<TodaySessionVO> sessions(Long userId, LocalDate date);

    /**
     * 编辑某会话的开始/结束时间（时间方向不限，均保持同日与「非未来/非重叠/单日≤24h」约束）。
     * <p>
     * 结束的 MANUAL 会话 start/end 均改；佩戴中 MANUAL 仅允许改 start、end 保持 null。
     * MANUAL 编辑后置 {@code edited=1}（打「修正」标），并按字段写入编辑留痕；随后重算当日结算。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param req       新开始/结束时间
     * @return 操作后的今日工作台
     */
    TodayWearVO updateSession(Long userId, Long sessionId, WearSessionEditRequest req);

    /**
     * 删除某条已结束的佩戴会话（{@code source} 为 MANUAL 打卡 或 MAKEUP 补录均可删，二次确认由前端负责）。
     * <p>
     * 删除前写 DELETE 留痕；跨夜会话可能覆盖多个自然日，删除后重算其覆盖到的每一天结算。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 操作后的今日工作台
     */
    TodayWearVO deleteSession(Long userId, Long sessionId);

    /**
     * 跨夜会话按自然日拆分（每日 00:10 定时任务调用）。
     * <p>
     * 把「跨过 00:00 仍佩戴中」的会话在自然日边界切开：前一天收口为 {@code startedAt → 次日00:00}，
     * 并为当天另起一条 00:00 起的佩戴中会话（继承 {@code source}/{@code alignerId}）。使每个自然日
     * 对应独立一行，删除/编辑某天不再波及别的天；收口的每一天重算结算。缺当日任务时可跨多日自愈补齐。
     *
     * @return 实际切分出的会话条数（0 表示无跨夜佩戴中会话）
     */
    int splitOpenSessionsAcrossMidnight();

    /**
     * 摘下超时提醒下发围栏（BE-6 handler 执行时调用）：该摘下事件对应的会话确实存在且
     * 已被摘下动作收口，且用户此后未戴回（当前无任何佩戴中会话）。
     * <p>
     * 戴回 / 换副会按业务键 {@code takeoff-timeout:<sessionId>} 取消任务；本围栏兜底
     * 取消与执行的竞态。
     *
     * @param userId    用户 ID
     * @param sessionId 摘下动作收口的佩戴会话 ID
     * @return true 表示仍处摘下中，可继续下发
     */
    boolean isTakeoffSessionOpen(Long userId, Long sessionId);

    /**
     * 最近一条已收口（摘下）的佩戴会话 ID（BE-5 换副取消摘下超时任务时定位业务键用）。
     *
     * @param userId 用户 ID
     * @return 最近收口会话 ID；无任何已收口会话返回 null
     */
    Long latestClosedSessionId(Long userId);

    /**
     * 用户当前每日佩戴目标（秒；缺失或非法回退默认目标）。
     * <p>
     * 供摘下超时提醒跨零点重算使用（2026-10-08 新口径：跨日到点须按「新一天目标」重算，
     * 不能用摘下时刻的快照值——用户可能已改目标）。
     *
     * @param userId 用户 ID
     * @return 目标秒数
     */
    int currentGoalSec(Long userId);

    /**
     * 取消最近一次摘下投递的超时任务（BE-5：换副结算旧会话事务内调用，幂等）。
     * <p>
     * 业务键按「最近一条已收口会话」定位；无摘下记录时为无害空操作。
     * 覆盖两种换副场景：佩戴中换副（旧会话刚被拆分收口）与摘下中换副（任务已投递未触发）。
     *
     * @param userId 用户 ID
     */
    void cancelLatestTakeoffTimeout(Long userId);

    /**
     * 复诊小结统计（BE-8）。
     * <p>
     * 口径：未达标 = 当日实际 &lt; 个人目标（无「接近」档）；目标时长设置日（{@code user_setting}
     * 最近更新日）之前的日子不计入未达标（无基准不评判），中途改目标不追溯；跨零点会话按
     * 自然日切分归属（与今日工作台/统计共用 {@code WearDayMath.distribute} 同一套切分）。
     * 三态由后端权威判定（viewState）：DATA（周期内有佩戴记录）/ STAGE_NO_RECORD（有阶段但
     * 所选周期内零打卡）/ NO_STAGE（从未创建阶段，含 stageId 非本人且无任何副）；empty 收窄为
     * viewState != DATA。周期终点真实判定：进行中/含今日 endDate=null 且 inProgress=true；
     * 已结束阶段统计窗口收口至阶段结束日（不混入后续阶段会话）。ALIGNER 档无进行中副回落
     * 「本阶段最后一副全程」，阶段不可解析再回落用户最近一副；recordDays 为周期内有佩戴
     * 记录的天数（含补录），与 aligner.wornDays 的副自然日口径不同名不混用。
     *
     * @param userId  用户 ID
     * @param period  ALIGNER（本副）/ STAGE（阶段）/ LAST30（近 30 天）
     * @param stageId period=STAGE 时的目标阶段（语义与月历接口一致：空回退 ACTIVE 阶段）
     * @return 复诊小结统计视图
     */
    ConsultSummaryVO consultSummary(Long userId, String period, Long stageId);
}
