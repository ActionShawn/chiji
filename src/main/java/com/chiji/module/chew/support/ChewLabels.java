package com.chiji.module.chew.support;

import com.chiji.entity.Aligner;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 咬胶归属标签工具。
 * <p>
 * 展示「第 X 副 · 第 Y 天」：{@code X = aligner.num}；{@code Y = (会话开始自然日 − aligner.startDate) + 1}，
 * <b>按开始日派生</b>，不因后续换副漂移。归属为空（无副/无序号）时返回空串。
 */
public final class ChewLabels {

    private ChewLabels() {
    }

    /**
     * 生成归属标签。
     *
     * @param aligner     开始时刻快照的牙套副（可空）
     * @param startedDate 会话开始所在自然日（可空）
     * @return 「第X副 · 第Y天」；无副/无序号返回空串
     */
    public static String of(Aligner aligner, LocalDate startedDate) {
        if (aligner == null || aligner.getNum() == null) {
            return "";
        }
        if (aligner.getStartDate() == null || startedDate == null) {
            return "第" + aligner.getNum() + "副";
        }
        long day = ChronoUnit.DAYS.between(aligner.getStartDate(), startedDate) + 1;
        if (day < 1) {
            day = 1;
        }
        return "第" + aligner.getNum() + "副 · 第" + day + "天";
    }
}
