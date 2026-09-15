package com.chiji.module.track.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.StatDaily;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.User;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.track.mapper.ApiMetricHourlyMapper;
import com.chiji.module.track.mapper.LoginLogMapper;
import com.chiji.module.track.mapper.StatDailyMapper;
import com.chiji.module.track.mapper.UsageSessionMapper;
import com.chiji.module.track.service.StatDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运营日汇总服务实现。
 * <p>
 * 各指标子查询相互独立 try-catch：单项失败记 0 并 warn，其余字段正常落库，
 * 重跑（先删后插幂等）自动修正。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatDailyServiceImpl implements StatDailyService {

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final UsageSessionMapper usageSessionMapper;
    private final ApiMetricHourlyMapper apiMetricHourlyMapper;
    private final TimelineRecordMapper timelineRecordMapper;
    private final RecordMediaMapper recordMediaMapper;
    private final StatDailyMapper statDailyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        StatDaily row = new StatDaily();
        row.setStatDate(date);
        row.setNewUsers((int) safeCount("new_users", () -> userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .between(User::getCreatedAt, start, end))));
        row.setLoginUsers((int) safeCount("login_users",
                () -> (long) loginLogMapper.countDistinctUsers(start, end)));
        row.setUsageSec(safeCount("usage_sec", () -> usageSessionMapper.sumDurationSec(start, end)));
        row.setUsageUsers((int) safeCount("usage_users",
                () -> (long) usageSessionMapper.countDistinctUsers(start, end)));
        row.setApiCalls(safeCount("api_calls", () -> apiMetricHourlyMapper.sumCnt(date)));
        row.setNewRecords((int) safeCount("new_records", () -> timelineRecordMapper.selectCount(
                new LambdaQueryWrapper<TimelineRecord>()
                        .between(TimelineRecord::getCreatedAt, start, end))));
        row.setNewImages((int) safeCount("new_images", () -> recordMediaMapper.selectCount(
                new LambdaQueryWrapper<RecordMedia>()
                        .between(RecordMedia::getCreatedAt, start, end))));

        // 幂等：先删后插
        statDailyMapper.delete(new LambdaQueryWrapper<StatDaily>()
                .eq(StatDaily::getStatDate, date));
        statDailyMapper.insert(row);
        log.info("运营日汇总完成, date={}, row={}", date, row);
    }

    /** 子查询包装：单项失败记 0，不影响整体。 */
    private long safeCount(String field, java.util.function.LongSupplier query) {
        try {
            Long value = query.getAsLong();
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("运营日汇总子查询失败, field={}, date 记 0 待重跑修正", field, e);
            return 0L;
        }
    }
}
