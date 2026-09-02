package com.chiji.module.stage.support;

import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.enums.AlignerFilmEnum;
import com.chiji.enums.RecordModeEnum;

/**
 * 记录模式支撑工具：双模模式下的天数回退与序号换算。
 * <p>
 * 天数回退规则（各处换副/换回/时间调整共用）：
 * <ul>
 *   <li>优先取牙套副自身 totalDays（可能被用户手动调整过）</li>
 *   <li>缺失时按膜片类型回退阶段配置：SOFT → softDays / HARD → hardDays</li>
 *   <li>单模（filmType 为 null）回退 daysPerAligner</li>
 * </ul>
 */
public final class StageModeSupport {

    private StageModeSupport() {
    }

    /**
     * 解析牙套副的默认佩戴天数（totalDays 缺失时的回退值）。
     *
     * @param stage   所属阶段
     * @param aligner 牙套副
     * @return 默认天数；无法确定时返回 null（调用方自行兜底 ≥1）
     */
    public static Integer resolveDefaultDays(Stage stage, Aligner aligner) {
        if (stage == null) {
            return null;
        }
        String film = aligner == null ? null : aligner.getFilmType();
        if (AlignerFilmEnum.SOFT.getCode().equals(film)) {
            return stage.getSoftDays() != null ? stage.getSoftDays() : stage.getDaysPerAligner();
        }
        if (AlignerFilmEnum.HARD.getCode().equals(film)) {
            return stage.getHardDays() != null ? stage.getHardDays() : stage.getDaysPerAligner();
        }
        return stage.getDaysPerAligner();
    }

    /**
     * 阶段是否双模模式。
     */
    public static boolean isDual(Stage stage) {
        return stage != null && RecordModeEnum.isDualCode(stage.getMode());
    }

    /**
     * 节点序号换算组号：双模 num 连续递增（软奇硬偶），组号 = ceil(num / 2)；单模组号 = num。
     */
    public static int resolveGroupNum(Stage stage, int num) {
        return isDual(stage) ? (num + 1) / 2 : num;
    }

    /**
     * 阶段总节点数（进度分母）：双模 = 组数 × 2（软+硬），单模 = 副数。
     */
    public static int resolveTotalNodes(Stage stage) {
        if (stage == null || stage.getCount() == null) {
            return 0;
        }
        return isDual(stage) ? stage.getCount() * 2 : stage.getCount();
    }

    /**
     * 阶段 meta 元信息文案（StageServiceImpl 与 HomeServiceImpl 共用，保证两处一致）：
     * <ul>
     *   <li>单模：「{count}副 · 每副{days}天 · 约{count*days}天」</li>
     *   <li>双模：「{count}组 · 软{soft}天 · 硬{hard}天 · 约{count*(soft+hard)}天」</li>
     * </ul>
     */
    public static String buildStageMeta(Stage stage) {
        if (isDual(stage)) {
            int soft = stage.getSoftDays() != null ? stage.getSoftDays() : 0;
            int hard = stage.getHardDays() != null ? stage.getHardDays() : 0;
            int count = stage.getCount() != null ? stage.getCount() : 0;
            return count + "组 · 软" + soft + "天 · 硬" + hard + "天 · 约" + (count * (soft + hard)) + "天";
        }
        Integer count = stage.getCount();
        Integer days = stage.getDaysPerAligner();
        int c = count != null ? count : 0;
        int d = days != null ? days : 0;
        return c + "副 · 每副" + d + "天 · 约" + (c * d) + "天";
    }
}
