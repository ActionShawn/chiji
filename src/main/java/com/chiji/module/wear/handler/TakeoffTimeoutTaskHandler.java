package com.chiji.module.wear.handler;

import com.chiji.entity.DelayTask;
import com.chiji.module.delay.handler.DelayTaskHandler;
import com.chiji.module.delay.handler.DelayTaskRescheduleException;
import com.chiji.module.delay.handler.DelayTaskSkipException;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.module.message.service.TakeoffNotifyService;
import com.chiji.module.wear.service.WearService;
import com.chiji.module.wear.support.TakeoffTimeoutSpec;
import com.chiji.module.wear.support.WearTimes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 摘下超时提醒下发 handler（BE-6，实现 delay 模块 handler SPI）。
 * <p>
 * 执行链（每步失败即止）：
 * <ol>
 *   <li>业务围栏：校验摘下状态未被戴回 / 换副打断（{@link WearService#isTakeoffSessionOpen}），
 *       否则 {@link DelayTaskSkipException}（记 last_error、不耗重试）；</li>
 *   <li>智能模式跨日重算（PRD 2026-10-08 修订：可摘下时间仅针对当日）——摘下事件跨零点延续到
 *       到点时：不下发昨日快照（文案已失真），按新一天账本重算并改期
 *       （{@link DelayTaskRescheduleException}，任务行不复制、biz_key 不变；改期时把 payload 内
 *       thing4 改写为跨零点可见性文案 {@link TakeoffTimeoutSpec#THING4_SMART_CROSS_DAY_RESCHEDULE}
 *       并打 {@link TakeoffTimeoutSpec#PAYLOAD_KEY_CROSS_DAY_RESCHEDULED} 标记——改期后的到点凭标记
 *       跳过重算、直接下发新文案，防摘下日期恒为昨日而二次进入重算）或立即下发跨零点即达文案；
 *       固定模式跨零点连续计时不变（固定文案不含时间信息，不失真）；</li>
 *   <li>额度预扣：{@link SubscribeQuotaService#consumeIfAvailable}——下发<b>尝试即扣、失败不回补</b>，
 *       无额度 {@link DelayTaskSkipException}；</li>
 *   <li>下发 + 留痕：{@link TakeoffNotifyService#sendAndArchive}（复用 message 表，不新建日志表）；</li>
 *   <li>下发失败（微信拒绝 / 本地异常）同样以 {@link DelayTaskSkipException} 结束——视为业务性结果，
 *       不重试（重试会导致重复预扣额度），原因记录在任务 last_error。</li>
 * </ol>
 * 「每次摘下事件最多 1 条下发」跨零点不重置：改期不是重复下发；改期后的任务到点若用户已戴回，
 * 由第 1 步围栏覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TakeoffTimeoutTaskHandler implements DelayTaskHandler {

    /** 任务类型 / 订阅场景码（与 {@link SubscribeQuotaService#SCENE_TAKEOFF_TIMEOUT} 同名） */
    public static final String TASK_TYPE = TakeoffTimeoutSpec.TASK_TYPE;

    private final WearService wearService;
    private final SubscribeQuotaService subscribeQuotaService;
    private final TakeoffNotifyService takeoffNotifyService;
    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return TASK_TYPE;
    }

    @Override
    public String owner() {
        return "wear";
    }

    @Override
    public void execute(DelayTask task) throws Exception {
        JsonNode payload = objectMapper.readTree(task.getPayload() == null ? "{}" : task.getPayload());
        long userId = payload.path("userId").asLong();
        long sessionId = payload.path("sessionId").asLong();
        String remindMode = payload.path("remindMode").asText("");
        String takeoffAtText = payload.path("takeoffAtText").asText("");
        String thing4 = payload.path("thing4").asText("");
        if (userId <= 0 || sessionId <= 0) {
            throw new DelayTaskSkipException("payload 缺少 userId/sessionId，跳过下发");
        }

        // ① 业务围栏：摘下状态未被戴回 / 换副打断（戴回与换副会按业务键取消本任务，此处兜底竞态）
        if (!wearService.isTakeoffSessionOpen(userId, sessionId)) {
            throw new DelayTaskSkipException("会话已戴回或已换副，跳过下发");
        }

        // ② 智能模式跨日重算：摘下事件跨零点延续到到点 → 按新一天账本重排或立即下发跨零点文案。
        //    跨日判据用「摘下日期 != 当前日期」（取自 payload takeoffAtMs），比 executeAt 日期判据
        //    更完备：调度泵延迟导致 executeAt 界内但实际跨日执行时同样命中。
        //    已重排任务（crossDayRescheduled=true，重排时写入 payload）跳过重算：其摘下日期恒为
        //    昨日，若二次进入重算分支，公式随时间双重递减使剩余必然 ≤5 分钟，会错误改发
        //    「不足」即达文案——重排时已把 thing4 定稿为跨零点可见性文案，到点直接走原路径下发。
        boolean crossDayRescheduled =
                payload.path(TakeoffTimeoutSpec.PAYLOAD_KEY_CROSS_DAY_RESCHEDULED).asBoolean(false);
        if (TakeoffTimeoutSpec.MODE_SMART.equals(remindMode) && !crossDayRescheduled) {
            LocalDateTime now = WearTimes.now();
            LocalDateTime takeoffAt = takeoffAtFrom(payload);
            if (takeoffAt != null && !takeoffAt.toLocalDate().equals(now.toLocalDate())) {
                rerouteCrossDay(payload, userId, remindMode, takeoffAtText, now, takeoffAt);
                return; // rerouteCrossDay 要么抛 DelayTaskRescheduleException，要么已下发
            }
        }

        // ③④ 当日原路径：预扣 → 下发（thing4 取 payload 定稿文案；重排后的任务其 payload thing4
        //     已在重排时改写为跨零点可见性版，此处自然取到新文案）
        consumeAndSend(userId, remindMode, takeoffAtText, thing4);
    }

    /**
     * 智能模式跨日重算分支：
     * <ul>
     *   <li>新目标取用户当前设置（{@link WearService#currentGoalSec}，非摘下时快照——用户可能已改目标）；
     *       未设置（≤0，理论上智能模式已开启必有目标）→ 防御性 skip 记 last_error；</li>
     *   <li>新一天已佩戴传 0：能存活到本分支的任务期间用户必然未戴回（戴回会取消任务 / 命中围栏），
     *       同一用户同一时刻至多一个会话，故新一天除本次跨零点连续摘下外无其他佩戴时段；</li>
     *   <li>剩余 &gt; 5 分钟 → 抛 {@link DelayTaskRescheduleException} 改期（不复制任务行，
     *       「每次摘下事件最多 1 条下发」不重置；改期同时把 payload 的 thing4 改写为跨零点可见性
     *       文案 {@link TakeoffTimeoutSpec#THING4_SMART_CROSS_DAY_RESCHEDULE} 并打重排标记——
     *       2026-10-08 文案拍板：凌晨收到普通文案用户无法理解提醒时刻为何与摘下时被告知的不同）；
     *       剩余 ≤ 5 分钟 → 立即下发跨零点即达文案（预扣 + 留痕同原链）。</li>
     * </ul>
     */
    private void rerouteCrossDay(JsonNode payload, long userId, String remindMode, String takeoffAtText,
                                 LocalDateTime now, LocalDateTime takeoffAt) throws DelayTaskRescheduleException {
        int newGoalSec = wearService.currentGoalSec(userId);
        if (newGoalSec <= 0) {
            throw new DelayTaskSkipException("跨零点重算失败：新目标未设置，跳过下发");
        }
        TakeoffTimeoutSpec.CrossDayReroute reroute =
                TakeoffTimeoutSpec.crossDayReroute(now, newGoalSec, 0L, takeoffAt);
        if (reroute.sendImmediately()) {
            log.info("摘下超时提醒跨零点即达上限，立即下发, userId={}", userId);
            consumeAndSend(userId, remindMode, takeoffAtText, TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY);
            return;
        }
        throw new DelayTaskRescheduleException(reroute.rescheduleAt(),
                "智能模式跨零点重算延期（可摘下时间仅针对当日，按新一天账本重排）",
                reschedulePayload(payload));
    }

    /**
     * 构建跨零点重排后的新 payload：thing4 改写为跨零点可见性文案
     * （{@link TakeoffTimeoutSpec#THING4_SMART_CROSS_DAY_RESCHEDULE}）并打
     * {@link TakeoffTimeoutSpec#PAYLOAD_KEY_CROSS_DAY_RESCHEDULED} 标记——重排后的到点凭标记
     * 跳过重算分支、直接下发新文案（其摘下日期恒为昨日，无标记会二次进入重算，
     * 公式随时间双重递减使剩余必然 ≤5 分钟，错误改发「不足」即达文案）。
     * 改写失败返回 null（保留原 payload 兜底）：到点将再次进入重算分支，剩余必然 ≤5 分钟，
     * 按跨零点即达文案下发（语义可接受的降级，极小概率——payload 由 buildPayload 生成必为合法 JSON）。
     */
    private String reschedulePayload(JsonNode payload) {
        try {
            Map<String, Object> map = objectMapper.readValue(payload.toString(),
                    new TypeReference<Map<String, Object>>() {});
            map.put("thing4", TakeoffTimeoutSpec.THING4_SMART_CROSS_DAY_RESCHEDULE);
            map.put(TakeoffTimeoutSpec.PAYLOAD_KEY_CROSS_DAY_RESCHEDULED, true);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("跨零点重排 payload 改写失败，保留原 payload（到点按即达口径兜底）", e);
            return null;
        }
    }

    /** 预扣当日额度后下发并留痕；无额度 / 下发失败均以 DelayTaskSkipException 结束（不回补、不重试）。 */
    private void consumeAndSend(long userId, String remindMode, String takeoffAtText, String thing4)
            throws DelayTaskSkipException {
        if (!subscribeQuotaService.consumeIfAvailable(userId, SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT)) {
            throw new DelayTaskSkipException("当日无可用额度，跳过下发（预扣不回补）");
        }
        TakeoffNotifyService.SendOutcome outcome = takeoffNotifyService.sendAndArchive(
                new TakeoffNotifyService.SendCommand(userId, remindMode, takeoffAtText, thing4));
        if (!outcome.success()) {
            throw new DelayTaskSkipException(
                    "下发失败 errcode=" + outcome.errcode() + " " + outcome.errmsg() + "；额度已预扣不回补");
        }
        log.info("摘下超时提醒下发成功, userId={}, remindMode={}, thing4={}", userId, remindMode, thing4);
    }

    /** 从 payload 解析摘下时刻（takeoffAtMs）；缺失或非法返回 null（走原路径兜底）。 */
    private LocalDateTime takeoffAtFrom(JsonNode payload) {
        if (!payload.hasNonNull("takeoffAtMs")) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(payload.get("takeoffAtMs").asLong()),
                    WearTimes.ZONE);
        } catch (Exception e) {
            log.warn("payload takeoffAtMs 解析失败，按未跨日兜底", e);
            return null;
        }
    }
}
