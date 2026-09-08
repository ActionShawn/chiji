package com.chiji.module.wear.service.impl;

import com.chiji.enums.WearLevelEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link WearSettleServiceImpl#levelOf} 达标档位判定纯逻辑单测（不依赖 Spring/DB）：
 * FULL ≥ 目标；PARTIAL ≥ 目标 − 4h；NONE &lt; 目标 − 4h。
 */
class WearSettleLevelTest {

    private static final int GOAL = WearSettleServiceImpl.DEFAULT_GOAL_SEC; // 20h = 72000s

    @Test
    void constants_match_documented_goal_and_offset() {
        assertEquals(20 * 3600, WearSettleServiceImpl.DEFAULT_GOAL_SEC, "默认目标 20h");
        assertEquals(4 * 3600, WearSettleServiceImpl.PARTIAL_OFFSET_SEC, "PARTIAL 偏移 4h");
    }

    @Test
    void full_at_goal_and_above() {
        assertEquals(WearLevelEnum.FULL, WearSettleServiceImpl.levelOf(GOAL, GOAL), "恰好达标算 FULL");
        assertEquals(WearLevelEnum.FULL, WearSettleServiceImpl.levelOf(GOAL + 1, GOAL));
        assertEquals(WearLevelEnum.FULL, WearSettleServiceImpl.levelOf(GOAL + 3600, GOAL), "超戴也算 FULL");
    }

    @Test
    void partial_between_goal_minus_4h_and_goal() {
        assertEquals(WearLevelEnum.PARTIAL, WearSettleServiceImpl.levelOf(GOAL - 1, GOAL), "差 1 秒为 PARTIAL");
        // 边界：≥ 目标 − 4h（=57600）即 PARTIAL
        assertEquals(WearLevelEnum.PARTIAL, WearSettleServiceImpl.levelOf(GOAL - WearSettleServiceImpl.PARTIAL_OFFSET_SEC, GOAL));
        assertEquals(WearLevelEnum.PARTIAL, WearSettleServiceImpl.levelOf(GOAL - WearSettleServiceImpl.PARTIAL_OFFSET_SEC + 1, GOAL));
    }

    @Test
    void none_below_goal_minus_4h() {
        assertEquals(WearLevelEnum.NONE, WearSettleServiceImpl.levelOf(GOAL - WearSettleServiceImpl.PARTIAL_OFFSET_SEC - 1, GOAL), "差 1 秒即为 NONE");
        assertEquals(WearLevelEnum.NONE, WearSettleServiceImpl.levelOf(GOAL / 2, GOAL), "10h 不达标");
        assertEquals(WearLevelEnum.NONE, WearSettleServiceImpl.levelOf(0, GOAL));
    }

    @Test
    void respects_given_goal_not_only_default() {
        int lowGoal = 18 * 3600; // 18h
        assertEquals(WearLevelEnum.FULL, WearSettleServiceImpl.levelOf(lowGoal, lowGoal), "达到该目标即 FULL");
        assertEquals(WearLevelEnum.FULL, WearSettleServiceImpl.levelOf(20 * 3600, lowGoal));
        // 戴 15h：对 18h 目标 ≥ 14h 阈值 → PARTIAL；而对默认 20h 目标 < 16h 阈值 → NONE。
        // 同一佩戴量因目标不同而档位不同，证明判定尊重传入目标而非写死默认值。
        assertEquals(WearLevelEnum.PARTIAL, WearSettleServiceImpl.levelOf(15 * 3600, lowGoal));
        assertEquals(WearLevelEnum.NONE, WearSettleServiceImpl.levelOf(15 * 3600, GOAL));
        assertEquals(WearLevelEnum.NONE, WearSettleServiceImpl.levelOf(13 * 3600, lowGoal), "低于 18h−4h=14h");
    }
}
