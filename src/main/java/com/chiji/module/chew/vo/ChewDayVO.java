package com.chiji.module.chew.vo;

import java.util.List;

/**
 * 咬胶月历视图（#9，平行数组；一律「已结束」口径，不含进行中）。
 * <p>
 * 便于小程序端渲染月历格：{@code days[i]} 对应 {@code netSec[i]} 与 {@code count[i]}；无记录日为 0。
 *
 * @param month yyyy-MM
 * @param goal  日格进度条分母（秒）= 1200
 * @param days  该月逐日 yyyy-MM-dd（正序，长度 = 当月天数）
 * @param netSec 逐日已结束净时长（秒）
 * @param count  逐日已结束次数
 */
public record ChewDayVO(
        String month,
        Integer goal,
        List<String> days,
        List<Long> netSec,
        List<Integer> count
) {
}
