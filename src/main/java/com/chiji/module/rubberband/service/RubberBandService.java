package com.chiji.module.rubberband.service;

import com.chiji.module.rubberband.vo.RubberBandIncrementVO;
import com.chiji.module.rubberband.vo.RubberBandRecentVO;

/**
 * 橡皮筋每日计数服务（固定托槽第 1 批 REQ-FB-006 勾选打卡 → 第 2 批 OPT-FB-04 次数计数）。
 * <p>
 * 形态：纯手动、每日多次「+1」、仅当日、零提醒零订阅消息；
 * 门控由 {@link com.chiji.module.stage.support.TreatmentGateSupport} 唯一事实源承担。
 */
public interface RubberBandService {

    /**
     * 查询今日计数 + 近 7 天点阵 + 连续天数/本周次数（一条范围 SQL + 两条聚合 SQL，零新增请求红线的数据源）。
     *
     * @param userId 当前用户 ID
     * @return 今日次数 + 近 7 天逐日次数 + streakDays/weekCount 聚合
     */
    RubberBandRecentVO recent(Long userId);

    /**
     * 当日计数 +1（仅当日；upsert 原子累加，以落库计数返回；单日上限校验）。
     *
     * @param userId 当前用户 ID
     * @return 落库后的当日累计次数
     */
    RubberBandIncrementVO increment(Long userId);
}
