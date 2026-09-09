package com.chiji.module.message.service;

import java.time.LocalDate;

/**
 * 矫正进度/问候类提醒产出服务（补全通知设置页 8 类中缺失的生产者）。
 * <p>
 * 每个方法面向<b>单个用户</b>判定一次「今天该不该发、发什么」，由定时任务逐用户调用；
 * 其中「换副里程碑」等方法供阶段模块在换副/结束阶段的真实动作中<b>实时</b>回调。
 * 所有方法内部复用 {@link ReminderNotifyService} 的开关门控与 (user,type,sceneDate) 去重，
 * 未开启对应类型/总开关或同日期已存在时返回 false。
 * <ul>
 *   <li>{@link com.chiji.enums.WearReminderTypeEnum#ALIGNER_CHANGE}：当前副超计划佩戴期后每晚催换副</li>
 *   <li>{@link com.chiji.enums.WearReminderTypeEnum#DAILY_RECORD}：当天无任何时光轴记录时催记录</li>
 *   <li>{@link com.chiji.enums.WearReminderTypeEnum#GREETING}：召回/连续未达标关怀/节假日/晨间问候/每周小结（一天至多一条）</li>
 *   <li>{@link com.chiji.enums.WearReminderTypeEnum#MILESTONE}：每完成一副+进度分档、连续达标成就、阶段满一周、阶段结束</li>
 * </ul>
 */
public interface ProgressReminderService {

    /**
     * 换副超期提醒：当前副已超过计划结束日（且非最后不需要换的情形一律按「该换副」处理）。
     *
     * @param userId 用户 ID
     * @return 是否实际归档了一条消息
     */
    boolean alignerOverdueRemind(Long userId);

    /**
     * 每日记录提醒：仅当当天没有任何时光轴记录且存在启用阶段时补发。
     *
     * @param userId 用户 ID
     * @return 是否实际归档了一条消息
     */
    boolean dailyRecordRemind(Long userId);

    /**
     * 晨间问候/关怀级联（GREETING，一天至多一条）：久未使用召回 &gt; 连续未达标关怀 &gt;
     * 法定节假日问候 &gt; 每日晨间问候；周日不发晨间问候（留给晚间每周小结）。
     *
     * @param userId 用户 ID
     * @return 是否实际归档了一条消息
     */
    boolean morningGreet(Long userId);

    /**
     * 周日晚每周小结（GREETING）：当天已有其它问候消息（召回/关怀/节假日/晨间）时跳过。
     *
     * @param userId 用户 ID
     * @return 是否实际归档了一条消息
     */
    boolean weeklySundaySummary(Long userId);

    /**
     * 阶段「满一周」大事记（MILESTONE，启用阶段开始佩戴第 7 天时补发一次）。
     *
     * @param userId 用户 ID
     * @return 是否实际归档了一条消息
     */
    boolean firstWeekMilestone(Long userId);

    /**
     * 连续达标成就（MILESTONE）：从 {@code achievementDate} 起连续 FULL 达 7/14/30 天时补发一次。
     *
     * @param userId          用户 ID
     * @param achievementDate 达标归属日（通常是昨天结算日）
     * @return 是否实际归档了一条消息
     */
    boolean fullStreakMilestone(Long userId, LocalDate achievementDate);

    /**
     * 完成一副里程碑（MILESTONE，实时）：换副动作当下归档「第 N 副完成」，跨过 25/50/75/100%
     * 进度分档时并入进度文案；若为最后一副（阶段完成）改用阶段完成文案。
     *
     * @param userId      用户 ID
     * @param stageId     所属阶段 ID
     * @param finishedNum 刚完成的那副序号（即阶段内 DONE 副数）
     * @return 是否实际归档了一条消息
     */
    boolean alignerFinishedMilestone(Long userId, Long stageId, int finishedNum);

    /**
     * 阶段结束里程碑（MILESTONE，实时）：用户显式结束一个「已走完至少一副」的阶段时补发一条。
     * 自然戴完全部副的阶段走 {@link #alignerFinishedMilestone}，同一天不会重复归档。
     *
     * @param userId  用户 ID
     * @param stageId 被结束的阶段 ID
     * @return 是否实际归档了一条消息
     */
    boolean stageEndedMilestone(Long userId, Long stageId);
}
