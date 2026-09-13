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

/**
 * 订阅额度记账实现。见 {@link SubscribeQuotaService}。
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
        // 已存在（含逻辑删除残留）行：绕过逻辑删除过滤原子 +1 并复活
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

    @Override
    public boolean consumeIfAvailable(Long userId, String scene) {
        if (userId == null || scene == null || scene.isBlank()) {
            return false;
        }
        return quotaMapper.consumeOneIfAvailable(userId, scene) > 0;
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
        return row == null || row.getRemain() == null ? 0 : row.getRemain();
    }
}
