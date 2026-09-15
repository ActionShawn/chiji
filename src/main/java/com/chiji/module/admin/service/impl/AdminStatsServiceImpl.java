package com.chiji.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.User;
import com.chiji.module.admin.service.AdminStatsService;
import com.chiji.module.admin.vo.AdminStatsOverviewVO;
import com.chiji.module.admin.vo.AdminStatsTrendVO;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.track.mapper.ApiMetricHourlyMapper;
import com.chiji.module.track.mapper.LoginLogMapper;
import com.chiji.module.track.mapper.UsageSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端运营看板服务实现。
 * <p>
 * 全部实时聚合（个人项目量级毫秒级）：今日随明细增长鲜活展示；
 * 区间边界统一 Asia/Shanghai 自然日 [start 00:00, end+1 00:00)。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 趋势天数（含今日） */
    private static final int TREND_DAYS = 7;

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final UsageSessionMapper usageSessionMapper;
    private final ApiMetricHourlyMapper apiMetricHourlyMapper;
    private final TimelineRecordMapper timelineRecordMapper;
    private final RecordMediaMapper recordMediaMapper;

    @Override
    public AdminStatsOverviewVO overview(String period) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = switch (period == null ? "day" : period) {
            case "week" -> today.minusDays(6);
            case "month" -> today.minusDays(29);
            default -> today;
        };
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();

        return AdminStatsOverviewVO.builder()
                .period(period == null ? "day" : period)
                .newUsers(countNewUsers(startAt, endAt))
                .loginUsers(loginLogMapper.countDistinctUsers(startAt, endAt))
                .usageSec(usageSessionMapper.sumDurationSec(startAt, endAt))
                .usageUsers(usageSessionMapper.countDistinctUsers(startAt, endAt))
                .apiCalls(apiMetricHourlyMapper.sumCntRange(start, today))
                .newRecords(countNewRecords(startAt, endAt))
                .newImages(countNewImages(startAt, endAt))
                .build();
    }

    @Override
    public AdminStatsTrendVO trend() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate first = today.minusDays(TREND_DAYS - 1);
        LocalDateTime startAt = first.atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();

        // 各指标按日序列（缺日由 buildPoints 补 0）
        Map<LocalDate, Long> newUsers = toMap(userMapper.countNewUsersByDay(startAt, endAt));
        Map<LocalDate, Long> loginUsers = toMap(loginLogMapper.countDistinctUsersByDay(startAt, endAt));
        Map<LocalDate, Long> usageSec = toMap(usageSessionMapper.sumDurationSecByDay(startAt, endAt));
        Map<LocalDate, Long> usageUsers = toMap(usageSessionMapper.countDistinctUsersByDay(startAt, endAt));
        Map<LocalDate, Long> apiCalls = toMap(apiMetricHourlyMapper.sumCntByDay(first, today));
        Map<LocalDate, Long> newRecords = toMap(timelineRecordMapper.countByDay(startAt, endAt));
        Map<LocalDate, Long> newImages = toMap(recordMediaMapper.countByDay(startAt, endAt));

        List<AdminStatsTrendVO.Point> points = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate d = first.plusDays(i);
            points.add(AdminStatsTrendVO.Point.builder()
                    .date(d)
                    .newUsers(newUsers.getOrDefault(d, 0L))
                    .loginUsers(loginUsers.getOrDefault(d, 0L))
                    .usageSec(usageSec.getOrDefault(d, 0L))
                    .usageUsers(usageUsers.getOrDefault(d, 0L))
                    .apiCalls(apiCalls.getOrDefault(d, 0L))
                    .newRecords(newRecords.getOrDefault(d, 0L))
                    .newImages(newImages.getOrDefault(d, 0L))
                    .build());
        }
        return AdminStatsTrendVO.builder().points(points).build();
    }

    /** 新增用户数（MP 查询自动排除逻辑删除）。 */
    private long countNewUsers(LocalDateTime start, LocalDateTime end) {
        Long cnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .between(User::getCreatedAt, start, end));
        return cnt == null ? 0L : cnt;
    }

    private long countNewRecords(LocalDateTime start, LocalDateTime end) {
        Long cnt = timelineRecordMapper.selectCount(new LambdaQueryWrapper<TimelineRecord>()
                .between(TimelineRecord::getCreatedAt, start, end));
        return cnt == null ? 0L : cnt;
    }

    private long countNewImages(LocalDateTime start, LocalDateTime end) {
        Long cnt = recordMediaMapper.selectCount(new LambdaQueryWrapper<RecordMedia>()
                .between(RecordMedia::getCreatedAt, start, end));
        return cnt == null ? 0L : cnt;
    }

    /** 投影行转 Map（key=日期）。 */
    private Map<LocalDate, Long> toMap(List<DayValueRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .filter(r -> r.getDate() != null)
                .collect(Collectors.toMap(DayValueRow::getDate,
                        r -> r.getValue() == null ? 0L : r.getValue(), (a, b) -> a));
    }
}
