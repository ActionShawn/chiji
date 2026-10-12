package com.chiji.module.chew.service;

import com.chiji.module.chew.vo.ChewDayDetailVO;
import com.chiji.module.chew.vo.ChewDayVO;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.ChewStatsVO;
import com.chiji.module.chew.vo.TodayChewVO;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 咬胶只读查询服务（#6–#10）。
 * <p>
 * 聚合/日历/统计口径一律「已结束」（{@code ended_at IS NOT NULL AND deleted = 0}）；
 * {@code today} 额外派生进行中会话的实时净时长用于「今日累计」展示。时区 Asia/Shanghai。
 */
public interface ChewQueryService {

    /**
     * 当前进行中会话（#6）。
     *
     * @param userId 用户 ID
     * @return 进行中会话视图；无则 null
     */
    ChewSessionVO active(Long userId);

    /**
     * 今日累计（#7，含进行中实时净时长）。
     *
     * @param userId 用户 ID
     * @return 今日累计视图
     */
    TodayChewVO today(Long userId);

    /**
     * 某月每日汇总（#9，平行数组，全月逐日 0 填充）。
     *
     * @param userId 用户 ID
     * @param month  月份
     * @return 月历视图
     */
    ChewDayVO calendar(Long userId, YearMonth month);

    /**
     * 某日汇总 + 逐条记录（#10）。
     *
     * @param userId 用户 ID
     * @param date   自然日
     * @return 当日明细视图
     */
    ChewDayDetailVO day(Long userId, LocalDate date);

    /**
     * 区间统计（#8）。
     *
     * @param userId 用户 ID
     * @param range  LAST_7 / CURRENT_MONTH / LAST_30 / YEAR（空回退 LAST_7，非法抛 {@code CHEW_PARAM_INVALID}）
     * @return 统计视图
     */
    ChewStatsVO stats(Long userId, String range);
}
