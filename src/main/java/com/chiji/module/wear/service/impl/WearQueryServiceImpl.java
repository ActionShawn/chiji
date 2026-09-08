package com.chiji.module.wear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chiji.entity.WearSession;
import com.chiji.module.wear.mapper.WearSessionMapper;
import com.chiji.module.wear.service.WearQueryService;
import com.chiji.module.wear.support.WearTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 佩戴批处理查询实现。见 {@link WearQueryService}。
 */
@Service
@RequiredArgsConstructor
public class WearQueryServiceImpl implements WearQueryService {

    private final WearSessionMapper wearSessionMapper;

    @Override
    public List<Long> listCandidatesForSettle(LocalDate date) {
        LocalDateTime dayStart = WearTimes.startOf(date);
        LocalDateTime dayEnd = WearTimes.endOf(date);
        List<Object> uids = wearSessionMapper.selectObjs(new QueryWrapper<WearSession>()
                .select("DISTINCT user_id")
                .lt("started_at", dayEnd)
                .and(w -> w.isNull("ended_at").or().ge("ended_at", dayStart)));
        List<Long> result = new ArrayList<>(uids.size());
        for (Object obj : uids) {
            if (obj instanceof Number n) {
                result.add(n.longValue());
            }
        }
        return result;
    }

    @Override
    public boolean hasOpenSession(Long userId) {
        Long count = wearSessionMapper.selectCount(new QueryWrapper<WearSession>()
                .eq("user_id", userId)
                .isNull("ended_at"));
        return count != null && count > 0;
    }
}
