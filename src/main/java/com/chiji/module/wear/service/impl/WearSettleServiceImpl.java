package com.chiji.module.wear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.WearDailySummary;
import com.chiji.entity.WearSession;
import com.chiji.entity.UserSetting;
import com.chiji.enums.WearLevelEnum;
import com.chiji.module.wear.mapper.WearDailySummaryMapper;
import com.chiji.module.wear.mapper.WearSessionMapper;
import com.chiji.module.wear.mapper.WearUserSettingMapper;
import com.chiji.module.wear.service.WearSettleService;
import com.chiji.module.wear.support.WearDayMath;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 佩戴每日结算实现。见 {@link WearSettleService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WearSettleServiceImpl implements WearSettleService {

    /** 懒结算最早时刻：次日 00:05（此前昨日可能仍在续戴中） */
    private static final LocalTime LAZY_EARLIEST = LocalTime.of(0, 5);

    private final WearSessionMapper wearSessionMapper;
    private final WearDailySummaryMapper wearDailySummaryMapper;
    private final WearUserSettingMapper wearUserSettingMapper;

    @Override
    public WearDailySummary settleDay(Long userId, LocalDate date) {
        return settle(userId, date, true);
    }

    @Override
    public WearDailySummary settleMissing(Long userId, LocalDate date) {
        return settle(userId, date, false);
    }

    @Override
    public void ensureYesterdaySettled(Long userId) {
        LocalDateTime now = WearTimes.now();
        if (now.toLocalTime().isBefore(LAZY_EARLIEST)) {
            return;
        }
        settleMissing(userId, now.toLocalDate().minusDays(1));
    }

    /**
     * @param force true=重算覆盖；false=仅缺失时结算
     */
    private WearDailySummary settle(Long userId, LocalDate date, boolean force) {
        WearDailySummary existing = selectByUserDate(userId, date);
        if (existing != null && !force) {
            return existing;
        }

        LocalDateTime now = WearTimes.now();
        LocalDate today = now.toLocalDate();
        // 边界：只允许结算「今天之前」的完整自然日；未来日期直接拒绝，避免脏数据
        if (!date.isBefore(today)) {
            log.warn("拒绝结算非完整自然日, userId={}, date={}", userId, date);
            return existing;
        }

        int goalSec = loadGoalSec(userId);
        Map<LocalDate, WearDayMath.DaySecs> days = computeDay(userId, date, now);
        WearDayMath.DaySecs day = days.get(date);
        int wear = (int) Math.min(Integer.MAX_VALUE, day == null ? 0 : day.wear());
        int manual = (int) Math.min(Integer.MAX_VALUE, day == null ? 0 : day.manual());
        int makeup = wear - manual;
        String level = levelOf(wear, goalSec).getCode();

        if (existing == null) {
            WearDailySummary row = new WearDailySummary();
            row.setUserId(userId);
            row.setSummaryDate(date);
            row.setWearSec(wear);
            row.setManualSec(manual);
            row.setMakeupSec(Math.max(0, makeup));
            row.setGoalSec(goalSec);
            row.setLevel(level);
            row.setSettledAt(now);
            try {
                wearDailySummaryMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发双结算兜底：回读已有行并按其覆盖（force 语义下取最新一次）
                WearDailySummary raced = selectByUserDate(userId, date);
                if (raced != null) {
                    copyInto(raced, wear, manual, Math.max(0, makeup), goalSec, level, now);
                    wearDailySummaryMapper.updateById(raced);
                    return raced;
                }
                throw e;
            }
            log.info("佩戴结算(新), userId={}, date={}, wearSec={}, level={}", userId, date, wear, level);
            return row;
        }

        copyInto(existing, wear, manual, Math.max(0, makeup), goalSec, level, now);
        wearDailySummaryMapper.updateById(existing);
        log.info("佩戴结算(重算), userId={}, date={}, wearSec={}, level={}", userId, date, wear, level);
        return existing;
    }

    private void copyInto(WearDailySummary row, int wear, int manual, int makeup, int goalSec, String level, LocalDateTime now) {
        row.setWearSec(wear);
        row.setManualSec(manual);
        row.setMakeupSec(makeup);
        row.setGoalSec(goalSec);
        row.setLevel(level);
        row.setSettledAt(now);
    }

    private WearDailySummary selectByUserDate(Long userId, LocalDate date) {
        return wearDailySummaryMapper.selectOne(new LambdaQueryWrapper<WearDailySummary>()
                .eq(WearDailySummary::getUserId, userId)
                .eq(WearDailySummary::getSummaryDate, date)
                .last("LIMIT 1"));
    }

    private Map<LocalDate, WearDayMath.DaySecs> computeDay(Long userId, LocalDate date, LocalDateTime now) {
        // 该日相关的会话：开始于当日截止前，且未在当日开始前结束（含跨昨日延续）
        LocalDateTime dayStart = WearTimes.startOf(date);
        LocalDateTime dayEnd = WearTimes.endOf(date);
        List<WearSession> sessions = wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .eq(WearSession::getUserId, userId)
                .lt(WearSession::getStartedAt, dayEnd)
                .and(w -> w.isNull(WearSession::getEndedAt).or().ge(WearSession::getEndedAt, dayStart)));
        return WearDayMath.distribute(sessions, date, date, now);
    }

    private int loadGoalSec(Long userId) {
        UserSetting setting = wearUserSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .last("LIMIT 1"));
        Integer goal = setting == null ? null : setting.getGoalSec();
        return goal == null || goal <= 0 ? DEFAULT_GOAL_SEC : goal;
    }

    /**
     * 判定某佩戴量对应的达标档位。
     */
    public static WearLevelEnum levelOf(int wearSec, int goalSec) {
        if (wearSec >= goalSec) {
            return WearLevelEnum.FULL;
        }
        if (wearSec >= goalSec - PARTIAL_OFFSET_SEC) {
            return WearLevelEnum.PARTIAL;
        }
        return WearLevelEnum.NONE;
    }

    /** 默认目标 20h（秒）。 */
    public static final int DEFAULT_GOAL_SEC = 20 * 3600;
    /** PARTIAL 阈值偏移 4h（≥ goal−4h 视为部分达标）。 */
    public static final int PARTIAL_OFFSET_SEC = 4 * 3600;
}
