package com.chiji.module.track.service.impl;

import com.chiji.common.util.RedisService;
import com.chiji.entity.UsageSession;
import com.chiji.framework.metrics.TrackProperties;
import com.chiji.module.track.dto.TrackSessionRequest;
import com.chiji.module.track.mapper.UsageSessionMapper;
import com.chiji.module.track.service.UsageSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 使用会话服务实现。
 * <p>
 * Redis 按「用户+小时」限频（INCR + 首次过期），超限静默丢弃；
 * 写入走 INSERT IGNORE，client_session_id 唯一键冲突（补报重复）幂等忽略。
 */
@Service
@RequiredArgsConstructor
public class UsageSessionServiceImpl implements UsageSessionService {

    /** 业务时区（与聚合口径一致） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** enterAt 允许的最大回溯时长（7 天，超期视为脏数据丢弃） */
    private static final long MAX_AGE_MS = 7L * 24 * 3600 * 1000;

    private final UsageSessionMapper usageSessionMapper;
    private final RedisService redisService;
    private final TrackProperties trackProperties;

    @Override
    public void record(Long userId, TrackSessionRequest request) {
        if (userId == null) {
            return;
        }
        // enterAt 合理性：不早于 7 天前、不晚于当前时刻
        long now = System.currentTimeMillis();
        if (request.enterAt() > now || now - request.enterAt() > MAX_AGE_MS) {
            return;
        }
        // 单用户小时限频：超限静默丢弃（埋点接口永不报错）
        String rateKey = "track:sess:" + userId + ":"
                + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHH")
                        .withZone(ZONE)
                        .format(Instant.now());
        long count = redisService.increment(rateKey, Duration.ofHours(1));
        if (count > trackProperties.getSessionRateLimitPerHour()) {
            return;
        }
        // 幂等写入：补报重复由唯一键吸收
        UsageSession session = new UsageSession();
        session.setUserId(userId);
        session.setClientSessionId(request.clientSessionId());
        session.setEnterAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(request.enterAt()), ZONE));
        session.setDurationSec(request.durationSec());
        usageSessionMapper.insertIgnore(session);
    }
}
