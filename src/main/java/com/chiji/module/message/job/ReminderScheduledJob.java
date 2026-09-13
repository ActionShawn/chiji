package com.chiji.module.message.job;

import com.chiji.module.auth.service.UserService;
import com.chiji.module.message.service.NotificationSettingService;
import com.chiji.module.message.service.ProgressReminderService;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 矫正进度/问候类提醒定时任务（Asia/Shanghai 时区）。
 * <ul>
 *   <li>每小时整点：换副提醒（命中用户配置的提醒小时才判定，默认 07:00）</li>
 *   <li>09:00 晨间问候/关怀级联（召回 &gt; 连续未达标关怀 &gt; 节假日 &gt; 晨间问候）
 *       + 阶段「满一周」里程碑补发</li>
 *   <li>20:30 当日零记录催；周日 20:30 另发每周阶段小结</li>
 * </ul>
 * 由 {@code chiji.job.enabled}（默认 true，可经环境变量 {@code CHIJI_JOB_ENABLED} 覆盖）开关，
 * 与 {@code WearScheduledJob} 共用同一开关；每类消息的生成门控与去重统一在
 * {@link ProgressReminderService} 内完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduledJob {

    @Value("${chiji.job.enabled:true}")
    private boolean enabled;

    private final UserService userService;
    private final ProgressReminderService progressReminderService;
    private final NotificationSettingService notificationSettingService;

    /**
     * 每小时整点扫描换副提醒（Asia/Shanghai）。
     * <p>
     * 先按 {@link NotificationSettingService#isAlignerRemindHour(Long)} 过滤命中用户配置整点的用户，
     * 再交给 {@link ProgressReminderService#alignerChangeRemind(Long)} 做换副判定（计划结束日 ± 偏移）。
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Shanghai")
    public void alignerChangeScan() {
        if (!enabled) {
            return;
        }
        int changed = 0;
        int failed = 0;
        for (Long userId : userService.listAllUserIds()) {
            try {
                if (notificationSettingService.isAlignerRemindHour(userId)
                        && progressReminderService.alignerChangeRemind(userId)) {
                    changed++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("换副提醒 job 处理失败, userId={}", userId, e);
            }
        }
        log.info("换副提醒 job 结束, date={}, 换副={}, 失败={}", WearTimes.today(), changed, failed);
    }

    /**
     * 每天 09:00 晨间问候/关怀与「阶段满一周」里程碑（Asia/Shanghai）。
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Shanghai")
    public void morningScan() {
        if (!enabled) {
            return;
        }
        int greet = 0;
        int firstWeek = 0;
        int failed = 0;
        for (Long userId : userService.listAllUserIds()) {
            try {
                if (progressReminderService.morningGreet(userId)) {
                    greet++;
                }
                if (progressReminderService.firstWeekMilestone(userId)) {
                    firstWeek++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("晨间问候/里程碑 job 处理失败, userId={}", userId, e);
            }
        }
        log.info("晨间问候 job 结束, date={}, 问候={}, 满一周里程碑={}, 失败={}",
                WearTimes.today(), greet, firstWeek, failed);
    }

    /**
     * 每天 20:30 当日零记录催；周日顺带每周阶段小结（Asia/Shanghai）。
     */
    @Scheduled(cron = "0 30 20 * * *", zone = "Asia/Shanghai")
    public void eveningNudge() {
        if (!enabled) {
            return;
        }
        int dailyRecord = 0;
        int weekly = 0;
        int failed = 0;
        for (Long userId : userService.listAllUserIds()) {
            try {
                if (progressReminderService.dailyRecordRemind(userId)) {
                    dailyRecord++;
                }
                if (progressReminderService.weeklySundaySummary(userId)) {
                    weekly++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("晚间提醒 job 处理失败, userId={}", userId, e);
            }
        }
        log.info("晚间提醒 job 结束, date={}, 记录催={}, 周小结={}, 失败={}",
                WearTimes.today(), dailyRecord, weekly, failed);
    }
}
