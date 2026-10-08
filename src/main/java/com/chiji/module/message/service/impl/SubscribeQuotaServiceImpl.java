package com.chiji.module.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.UserSubscribeQuota;
import com.chiji.module.message.mapper.UserSubscribeQuotaMapper;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 订阅额度记账实现。见 {@link SubscribeQuotaService}。
 * <p>
 * 按 {@code scene} 分流：{@link #SCENE_TAKEOFF_TIMEOUT} 走当日预扣模型，其余场景走既有生命周期累计模型
 * （ALIGNER_CHANGE 等常驻场景记账行为保持不变，存量红线）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeQuotaServiceImpl implements SubscribeQuotaService {

    private final UserSubscribeQuotaMapper quotaMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordAccept(Long userId, String scene) {
        if (userId == null || scene == null || scene.isBlank()) {
            return;
        }
        if (SCENE_TAKEOFF_TIMEOUT.equals(scene)) {
            recordTakeoffAccept(userId);
            return;
        }
        // 常驻场景（既有生命周期累计模型）：已存在（含逻辑删除残留）行原子 +1 并复活
        if (quotaMapper.increaseByUserScene(userId, scene) > 0) {
            return;
        }
        UserSubscribeQuota created = new UserSubscribeQuota();
        created.setUserId(userId);
        created.setScene(scene);
        created.setRemain(1);
        created.setAcceptedTotal(1);
        created.setLastAcceptAt(WearTimes.now());
        try {
            quotaMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发首次授权，另一线程已插入，改为增量更新
            quotaMapper.increaseByUserScene(userId, scene);
        }
    }

    /**
     * TAKEOFF_TIMEOUT 授权记账：+1 记入当日（含惰性跨天重置），单条条件 UPDATE 完成，
     * 不经过「读-判断-写」。
     */
    private void recordTakeoffAccept(Long userId) {
        LocalDate today = WearTimes.today();
        if (quotaMapper.increaseTakeoffDaily(userId, SCENE_TAKEOFF_TIMEOUT, today) > 0) {
            return;
        }
        UserSubscribeQuota created = new UserSubscribeQuota();
        created.setUserId(userId);
        created.setScene(SCENE_TAKEOFF_TIMEOUT);
        created.setRemain(1);
        created.setAcceptedTotal(1);
        created.setQuotaDate(today);
        created.setLastAcceptAt(WearTimes.now());
        try {
            quotaMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发首次授权，另一线程已插入，改为增量更新
            quotaMapper.increaseTakeoffDaily(userId, SCENE_TAKEOFF_TIMEOUT, today);
        }
    }

    @Override
    public boolean consumeIfAvailable(Long userId, String scene) {
        if (userId == null || scene == null || scene.isBlank()) {
            return false;
        }
        if (SCENE_TAKEOFF_TIMEOUT.equals(scene)) {
            return consumeTakeoff(userId);
        }
        // 常驻场景（既有模型）：仅在微信下发成功后调用，失败不扣
        return quotaMapper.consumeOneIfAvailable(userId, scene) > 0;
    }

    /**
     * TAKEOFF_TIMEOUT 当日预扣：下发尝试即扣、失败不回补（不存在回补调用）。
     * <p>
     * 两步原子 UPDATE、全程无「读-判断-写」：先按当日条件扣减；未命中时可能为跨天 / 存量 NULL 行，
     * 随手执行惰性重置（原子置 {@code remain = 0, quota_date = 当日}）——重置后当日本就无额度，
     * 故无论重置是否发生均如实返回 false。
     */
    private boolean consumeTakeoff(Long userId) {
        LocalDate today = WearTimes.today();
        if (quotaMapper.consumeTakeoffDaily(userId, SCENE_TAKEOFF_TIMEOUT, today) > 0) {
            return true;
        }
        quotaMapper.resetTakeoffToToday(userId, SCENE_TAKEOFF_TIMEOUT, today);
        return false;
    }

    @Override
    public int remain(Long userId, String scene) {
        if (userId == null || scene == null || scene.isBlank()) {
            return 0;
        }
        UserSubscribeQuota row = quotaMapper.selectOne(new LambdaQueryWrapper<UserSubscribeQuota>()
                .eq(UserSubscribeQuota::getUserId, userId)
                .eq(UserSubscribeQuota::getScene, scene)
                .last("LIMIT 1"));
        if (row == null || row.getRemain() == null) {
            return 0;
        }
        if (SCENE_TAKEOFF_TIMEOUT.equals(scene)) {
            // 当日口径折算：quota_date 为 NULL（存量行）或非当日 → 需重置，当日剩余为 0
            return WearTimes.today().equals(row.getQuotaDate()) ? row.getRemain() : 0;
        }
        return row.getRemain();
    }

    @Override
    public TakeoffDailyQuota takeoffDailyQuota(Long userId) {
        if (userId == null) {
            return new TakeoffDailyQuota(0, 0);
        }
        UserSubscribeQuota row = quotaMapper.selectOne(new LambdaQueryWrapper<UserSubscribeQuota>()
                .eq(UserSubscribeQuota::getUserId, userId)
                .eq(UserSubscribeQuota::getScene, SCENE_TAKEOFF_TIMEOUT)
                .last("LIMIT 1"));
        if (row == null) {
            return new TakeoffDailyQuota(0, 0);
        }
        // 只读折算：quota_date 为 NULL（存量行）或非当日 → 需重置，两口径均按 0
        if (row.getQuotaDate() == null || !WearTimes.today().equals(row.getQuotaDate())) {
            return new TakeoffDailyQuota(0, 0);
        }
        return new TakeoffDailyQuota(
                row.getRemain() == null ? 0 : row.getRemain(),
                row.getAcceptedTotal() == null ? 0 : row.getAcceptedTotal());
    }
}
