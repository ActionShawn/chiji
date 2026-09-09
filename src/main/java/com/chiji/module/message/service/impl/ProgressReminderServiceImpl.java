package com.chiji.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.WearDailySummary;
import com.chiji.entity.WearSession;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.enums.StageStatusEnum;
import com.chiji.enums.WearLevelEnum;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.service.ProgressReminderService;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.message.support.ChinaHolidayCalendar;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.stage.support.StageModeSupport;
import com.chiji.module.wear.mapper.WearDailySummaryMapper;
import com.chiji.module.wear.mapper.WearSessionMapper;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 矫正进度/问候类提醒产出实现。见 {@link ProgressReminderService}。
 * <p>
 * 数据聚合自阶段/牙套副/时光轴/佩戴结算与佩戴会话；所有落库统一走
 * {@link ReminderNotifyService}（含总开关+类型开关门控与同日去重）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressReminderServiceImpl implements ProgressReminderService {

    /** 连续达标成就档位（天） */
    private static final int[] FULL_STREAK_LEVELS = {7, 14, 30};
    /** 进度分档（%） */
    private static final int[] PROGRESS_TIERS = {25, 50, 75, 100};
    /** 久未使用召回 / 连续未达标关怀的天数阈值 */
    private static final int IDLE_RECALL_DAYS = 3;
    private static final int UNDER_GOAL_DAYS = 3;

    private final NotificationSettingService notificationSettingService;
    private final ReminderNotifyService reminderNotifyService;
    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;
    private final TimelineRecordMapper timelineRecordMapper;
    private final WearDailySummaryMapper wearDailySummaryMapper;
    private final WearSessionMapper wearSessionMapper;

    // ───────────────────────── 换副 / 每日记录 ─────────────────────────

    @Override
    public boolean alignerOverdueRemind(Long userId) {
        if (!allowed(userId, WearReminderTypeEnum.ALIGNER_CHANGE)) {
            return false;
        }
        Ctx ctx = loadActiveCtx(userId);
        Aligner active = ctx == null ? null : ctx.activeAligner();
        if (active == null) {
            return false;
        }
        LocalDate plannedEnd = plannedEndDate(active);
        if (plannedEnd == null) {
            return false;
        }
        LocalDate today = WearTimes.today();
        if (!today.isAfter(plannedEnd)) {
            return false;
        }
        boolean last = active.getNum() != null && active.getNum().intValue() >= ctx.totalNodes();
        String title;
        String body;
        if (last) {
            title = "最后一程也到时间啦 🎉";
            body = "「" + stageLabel(ctx.stage()) + "」快戴满啦，" + alignerLabel(ctx.stage(), active.getNum())
                    + "结束后就可以收个漂亮的尾，收获满满的成就感～";
        } else {
            title = "该换下一副牙套啦 🦷";
            String[] variants = {
                    alignerLabel(ctx.stage(), active.getNum()) + "已经超计划时间了，今晚换上新的，让矫正一刻不停～",
                    alignerLabel(ctx.stage(), active.getNum()) + "佩戴超时啦，趁睡前换上下一段，距离整齐的笑容又近一步～",
            };
            body = variants[pick(userId, today, variants.length)];
        }
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.ALIGNER_CHANGE, today, title, body);
    }

    @Override
    public boolean dailyRecordRemind(Long userId) {
        if (!allowed(userId, WearReminderTypeEnum.DAILY_RECORD)) {
            return false;
        }
        if (loadActiveCtx(userId) == null) {
            return false;
        }
        LocalDate today = WearTimes.today();
        if (recordedOn(userId, today)) {
            return false;
        }
        String[] bodies = {
                "在时光轴留个脚印吧，一句话或一张照片都好，以后回头看会很有感触～",
                "今天还没记录哦，几分钟就能留下一个小纪念，让改变有迹可循～",
        };
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.DAILY_RECORD, today,
                "今天记得记录一下 🌿", bodies[pick(userId, today, bodies.length)]);
    }

    // ───────────────────────── GREETING 问候家族 ─────────────────────────

    @Override
    public boolean morningGreet(Long userId) {
        if (!allowed(userId, WearReminderTypeEnum.GREETING)) {
            return false;
        }
        // 只服务已有矫正阶段的用户（未建档的纯新用户不做打扰）
        if (hasStage(userId) == false) {
            return false;
        }
        LocalDate today = WearTimes.today();
        // 1. 久未使用召回（3 天无佩戴且无记录）
        if (idleDaysEnding(userId, today.minusDays(1)) == IDLE_RECALL_DAYS) {
            return reminderNotifyService.notify(userId, WearReminderTypeEnum.GREETING, today,
                    "好久不见，回来看看吧 🌿",
                    "这 3 天没看到你的动静了，牙套别忘了戴，记录也等着你补上哦。我们一直在～");
        }
        // 2. 连续未达标关怀（连续 3 天有佩戴但都未达标）
        if (underGoalDaysEnding(userId, today.minusDays(1)) == UNDER_GOAL_DAYS) {
            return reminderNotifyService.notify(userId, WearReminderTypeEnum.GREETING, today,
                    "今天再坚持久一点 🌿",
                    "连续 3 天佩戴时长差一点达标啦，今晚戴上牙套多坚持一会儿，我们一起冲目标～");
        }
        // 3. 法定节假日问候（优先级高于日常晨间问候）
        String festival = ChinaHolidayCalendar.festivalOf(today).orElse(null);
        if (festival != null) {
            return reminderNotifyService.notify(userId, WearReminderTypeEnum.GREETING, today,
                    festival + "快乐 🎉",
                    "假期愉快！别忘了抽出几小时戴上牙套，玩得开心的同时，矫正也稳稳继续～");
        }
        // 4. 每日晨间问候（周日不发，晚间交给每周小结）
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        String[] titles = {"早安，新的一天 ☀️", "早安呀 🌤️"};
        String[] bodies = {
                "戴上牙套开启元气满满的一天吧，每一次坚持都让整齐的笑容更近一步～",
                "今天也要记得按时佩戴哦，日拱一卒，改变正在悄悄发生～",
        };
        int i = pick(userId, today, titles.length);
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.GREETING, today,
                titles[i], bodies[i]);
    }

    @Override
    public boolean weeklySundaySummary(Long userId) {
        if (!allowed(userId, WearReminderTypeEnum.GREETING)) {
            return false;
        }
        LocalDate today = WearTimes.today();
        if (today.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return false;
        }
        Ctx ctx = loadActiveCtx(userId);
        if (ctx == null) {
            return false;
        }
        // 当天已有召回/关怀/节假日/晨间等 GREETING 消息时，小结让位（一天至多一条）
        if (notificationSettingService.existsSceneMessage(userId, WearReminderTypeEnum.GREETING, today)) {
            return false;
        }
        int pct = ctx.totalNodes() <= 0 ? 0 : Math.round(ctx.doneCount() * 100f / ctx.totalNodes());
        String[] bodies = {
                "「" + stageLabel(ctx.stage()) + "」已经推进 " + pct + "% 了。这周也辛苦了，周末好好歇一歇，牙套记得戴哦～",
                "这周的你依然在坚持，进度已到 " + pct + "%。给自己鼓鼓掌，矫正这条路你走得稳稳的 🌿",
        };
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.GREETING, today,
                "本周小结 🌿", bodies[pick(userId, today, bodies.length)]);
    }

    // ───────────────────────── MILESTONE 里程碑 ─────────────────────────

    @Override
    public boolean firstWeekMilestone(Long userId) {
        if (!allowed(userId, WearReminderTypeEnum.MILESTONE)) {
            return false;
        }
        Ctx ctx = loadActiveCtx(userId);
        if (ctx == null) {
            return false;
        }
        LocalDate anchor = weekAnchor(ctx.stage(), ctx.aligners());
        if (anchor == null) {
            return false;
        }
        LocalDate today = WearTimes.today();
        if (!today.equals(anchor.plusDays(6))) {
            return false;
        }
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.MILESTONE, today,
                "矫正满一周啦 🌱",
                "第一周是最需要适应的一周，你已经坚持下来啦。接下来会越来越顺，继续保持～");
    }

    @Override
    public boolean fullStreakMilestone(Long userId, LocalDate achievementDate) {
        if (!allowed(userId, WearReminderTypeEnum.MILESTONE)) {
            return false;
        }
        if (achievementDate == null) {
            return false;
        }
        int streak = fullStreakEnding(userId, achievementDate);
        for (int level : FULL_STREAK_LEVELS) {
            if (streak == level) {
                return reminderNotifyService.notify(userId, WearReminderTypeEnum.MILESTONE, achievementDate,
                        "连续达标 " + streak + " 天 🏆",
                        "已经连续 " + streak + " 天达到佩戴目标，稳扎稳打的坚持，会被牙齿记住的～");
            }
        }
        return false;
    }

    @Override
    public boolean alignerFinishedMilestone(Long userId, Long stageId, int finishedNum) {
        if (!allowed(userId, WearReminderTypeEnum.MILESTONE)) {
            return false;
        }
        Stage stage = stageId == null ? null : stageMapper.selectById(stageId);
        if (stage == null || !userId.equals(stage.getUserId()) || finishedNum <= 0) {
            return false;
        }
        int total = StageModeSupport.resolveTotalNodes(stage);
        if (total <= 0) {
            return false;
        }
        LocalDate today = WearTimes.today();
        // 最后一副：整个阶段自然完成（结束动作已由调用方在换副事务内完成）
        if (finishedNum >= total) {
            return reminderNotifyService.notify(userId, WearReminderTypeEnum.MILESTONE, today,
                    "🎉 阶段完成啦！",
                    "「" + stageLabel(stage) + "」全部戴完啦，你超棒的！记录下这一刻，回头看看会很有成就感～");
        }
        int pct = Math.round(finishedNum * 100f / total);
        // 跨过进度分档时并入进度文案（每个分档只在该段完成当下触发一次）
        StringBuilder extra = new StringBuilder();
        for (int tier : PROGRESS_TIERS) {
            int needNum = (int) Math.ceil(total * tier / 100.0);
            if (needNum > 0 && needNum == finishedNum) {
                extra.append("整体进度已达 ").append(tier).append("%！");
                break;
            }
        }
        String body = "这一程辛苦了，牙齿又向前迈了一步。已完成 " + finishedNum + "/" + total
                + " 段（进度 " + pct + "%）。" + extra;
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.MILESTONE, today,
                alignerLabel(stage, finishedNum) + "完成 🎉", body);
    }

    @Override
    public boolean stageEndedMilestone(Long userId, Long stageId) {
        if (!allowed(userId, WearReminderTypeEnum.MILESTONE)) {
            return false;
        }
        Stage stage = stageId == null ? null : stageMapper.selectById(stageId);
        if (stage == null || !userId.equals(stage.getUserId())) {
            return false;
        }
        int done = countDone(stageId);
        if (done <= 0) {
            return false;
        }
        LocalDate today = WearTimes.today();
        // 自然完成最后一副的阶段已由 alignerFinishedMilestone 归档（同类型同日去重兜底）
        if (notificationSettingService.existsSceneMessage(userId, WearReminderTypeEnum.MILESTONE, today)) {
            return false;
        }
        int total = StageModeSupport.resolveTotalNodes(stage);
        String body = "「" + stageLabel(stage) + "」走到了第 " + done + "/" + (total > 0 ? total : done)
                + " 段，每一个脚印都算数。整理好心情，下一程继续加油～";
        return reminderNotifyService.notify(userId, WearReminderTypeEnum.MILESTONE, today,
                "阶段告一段落", body);
    }

    // ───────────────────────── 内部工具 ─────────────────────────

    /** 当前启用阶段 + 其下全部牙套副（升序）；无启用阶段返回 null。 */
    private Ctx loadActiveCtx(Long userId) {
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getUserId, userId)
                .eq(Stage::getStatus, StageStatusEnum.ACTIVE.getCode())
                .last("LIMIT 1"));
        if (stage == null) {
            return null;
        }
        List<Aligner> aligners = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stage.getId())
                .orderByAsc(Aligner::getNum));
        Aligner active = null;
        int done = 0;
        for (Aligner a : aligners) {
            if (AlignerStateEnum.ACTIVE.getCode().equals(a.getState())) {
                active = a;
            } else if (AlignerStateEnum.DONE.getCode().equals(a.getState())) {
                done++;
            }
        }
        return new Ctx(stage, aligners, active, done, StageModeSupport.resolveTotalNodes(stage));
    }

    private boolean hasStage(Long userId) {
        Long count = stageMapper.selectCount(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getUserId, userId));
        return count != null && count > 0;
    }

    private int countDone(Long stageId) {
        Long count = alignerMapper.selectCount(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stageId)
                .eq(Aligner::getState, AlignerStateEnum.DONE.getCode()));
        return count == null ? 0 : count.intValue();
    }

    private String stageLabel(Stage stage) {
        return stage == null || stage.getName() == null || stage.getName().isBlank()
                ? "这个阶段" : stage.getName();
    }

    /** 单副措辞：单模「第 N 副」；双模按全局连续序号换算为「第 G 组·软/硬膜」（软奇硬偶）。 */
    private String alignerLabel(Stage stage, Integer num) {
        if (num == null) {
            return "这一程";
        }
        if (StageModeSupport.isDual(stage)) {
            int group = StageModeSupport.resolveGroupNum(stage, num);
            return "第 " + group + " 组·" + (num % 2 == 1 ? "软膜" : "硬膜");
        }
        return "第 " + num + " 副";
    }

    /** 满一周锚点：优先阶段开始日期，缺失回退最早非空副开始日期；都没有返回 null。 */
    private LocalDate weekAnchor(Stage stage, List<Aligner> aligners) {
        if (stage != null && stage.getStartDate() != null) {
            return stage.getStartDate();
        }
        if (aligners != null) {
            for (Aligner a : aligners) {
                if (a.getStartDate() != null) {
                    return a.getStartDate();
                }
            }
        }
        return null;
    }

    /** 当前副计划结束日：endDate 缺失时按 startDate+totalDays-1 推算；无法确定返回 null。 */
    private LocalDate plannedEndDate(Aligner aligner) {
        if (aligner.getEndDate() != null) {
            return aligner.getEndDate();
        }
        if (aligner.getStartDate() != null && aligner.getTotalDays() != null) {
            return aligner.getStartDate().plusDays(aligner.getTotalDays() - 1L);
        }
        return null;
    }

    private boolean allowed(Long userId, WearReminderTypeEnum type) {
        return notificationSettingService.isReminderAllowed(userId, type);
    }

    /** 某日是否有佩戴会话重叠（含当日 00:00 前开始的延续会话）。 */
    private boolean wornOn(Long userId, LocalDate date) {
        LocalDateTime start = WearTimes.startOf(date);
        LocalDateTime end = WearTimes.endOf(date);
        Long count = wearSessionMapper.selectCount(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .lt(WearSession::getStartedAt, end)
                .and(w -> w.isNull(WearSession::getEndedAt).or().ge(WearSession::getEndedAt, start)));
        return count != null && count > 0;
    }

    /** 某日是否存在时光轴记录。 */
    private boolean recordedOn(Long userId, LocalDate date) {
        Long count = timelineRecordMapper.selectCount(new LambdaQueryWrapper<TimelineRecord>()
                .eq(TimelineRecord::getUserId, userId)
                .eq(TimelineRecord::getRecordDate, date));
        return count != null && count > 0;
    }

    /** 某日佩戴结算等级；无结算行返回 null。 */
    private String levelOn(Long userId, LocalDate date) {
        WearDailySummary row = wearDailySummaryMapper.selectOne(new LambdaQueryWrapper<WearDailySummary>()
                .eq(WearDailySummary::getUserId, userId)
                .eq(WearDailySummary::getSummaryDate, date)
                .last("LIMIT 1"));
        return row == null ? null : row.getLevel();
    }

    /** 截至 {@code end}（含）连续「无佩戴且无记录」的天数。 */
    private int idleDaysEnding(Long userId, LocalDate end) {
        int count = 0;
        LocalDate d = end;
        for (int i = 0; i < 10 && d != null; i++) {
            if (wornOn(userId, d) || recordedOn(userId, d)) {
                break;
            }
            count++;
            d = d.minusDays(1);
        }
        return count;
    }

    /** 截至 {@code end}（含）连续「每天有佩戴但都未达标(FULL)」的天数。 */
    private int underGoalDaysEnding(Long userId, LocalDate end) {
        int count = 0;
        LocalDate d = end;
        for (int i = 0; i < 10 && d != null; i++) {
            if (!wornOn(userId, d)) {
                break;
            }
            if (WearLevelEnum.FULL.getCode().equals(levelOn(userId, d))) {
                break;
            }
            count++;
            d = d.minusDays(1);
        }
        return count;
    }

    /** 截至 {@code end}（含）连续 FULL 达标天数（遇非 FULL/缺失行即断）。 */
    private int fullStreakEnding(Long userId, LocalDate end) {
        int count = 0;
        LocalDate d = end;
        for (int i = 0; i < 60 && d != null; i++) {
            if (!WearLevelEnum.FULL.getCode().equals(levelOn(userId, d))) {
                break;
            }
            count++;
            d = d.minusDays(1);
        }
        return count;
    }

    /** 稳定模板选取：按 (userId, date) 确定性取模，避免同一天随机抖动。 */
    private int pick(Long userId, LocalDate date, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) (Math.floorMod((userId == null ? 0 : userId) + (long) date.toEpochDay(), size));
    }

    /** 单用户会话上下文快照。 */
    private record Ctx(Stage stage, List<Aligner> aligners, Aligner activeAligner, int doneCount, int totalNodes) {
    }
}
