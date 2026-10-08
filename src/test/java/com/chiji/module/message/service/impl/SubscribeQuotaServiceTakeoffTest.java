package com.chiji.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chiji.entity.UserSubscribeQuota;
import com.chiji.module.message.mapper.UserSubscribeQuotaMapper;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.module.wear.support.WearTimes;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订阅额度记账测试：TAKEOFF_TIMEOUT（摘下超时提醒）当日预扣模型 + ALIGNER_CHANGE（换副提醒）
 * 常驻场景行为回归（存量红线）。
 * <p>
 * 不依赖 Spring 上下文与数据库，Mapper 以 Mockito 打桩；SQL 守卫条件（防并发透支、防跨天泄漏）
 * 以注解文本断言防回退。日期口径均为 {@link WearTimes#ZONE}（Asia/Shanghai）。
 */
@ExtendWith(MockitoExtension.class)
class SubscribeQuotaServiceTakeoffTest {

    private static final Long USER_ID = 10001L;
    private static final String SCENE_TAKEOFF = SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT;
    private static final String SCENE_ALIGNER = "ALIGNER_CHANGE";

    @Mock
    private UserSubscribeQuotaMapper quotaMapper;

    private SubscribeQuotaService service() {
        return new SubscribeQuotaServiceImpl(quotaMapper);
    }

    private static UserSubscribeQuota row(String scene, Integer remain, Integer acceptedTotal, LocalDate quotaDate) {
        UserSubscribeQuota row = new UserSubscribeQuota();
        row.setUserId(USER_ID);
        row.setScene(scene);
        row.setRemain(remain);
        row.setAcceptedTotal(acceptedTotal);
        row.setQuotaDate(quotaDate);
        return row;
    }

    private static String updateSql(String methodName) {
        for (Method method : UserSubscribeQuotaMapper.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Update annotation = method.getAnnotation(Update.class);
                assertNotNull(annotation, methodName + " 应使用 @Update 注解（禁止读-判断-写）");
                return String.join(" ", annotation.value());
            }
        }
        throw new AssertionError("方法不存在：" + methodName);
    }

    @Nested
    class TakeoffAccept {

        @Test
        void same_day_accept_increments_daily_counters() {
            // 同日重复授权：单条 UPDATE 原子 +1，不走 insert、不触碰常驻场景 SQL
            when(quotaMapper.increaseTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(1);

            service().recordAccept(USER_ID, SCENE_TAKEOFF);

            verify(quotaMapper).increaseTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), eq(WearTimes.today()));
            verify(quotaMapper, never()).insert(any(UserSubscribeQuota.class));
            verify(quotaMapper, never()).increaseByUserScene(anyLong(), anyString());
        }

        @Test
        void first_accept_inserts_row_counted_into_today() {
            // 首次授权：插入行 remain=1、当日累计授权数=1、quota_date=记账日
            when(quotaMapper.increaseTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(0);

            service().recordAccept(USER_ID, SCENE_TAKEOFF);

            ArgumentCaptor<UserSubscribeQuota> captor = ArgumentCaptor.forClass(UserSubscribeQuota.class);
            verify(quotaMapper).insert(captor.capture());
            UserSubscribeQuota created = captor.getValue();
            assertEquals(USER_ID, created.getUserId());
            assertEquals(SCENE_TAKEOFF, created.getScene());
            assertEquals(1, created.getRemain());
            assertEquals(1, created.getAcceptedTotal());
            assertEquals(WearTimes.today(), created.getQuotaDate());
        }

        @Test
        void concurrent_first_accept_conflict_falls_back_to_increment() {
            // 并发首次授权撞唯一键：回退为增量 UPDATE，保证当日累计授权数不丢
            when(quotaMapper.increaseTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(0, 1);
            doThrow(new DuplicateKeyException("uk_sub_quota_user_scene"))
                    .when(quotaMapper).insert(any(UserSubscribeQuota.class));

            service().recordAccept(USER_ID, SCENE_TAKEOFF);

            verify(quotaMapper, times(2)).increaseTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class));
        }

        @Test
        void increase_sql_resets_daily_counters_atomically() {
            // 授权 SQL 必须单条完成「跨天重置 + 计数」：CASE 分支按 quota_date 归零后记 1，禁止读-判断-写
            String sql = updateSql("increaseTakeoffDaily");
            assertTrue(sql.contains("CASE WHEN quota_date = #{quotaDate} THEN remain + 1 ELSE 1 END"));
            assertTrue(sql.contains("CASE WHEN quota_date = #{quotaDate} THEN accepted_total + 1 ELSE 1 END"));
            assertTrue(sql.contains("quota_date = #{quotaDate}"));
            assertFalse(sql.toUpperCase().contains("SELECT"));
        }
    }

    @Nested
    class TakeoffConsume {

        @Test
        void consume_deducts_once_and_returns_true() {
            // 预扣成功：条件 UPDATE 扣减一次，无任何回补/加额调用
            when(quotaMapper.consumeTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(1);

            assertTrue(service().consumeIfAvailable(USER_ID, SCENE_TAKEOFF));

            verify(quotaMapper).consumeTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), eq(WearTimes.today()));
            verify(quotaMapper, never()).resetTakeoffToToday(anyLong(), anyString(), any(LocalDate.class));
            verify(quotaMapper, never()).increaseTakeoffDaily(anyLong(), anyString(), any(LocalDate.class));
            verify(quotaMapper, never()).increaseByUserScene(anyLong(), anyString());
        }

        @Test
        void consume_no_quota_returns_false_and_never_refunds() {
            // 当日额度已尽（remain=0 行）：如实返回 false，且绝无加额/回补调用（预扣模型红线）
            when(quotaMapper.consumeTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(0);

            assertFalse(service().consumeIfAvailable(USER_ID, SCENE_TAKEOFF));

            verify(quotaMapper).resetTakeoffToToday(eq(USER_ID), eq(SCENE_TAKEOFF), eq(WearTimes.today()));
            verify(quotaMapper, never()).increaseTakeoffDaily(anyLong(), anyString(), any(LocalDate.class));
            verify(quotaMapper, never()).increaseByUserScene(anyLong(), anyString());
        }

        @Test
        void consume_stale_row_lazily_resets_without_leaking_yesterday_quota() {
            // 跨天行（quota_date=昨日、昨日残留 remain>0）：扣减 SQL 因 quota_date 条件不命中，
            // 走惰性重置（remain=0、quota_date=今日）后仍返回 false，昨日额度不泄漏到当日
            when(quotaMapper.consumeTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(0);
            when(quotaMapper.resetTakeoffToToday(eq(USER_ID), eq(SCENE_TAKEOFF), any(LocalDate.class)))
                    .thenReturn(1);

            assertFalse(service().consumeIfAvailable(USER_ID, SCENE_TAKEOFF));

            verify(quotaMapper).consumeTakeoffDaily(eq(USER_ID), eq(SCENE_TAKEOFF), eq(WearTimes.today()));
            verify(quotaMapper).resetTakeoffToToday(eq(USER_ID), eq(SCENE_TAKEOFF), eq(WearTimes.today()));
            verify(quotaMapper, never()).increaseTakeoffDaily(anyLong(), anyString(), any(LocalDate.class));
        }

        @Test
        void consume_sql_keeps_guard_conditions_against_overdraft() {
            // 扣减 SQL 守卫：remain > 0（防并发透支）+ quota_date = 记账日（防跨天额度泄漏），纯 UPDATE 无 SELECT
            String sql = updateSql("consumeTakeoffDaily");
            assertTrue(sql.contains("remain > 0"));
            assertTrue(sql.contains("quota_date = #{quotaDate}"));
            assertTrue(sql.contains("deleted = 0"));
            assertFalse(sql.toUpperCase().contains("SELECT"));
        }

        @Test
        void reset_sql_targets_stale_rows_only_and_is_idempotent() {
            // 惰性重置 SQL 守卫：仅命中 NULL（存量行）/非当日行（含 NULL 显式判断），当日行天然不命中（幂等）
            String sql = updateSql("resetTakeoffToToday");
            assertTrue(sql.contains("remain = 0"));
            assertTrue(sql.contains("quota_date IS NULL OR quota_date != #{quotaDate}"));
            assertFalse(sql.toUpperCase().contains("SELECT"));
        }
    }

    @Nested
    class TakeoffQuery {

        @Test
        void remain_today_row_returns_remain() {
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 2, 3, WearTimes.today()));

            assertEquals(2, service().remain(USER_ID, SCENE_TAKEOFF));
        }

        @Test
        void remain_stale_or_null_quota_date_counts_as_zero() {
            // 跨天行与 quota_date 为 NULL 的存量行：当日口径按 0（视为需重置，不报错）
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 2, 3, WearTimes.today().minusDays(1)));
            assertEquals(0, service().remain(USER_ID, SCENE_TAKEOFF));

            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 2, 3, null));
            assertEquals(0, service().remain(USER_ID, SCENE_TAKEOFF));
        }

        @Test
        void snapshot_missing_row_or_stale_row_is_zeroed() {
            // 无记录行
            when(quotaMapper.selectOne(any(Wrapper.class))).thenReturn(null);
            SubscribeQuotaService.TakeoffDailyQuota missing = service().takeoffDailyQuota(USER_ID);
            assertEquals(0, missing.remainToday());
            assertEquals(0, missing.acceptedToday());

            // quota_date 为 NULL 的存量行
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 5, 2, null));
            SubscribeQuotaService.TakeoffDailyQuota legacy = service().takeoffDailyQuota(USER_ID);
            assertEquals(0, legacy.remainToday());
            assertEquals(0, legacy.acceptedToday());

            // 跨天行（昨日）
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 5, 2, WearTimes.today().minusDays(1)));
            SubscribeQuotaService.TakeoffDailyQuota stale = service().takeoffDailyQuota(USER_ID);
            assertEquals(0, stale.remainToday());
            assertEquals(0, stale.acceptedToday());
        }

        @Test
        void snapshot_today_row_returns_both_metrics() {
            // 当日行：剩余额度与当日累计授权数一次返回（授权 +2、已预扣 1 → remain 1 / accepted 2）
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 1, 2, WearTimes.today()));

            SubscribeQuotaService.TakeoffDailyQuota snapshot = service().takeoffDailyQuota(USER_ID);
            assertEquals(1, snapshot.remainToday());
            assertEquals(2, snapshot.acceptedToday());
        }

        @Test
        void gate_judgment_uses_accepted_today_not_remain() {
            // C1 拍板口径：授权调起判断看「当日累计授权数」——当日已授权 3 次、预扣 1 次后剩余 2，
            // 若误用剩余额度（2 < 3）会重复弹窗；正确口径 3 < 3 为 false（不再调起）
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_TAKEOFF, 2, SubscribeQuotaService.TAKEOFF_DAILY_ACCEPT_LIMIT,
                            WearTimes.today()));

            SubscribeQuotaService.TakeoffDailyQuota snapshot = service().takeoffDailyQuota(USER_ID);
            assertTrue(snapshot.remainToday() < SubscribeQuotaService.TAKEOFF_DAILY_ACCEPT_LIMIT,
                    "剩余额度口径会误判为可调起，正说明两口径不同");
            assertFalse(snapshot.acceptedToday() < SubscribeQuotaService.TAKEOFF_DAILY_ACCEPT_LIMIT,
                    "当日累计授权数达上限，不应再调起授权弹窗");
        }

        @Test
        void null_user_id_returns_empty_snapshot() {
            SubscribeQuotaService.TakeoffDailyQuota snapshot = service().takeoffDailyQuota(null);
            assertEquals(0, snapshot.remainToday());
            assertEquals(0, snapshot.acceptedToday());
            verifyNoInteractions(quotaMapper);
        }
    }

    @Nested
    class AlignerChangeRegression {

        @Test
        void accept_keeps_legacy_lifecycle_accounting() {
            // 存量红线：换副场景记账行为不变——生命周期累计路径，绝不触碰 TAKEOFF 专用 SQL
            when(quotaMapper.increaseByUserScene(USER_ID, SCENE_ALIGNER)).thenReturn(1);

            service().recordAccept(USER_ID, SCENE_ALIGNER);

            verify(quotaMapper).increaseByUserScene(USER_ID, SCENE_ALIGNER);
            verify(quotaMapper, never()).increaseTakeoffDaily(anyLong(), anyString(), any(LocalDate.class));
            verify(quotaMapper, never()).insert(any(UserSubscribeQuota.class));
        }

        @Test
        void consume_keeps_legacy_success_then_deduct() {
            // 存量红线：换副场景仍为「下发成功后扣减」，与 TAKEOFF 的「尝试即扣」分流
            when(quotaMapper.consumeOneIfAvailable(USER_ID, SCENE_ALIGNER)).thenReturn(1);

            assertTrue(service().consumeIfAvailable(USER_ID, SCENE_ALIGNER));

            verify(quotaMapper).consumeOneIfAvailable(USER_ID, SCENE_ALIGNER);
            verify(quotaMapper, never()).consumeTakeoffDaily(anyLong(), anyString(), any(LocalDate.class));
            verify(quotaMapper, never()).resetTakeoffToToday(anyLong(), anyString(), any(LocalDate.class));
        }

        @Test
        void remain_is_not_daily_adjusted() {
            // 存量红线：常驻场景 remain 不做当日折算（quota_date 恒为 NULL 也不影响返回值）
            when(quotaMapper.selectOne(any(Wrapper.class)))
                    .thenReturn(row(SCENE_ALIGNER, 5, 9, null));

            assertEquals(5, service().remain(USER_ID, SCENE_ALIGNER));
        }

        @Test
        void blank_scene_remains_no_op() {
            // 入参防御既有行为保持：空白参数不触库
            service().recordAccept(USER_ID, " ");
            service().consumeIfAvailable(USER_ID, null);
            assertEquals(0, service().remain(null, SCENE_ALIGNER));

            verifyNoInteractions(quotaMapper);
        }
    }

    @Test
    void takeoff_scene_constant_matches_delay_task_type() {
        // 与延迟队列任务类型（HandlerRegistryTest 中 TAKEOFF_TIMEOUT）保持同名，避免 BE-4 投递时口径漂移
        assertEquals("TAKEOFF_TIMEOUT", SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT);
        assertEquals(3, SubscribeQuotaService.TAKEOFF_DAILY_ACCEPT_LIMIT);
    }
}
