package com.chiji.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserSubscribeQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 用户订阅消息额度表 Mapper。
 * <p>
 * 记账 SQL 按场景分流：{@link #increaseByUserScene} / {@link #consumeOneIfAvailable} 为常驻场景
 * （ALIGNER_CHANGE / CLINIC_*）的生命周期累计模型，行为保持不变；
 * {@code *TakeoffDaily} 三方法为 TAKEOFF_TIMEOUT（摘下超时提醒）的当日预扣模型专用。
 */
@Mapper
public interface UserSubscribeQuotaMapper extends BaseMapper<UserSubscribeQuota> {

    /**
     * 【常驻场景】授权成功：额度 +1、累计授权 +1、刷新最近授权时间，并复活可能存在的逻辑删除行。
     * <p>
     * 该 SQL 不经过 MyBatis-Plus 逻辑删除过滤：唯一键 {@code uk_sub_quota_user_scene} 不含
     * {@code deleted}，逻辑删除行仍物理占位，直接 insert 会撞唯一键，故统一走本方法更新。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景
     * @return 影响行数：>0 命中并更新（含复活）；0 物理行不存在，调用方应改走 insert
     */
    @Update("UPDATE user_subscribe_quota "
            + "SET remain = remain + 1, accepted_total = accepted_total + 1, "
            + "last_accept_at = NOW(), deleted = 0, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND scene = #{scene}")
    int increaseByUserScene(@Param("userId") Long userId, @Param("scene") String scene);

    /**
     * 【常驻场景】消费一次额度：仅当剩余额度大于 0 时原子扣减，避免并发超发。
     * <p>
     * 仅在微信下发成功（{@code errcode == 0}）后调用。
     *
     * @param userId 用户 ID
     * @param scene  订阅场景
     * @return 影响行数：1 扣减成功；0 表示无可用额度（或行已逻辑删除）
     */
    @Update("UPDATE user_subscribe_quota "
            + "SET remain = remain - 1, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND scene = #{scene} AND remain > 0 AND deleted = 0")
    int consumeOneIfAvailable(@Param("userId") Long userId, @Param("scene") String scene);

    /**
     * 【TAKEOFF_TIMEOUT 专用】授权 +1 记入当日（含惰性跨天重置），单条原子 UPDATE。
     * <p>
     * {@code quota_date} 已是记账日：{@code remain} 与 {@code accepted_total}（当日累计授权数）各 +1；
     * {@code quota_date} 为 NULL（存量行）或非当日（跨天）：视为需重置，直接落为
     * {@code remain = 1, accepted_total = 1, quota_date = 记账日}——昨日残留额度与授权计数同条 SQL 原子清零，
     * 不经过「读-判断-写」。命中（含复活）逻辑删除行，与 {@link #increaseByUserScene} 同理由。
     *
     * @param userId    用户 ID
     * @param scene     订阅场景（应为 {@code SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT}）
     * @param quotaDate 记账日（Asia/Shanghai，由调用方以应用时钟传入，保证与查询折算口径一致）
     * @return 影响行数：>0 记账成功；0 物理行不存在，调用方应改走 insert
     */
    @Update("UPDATE user_subscribe_quota "
            + "SET remain = CASE WHEN quota_date = #{quotaDate} THEN remain + 1 ELSE 1 END, "
            + "accepted_total = CASE WHEN quota_date = #{quotaDate} THEN accepted_total + 1 ELSE 1 END, "
            + "quota_date = #{quotaDate}, last_accept_at = NOW(), deleted = 0, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND scene = #{scene}")
    int increaseTakeoffDaily(@Param("userId") Long userId, @Param("scene") String scene,
                             @Param("quotaDate") LocalDate quotaDate);

    /**
     * 【TAKEOFF_TIMEOUT 专用】当日条件预扣（单条条件 UPDATE，防并发透支）。
     * <p>
     * 下发<b>尝试即扣</b>（预扣模型），下发失败<b>不回补</b>——本 Mapper 不存在任何回补方法。
     * 仅「记账日为当日且 remain > 0」的行可扣：昨日残留行 / NULL 存量行因
     * {@code quota_date = #{quotaDate}} 不命中而天然返回 0，昨日额度不会泄漏到当日。
     * <p>
     * 不与 {@link #resetTakeoffToToday} 合并为单条 SQL 的原因：合并后「已扣减」与「仅重置」
     * 的影响行数同为 1，调用方无法判定预扣结果（无额度必须如实返回 false，见 PRD「无额度不下发」）。
     *
     * @param userId    用户 ID
     * @param scene     订阅场景
     * @param quotaDate 记账日（Asia/Shanghai）
     * @return 影响行数：1 预扣成功（本次下发可尝试）；0 当日无可用额度
     */
    @Update("UPDATE user_subscribe_quota "
            + "SET remain = remain - 1, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND scene = #{scene} "
            + "AND quota_date = #{quotaDate} AND remain > 0 AND deleted = 0")
    int consumeTakeoffDaily(@Param("userId") Long userId, @Param("scene") String scene,
                            @Param("quotaDate") LocalDate quotaDate);

    /**
     * 【TAKEOFF_TIMEOUT 专用】惰性跨天重置：{@code quota_date} 为 NULL（存量行）或非当日 →
     * 原子置 {@code remain = 0, quota_date = 当日}，昨日残留额度就此清零。
     * <p>
     * 幂等：已是当日的行不命中（影响 0 行），可无条件调用。
     *
     * @param userId    用户 ID
     * @param scene     订阅场景
     * @param quotaDate 记账日（Asia/Shanghai）
     * @return 影响行数：1 执行了重置；0 行本就是当日（无需重置）或行不存在
     */
    @Update("UPDATE user_subscribe_quota "
            + "SET remain = 0, quota_date = #{quotaDate}, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND scene = #{scene} "
            + "AND deleted = 0 AND (quota_date IS NULL OR quota_date != #{quotaDate})")
    int resetTakeoffToToday(@Param("userId") Long userId, @Param("scene") String scene,
                            @Param("quotaDate") LocalDate quotaDate);
}
