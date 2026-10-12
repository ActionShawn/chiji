package com.chiji.module.chew.vo;

import java.util.List;

/**
 * 咬胶某日汇总 + 逐条记录（#10，一律「已结束」口径，不含进行中）。
 *
 * @param date     自然日 yyyy-MM-dd
 * @param netSec   当日已结束净时长合计（秒）
 * @param count    当日已结束会话数
 * @param goal     每日建议累计（秒）= 1200
 * @param sessions 当日逐条记录（按开始时刻升序）
 */
public record ChewDayDetailVO(
        String date,
        Long netSec,
        Integer count,
        Integer goal,
        List<ChewRecordVO> sessions
) {
}
