package com.chiji.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.UserSubscribeQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户订阅消息额度表 Mapper。
 */
@Mapper
public interface UserSubscribeQuotaMapper extends BaseMapper<UserSubscribeQuota> {

    /**
     * 授权成功：额度 +1、累计授权 +1、刷新最近授权时间，并复活可能存在的逻辑删除行。
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
     * 消费一次额度：仅当剩余额度大于 0 时原子扣减，避免并发超发。
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
}
