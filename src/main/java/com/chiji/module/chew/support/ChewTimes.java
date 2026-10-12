package com.chiji.module.chew.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 咬胶时间工具（天然日 = Asia/Shanghai，无夏令时）。
 * <p>
 * 应用服务器系统时区可能与上海不一致，咬胶「今日」判定、净时长派生与日历月边界统一以此处为准，
 * 避免依赖服务器默认时区。
 */
public final class ChewTimes {

    /** 业务时区：Asia/Shanghai */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ChewTimes() {
    }

    /** 当前时间（业务时区）。 */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** 今天（业务时区）。 */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** 某日 00:00。 */
    public static LocalDateTime startOf(LocalDate date) {
        return date.atStartOfDay();
    }

    /** 某日次日 00:00（开区间端点，用于归并到该日的截止）。 */
    public static LocalDateTime endOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    /** 某月 1 号 00:00。 */
    public static LocalDateTime startOfMonth(YearMonth month) {
        return month.atDay(1).atStartOfDay();
    }

    /** 某月次月 1 号 00:00（开区间端点）。 */
    public static LocalDateTime endOfMonth(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay();
    }

    /** LocalDateTime → epoch 毫秒（按业务时区）。 */
    public static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
