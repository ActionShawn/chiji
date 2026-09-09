package com.chiji.module.message.support;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中国法定节假日/调休放假日历（内置静态表，供「节假日问候」使用）。
 * <p>
 * 数据来源：国务院办公厅《关于部分节假日安排的通知》（中国政府网发布）。
 * 只收录「放假休息」的日期（含调休拼出的假期），调休补班的周末不在表内。
 * <b>每年需人工更新一次</b>（新一年安排通常于上一年 11 月左右公布），未收录年份返回空。
 * 当前内置：
 * <ul>
 *   <li>2025：元旦 1/1；春节 1/28–2/4；清明 4/4–4/6；劳动节 5/1–5/5；端午 5/31–6/2；中秋国庆 10/1–10/8</li>
 *   <li>2026：元旦 1/1–1/3；春节 2/15–2/23；清明 4/4–4/6；劳动节 5/1–5/5；端午 6/19–6/21；中秋 9/25–9/27；国庆 10/1–10/7</li>
 * </ul>
 */
public final class ChinaHolidayCalendar {

    private ChinaHolidayCalendar() {
    }

    /** 年份 → （节日名 → 放假日列表（含首尾）） */
    private static final Map<Integer, Map<String, List<LocalDate>>> HOLIDAYS = new LinkedHashMap<>();

    static {
        HOLIDAYS.put(2025, Map.of(
                "元旦", List.of(LocalDate.of(2025, 1, 1)),
                "春节", range(2025, 1, 28, 2, 4),
                "清明", range(2025, 4, 4, 4, 6),
                "劳动节", range(2025, 5, 1, 5, 5),
                "端午", range(2025, 5, 31, 6, 2),
                "中秋国庆", range(2025, 10, 1, 10, 8)));
        HOLIDAYS.put(2026, Map.of(
                "元旦", range(2026, 1, 1, 1, 3),
                "春节", range(2026, 2, 15, 2, 23),
                "清明", range(2026, 4, 4, 4, 6),
                "劳动节", range(2026, 5, 1, 5, 5),
                "端午", range(2026, 6, 19, 6, 21),
                "中秋", range(2026, 9, 25, 9, 27),
                "国庆", range(2026, 10, 1, 10, 7)));
    }

    /**
     * 某日是否为放假休息日；若是返回节日名（如「元旦」「春节」），否则返回 {@link Optional#empty()}。
     *
     * @param date 待判定日期
     */
    public static Optional<String> festivalOf(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        Map<String, List<LocalDate>> yearHolidays = HOLIDAYS.get(date.getYear());
        if (yearHolidays == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<LocalDate>> entry : yearHolidays.entrySet()) {
            if (entry.getValue().contains(date)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static List<LocalDate> range(int year, int startMonth, int startDay, int endMonth, int endDay) {
        LocalDate start = LocalDate.of(year, startMonth, startDay);
        LocalDate end = LocalDate.of(year, endMonth, endDay);
        return start.datesUntil(end.plusDays(1)).toList();
    }
}
