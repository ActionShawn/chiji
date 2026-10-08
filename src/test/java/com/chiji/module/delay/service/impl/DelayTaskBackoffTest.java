package com.chiji.module.delay.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 重试退避计算测试：第 k 次重试顺延 base × 2^(k-1)，即 30s/60s/120s/240s/480s（base=30）。
 */
class DelayTaskBackoffTest {

    @Test
    void backoff_doubles_per_retry() {
        assertEquals(30L, DelayTaskServiceImpl.nextBackoffSeconds(30, 1));
        assertEquals(60L, DelayTaskServiceImpl.nextBackoffSeconds(30, 2));
        assertEquals(120L, DelayTaskServiceImpl.nextBackoffSeconds(30, 3));
        assertEquals(240L, DelayTaskServiceImpl.nextBackoffSeconds(30, 4));
        assertEquals(480L, DelayTaskServiceImpl.nextBackoffSeconds(30, 5));
    }

    @Test
    void backoff_custom_base() {
        assertEquals(10L, DelayTaskServiceImpl.nextBackoffSeconds(10, 1));
        assertEquals(160L, DelayTaskServiceImpl.nextBackoffSeconds(10, 5));
    }

    @Test
    void backoff_guards_non_positive_retry_no() {
        // 防御式夹取：retryNo 传 0/负数按 1 处理，不会产生反向退避
        assertEquals(30L, DelayTaskServiceImpl.nextBackoffSeconds(30, 0));
        assertEquals(30L, DelayTaskServiceImpl.nextBackoffSeconds(30, -3));
    }
}
