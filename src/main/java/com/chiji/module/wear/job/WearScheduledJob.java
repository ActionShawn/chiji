package com.chiji.module.wear.job;

import com.chiji.entity.WearDailySummary;
import com.chiji.enums.WearLevelEnum;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.auth.service.UserService;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.service.ProgressReminderService;
import com.chiji.module.message.service.ReminderNotifyService;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.wear.service.WearQueryService;
import com.chiji.module.wear.service.WearSettleService;
import com.chiji.module.wear.support.WearDayMath;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 佩戴定时任务（Asia/Shanghai 时区）。
 * <ul>
 *   <li>00:30 每日结算：结算昨天有佩戴会话的用户，并归档「佩戴结算结果」信箱消息</li>
 *   <li>22:30 睡前提醒：给「有当前佩戴阶段、当前未佩戴、且允许睡前提醒」的用户归档提醒</li>
 * </ul>
 * 由 {@code chiji.job.enabled}（默认 true，可经环境变量 {@code CHIJI_JOB_ENABLED} 覆盖）开关。
 * 订阅消息通道未开通，提醒以信箱消息落库；00:05 后客户端打开「佩戴」页会懒结算，与本任务幂等并存
 * （{@code settleMissing} 不覆盖已结算行；消息按 (user,type,sceneDate) 去重）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WearScheduledJob {

    @Value("${chiji.job.enabled:true}")
    private boolean enabled;

    private final WearSettleService wearSettleService;
    private final WearQueryService wearQueryService;
    private final NotificationSettingService notificationSettingService;
    private final ReminderNotifyService reminderNotifyService;
    private final ProgressReminderService progressReminderService;
    private final AlignerService alignerService;
    private final UserService userService;

    /**
     * 每日 00:30 结算昨天（Asia/Shanghai）。
     */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Shanghai")
    public void dailySettle() {
        if (!enabled) {
            return;
        }
        LocalDate yesterday = WearTimes.today().minusDays(1);
        List<Long> wearers = wearQueryService.listCandidatesForSettle(yesterday);
        int done = 0;
        int failed = 0;
        for (Long userId : wearers) {
            try {
                WearDailySummary row = wearSettleService.settleMissing(userId, yesterday);
                String label = WearDayMath.tenthHoursLabel(row == null ? 0 : row.getWearSec());
                String levelDesc = row == null || row.getLevel() == null ? ""
                        : WearLevelEnum.getByCode(row.getLevel()).getDesc();
                boolean sent = reminderNotifyService.notify(
                        userId,
                        WearReminderTypeEnum.WEAR_SETTLE,
                        yesterday,
                        "昨日佩戴结算",
                        "昨天佩戴 " + label + (levelDesc.isBlank() ? "" : "，" + levelDesc));
                // 结算 FULL 时顺带判定连续达标成就（7/14/30 天，同日去重由 notify 保证）
                boolean streakSent = row != null
                        && WearLevelEnum.FULL.getCode().equals(row.getLevel())
                        && progressReminderService.fullStreakMilestone(userId, yesterday);
                log.info("佩戴每日结算 job, userId={}, date={}, wearSec={}, level={}, notified={}, streakNotified={}",
                        userId, yesterday, row == null ? 0 : row.getWearSec(),
                        row == null ? null : row.getLevel(), sent, streakSent);
                done++;
            } catch (Exception e) {
                failed++;
                log.warn("佩戴每日结算 job 处理失败, userId={}, date={}", userId, yesterday, e);
            }
        }
        log.info("佩戴每日结算 job 结束, date={}, 成功={}, 失败={}", yesterday, done, failed);
    }

    /**
     * 每晚 22:30 睡前佩戴提醒（Asia/Shanghai）。
     */
    @Scheduled(cron = "0 30 22 * * *", zone = "Asia/Shanghai")
    public void bedtimeRemind() {
        if (!enabled) {
            return;
        }
        LocalDate sceneDate = WearTimes.today();
        List<Long> userIds = userService.listAllUserIds();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        for (Long userId : userIds) {
            try {
                // 开关门控：总开关 + 睡前提醒类型开关
                if (!notificationSettingService.isReminderAllowed(userId, WearReminderTypeEnum.WEAR_BEDTIME)) {
                    skipped++;
                    continue;
                }
                // 没有当前佩戴副（无进行中矫正/阶段未排期）不提醒；定时任务无模式上下文，任一模式有 ACTIVE 即算
                if (alignerService.findActiveAligner(userId, null) == null) {
                    skipped++;
                    continue;
                }
                // 当前正佩戴中不提醒（已在牙套上）
                if (wearQueryService.hasOpenSession(userId)) {
                    skipped++;
                    continue;
                }
                boolean ok = reminderNotifyService.notify(
                        userId,
                        WearReminderTypeEnum.WEAR_BEDTIME,
                        sceneDate,
                        "该睡啦～记得戴上牙套",
                        "睡前佩戴可以让矫正不间断，今天也辛苦了，晚安～");
                if (ok) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("睡前提醒 job 处理失败, userId={}", userId, e);
            }
        }
        log.info("睡前提醒 job 结束, sceneDate={}, 发送={}, 跳过={}, 失败={}", sceneDate, sent, skipped, failed);
    }
}
