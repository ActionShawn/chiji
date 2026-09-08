package com.chiji.module.wear.support;

import com.chiji.entity.WearSession;
import com.chiji.enums.WearSourceEnum;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 佩戴会话按自然日切分归并的纯计算工具（天然日 = Asia/Shanghai）。
 * <p>
 * 会话跨午夜<b>不预拆</b>，任何「某用户某自然日戴了多久」的答案都在查询到相关会话后，
 * 按 {@code [当日00:00, 截止时刻)} 计算重叠归属。截止时刻对「今天」取调用时点（佩戴中的
 * 会话只计到当下），对「过去日」取次日 00:00（该日已完整）。
 */
public final class WearDayMath {

    private WearDayMath() {
    }

    /**
     * 某自然日的归属结果。
     *
     * @param wear   该日总佩戴秒（manual + makeup 的达标基数）
     * @param manual 打卡/晨起补记（MANUAL）秒
     * @param makeup 校准补录（MAKEUP）秒
     */
    public record DaySecs(long wear, long manual, long makeup) {
    }

    /**
     * 把一批会话按日归并为 {@code from..to}（含两端，正序）逐日秒数。
     * <p>
     * {@code now} 为计算时刻：当天使用其截断当前会话，过去日使用自然日截止。
     * 对每条会话取它与每个自然日区间 {@code [dayStart, dayCap)} 的交集；
     * 交集非空则计入对应日（MANUAL/MAKEUP 分桶）。
     *
     * @param sessions 已按 userId 过滤、且可能跨越该范围的会话（含佩戴中的 endedAt=null）
     * @param from     起始自然日
     * @param to       结束自然日（应与「今天」或过去日对齐；越过今天的日期无意义）
     * @param now      当前计算时刻（Asia/Shanghai）
     * @return from..to 每个自然日的结果（正序），无交集日也占位（wear=0）
     */
    public static Map<LocalDate, DaySecs> distribute(List<WearSession> sessions, LocalDate from, LocalDate to, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        Map<LocalDate, DaySecs> result = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDateTime dayStart = WearTimes.startOf(d);
            LocalDateTime dayCap = d.equals(today) ? now : WearTimes.endOf(d);
            long wear = 0;
            long manual = 0;
            long makeup = 0;
            for (WearSession s : sessions) {
                LocalDateTime lo = s.getStartedAt().isAfter(dayStart) ? s.getStartedAt() : dayStart;
                LocalDateTime hi = s.getEndedAt() == null ? dayCap : s.getEndedAt();
                if (hi.isAfter(dayCap)) {
                    hi = dayCap;
                }
                if (!hi.isAfter(lo)) {
                    continue;
                }
                long secs = Duration.between(lo, hi).getSeconds();
                if (secs <= 0) {
                    continue;
                }
                wear += secs;
                if (WearSourceEnum.MAKEUP.getCode().equals(s.getSource())) {
                    makeup += secs;
                } else {
                    manual += secs;
                }
            }
            result.put(d, new DaySecs(wear, manual, makeup));
        }
        return result;
    }

    /**
     * 从 {@link #distribute} 结果里取某日，无则补 0 占位。
     */
    public static DaySecs dayOrZero(Map<LocalDate, DaySecs> days, LocalDate date) {
        DaySecs secs = days.get(date);
        return secs == null ? new DaySecs(0, 0, 0) : secs;
    }

    /**
     * 0.1h 精度展示秒数为「12.5h」样式（向下取整到 0.1h = 6 分钟）。
     */
    public static String tenthHoursLabel(long seconds) {
        long tenths = seconds / 360L;
        return (tenths / 10) + "." + (tenths % 10) + "h";
    }

    /**
     * 秒 → 进度百分数（0–100，可超过 100，由前端截断展示）。
     */
    public static double percent(long seconds, long goalSeconds) {
        if (goalSeconds <= 0) {
            return 0;
        }
        return seconds * 100.0 / goalSeconds;
    }

    /**
     * 会话在某个自然日区间内的可见子区间起止（用于今日时间线 VO，不修改会话本体）。
     *
     * @return 元素数组 [lo, hi]；hi 可为 null（会话佩戴中且延续过该日截止），无交集返回 null
     */
    public static LocalDateTime[] clip(WearSession s, LocalDate day, LocalDateTime dayCap) {
        LocalDateTime dayStart = WearTimes.startOf(day);
        LocalDateTime lo = s.getStartedAt().isAfter(dayStart) ? s.getStartedAt() : dayStart;
        LocalDateTime hi = s.getEndedAt() == null ? dayCap : s.getEndedAt();
        if (hi.isAfter(dayCap)) {
            hi = dayCap;
        }
        if (!hi.isAfter(lo)) {
            return null;
        }
        boolean stillWearing = s.getEndedAt() == null && hi.equals(dayCap);
        return new LocalDateTime[]{lo, stillWearing ? null : hi};
    }
}
