package com.chiji.module.wear.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 佩戴时长时间工具（天然日 = Asia/Shanghai，无夏令时）。
 * <p>
 * 应用服务器系统时区可能与上海不一致，佩戴结算与「今日」判定统一以此处为准，
 * 避免依赖服务器默认时区。
 */
public final class WearTimes {

    /** 业务时区：Asia/Shanghai */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private WearTimes() {
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

    /** LocalDateTime → epoch 毫秒（按业务时区）。 */
    public static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
