package com.chiji.module.rubberband.vo;

import java.time.LocalDate;

/**
 * 橡皮筋计数结果 VO（POST /api/rubber-band/increment，OPT-FB-04；第 1 批 toggle VO 废止删除）。
 * <p>
 * 以落库状态返回（upsert 累加后回读，并发冲突时返回真实最终计数）。
 *
 * @param recordDate 计数日（服务端当日，不支持补录）
 * @param count      落库后的当日累计次数（含本次 +1）
 */
public record RubberBandIncrementVO(
        LocalDate recordDate,
        Integer count
) {
}
