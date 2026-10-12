package com.chiji.module.chew.support;

/**
 * 咬胶计时常量。
 * <p>
 * 两个独立口径（勿混用）：
 * <ul>
 *   <li>{@link #GOAL_SEC}：每日建议累计（秒）= 20 分钟，达标判定分母；</li>
 *   <li>{@link #DEFAULT_TARGET_SEC}：单次倒计时目标默认档（秒）= 10 分钟，仅作展示口径快照。</li>
 * </ul>
 */
public final class ChewConstants {

    /** 每日建议累计（秒）= 20 分钟；日格进度分母与「达标」判定阈值。 */
    public static final int GOAL_SEC = 1200;

    /** 单次目标时长默认档（秒）= 10 分钟。 */
    public static final int DEFAULT_TARGET_SEC = 600;

    /** 单次目标时长下限（秒）= 1 分钟。 */
    public static final int MIN_TARGET_SEC = 60;

    /** 单次目标时长上限（秒）= 24 小时。 */
    public static final int MAX_TARGET_SEC = 86400;

    /** 有效净时长下限（秒）：结束时会话净时长 < 此值视为误触，不落库。 */
    public static final int MIN_VALID_NET_SEC = 5;

    /**
     * active 会话净时长展示上限（秒）= 2 小时：忘结束的 active 会话在「今日累计」中按净时长实时累计，
     * 无上限可被挂机虚高至荒谬值；此处仅约束展示统计口径（today.netSec），不截断会话自身倒计时、不落库。
     */
    public static final int ACTIVE_DISPLAY_CAP_SEC = 7200;

    /** 统计口径：近 7 天（含今天）。 */
    public static final String RANGE_LAST_7 = "LAST_7";

    /** 统计口径：自然月 1 号 → 今天。 */
    public static final String RANGE_CURRENT_MONTH = "CURRENT_MONTH";

    /** 统计口径：近 30 天（含今天）。 */
    public static final String RANGE_LAST_30 = "LAST_30";

    /** 统计口径：当前自然年 1 月 1 日 → 今天。 */
    public static final String RANGE_YEAR = "YEAR";

    private ChewConstants() {
    }
}
