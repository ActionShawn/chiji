package com.chiji.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.common.core.dto.LabelValueRow;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.User;
import com.chiji.module.admin.service.AdminStatsService;
import com.chiji.module.admin.vo.AdminStatsOverviewVO;
import com.chiji.module.admin.vo.AdminStatsTrendVO;
import com.chiji.module.admin.vo.UsageRankVO;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.track.dto.MetricTrendRow;
import com.chiji.module.track.dto.UsageRankRow;
import com.chiji.module.track.mapper.ApiMetricHourlyMapper;
import com.chiji.module.track.mapper.LoginLogMapper;
import com.chiji.module.track.mapper.UsageSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端运营看板服务实现。
 * <p>
 * 全部实时聚合（个人项目量级毫秒级）：今日随明细增长鲜活展示；
 * 区间边界统一 Asia/Shanghai 自然日 [start 00:00, end+1 00:00)。
 * 趋势粒度自适应：day=24 小时点、week/month=按日、all=按月（缺分组补 0）。
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 排行榜容量 */
    private static final int RANK_LIMIT = 10;

    /** 「全部」区间起算日（远早于业务上线，等效不设下限） */
    private static final LocalDate ALL_SINCE = LocalDate.of(2000, 1, 1);

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final UsageSessionMapper usageSessionMapper;
    private final ApiMetricHourlyMapper apiMetricHourlyMapper;
    private final TimelineRecordMapper timelineRecordMapper;
    private final RecordMediaMapper recordMediaMapper;

    @Override
    public AdminStatsOverviewVO overview(String period) {
        String effective = normalizeRange(period);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = switch (effective) {
            case "week" -> today.minusDays(6);
            case "month" -> today.minusDays(29);
            case "all" -> ALL_SINCE;
            default -> today;
        };
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();

        return AdminStatsOverviewVO.builder()
                .period(effective)
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
    public AdminStatsTrendVO trend(String range) {
        String effective = normalizeRange(range);
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();

        Map<String, Long> newUsers;
        Map<String, Long> usageSec;
        Map<String, Long> apiCalls;
        Map<String, Long> newRecords;
        Map<String, Long> newImages;
        List<String> labels;

        switch (effective) {
            case "week", "month" -> {
                LocalDate first = today.minusDays("week".equals(effective) ? 6 : 29);
                LocalDateTime startAt = first.atStartOfDay();
                newUsers = toMapByDay(userMapper.countNewUsersByDay(startAt, endAt));
                usageSec = toMapByDay(usageSessionMapper.sumDurationSecByDay(startAt, endAt));
                apiCalls = toMapByDay(apiMetricHourlyMapper.sumCntByDay(first, today));
                newRecords = toMapByDay(timelineRecordMapper.countByDay(startAt, endAt));
                newImages = toMapByDay(recordMediaMapper.countByDay(startAt, endAt));
                labels = new ArrayList<>();
                for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
                    labels.add(d.format(DAY_LABEL));
                }
            }
            case "all" -> {
                LocalDateTime startAt = ALL_SINCE.atStartOfDay();
                newUsers = toMapByLabel(userMapper.countNewUsersByMonth(startAt, endAt));
                usageSec = toMapByLabel(usageSessionMapper.sumDurationSecByMonth(startAt, endAt));
                apiCalls = toMapByLabel(toLabelRows(apiMetricTrendByMonth()));
                newRecords = toMapByLabel(timelineRecordMapper.countByMonth(startAt, endAt));
                newImages = toMapByLabel(recordMediaMapper.countByMonth(startAt, endAt));
                labels = monthLabels(earliestMonth(List.of(newUsers, usageSec, apiCalls, newRecords, newImages)),
                        YearMonth.from(today));
            }
            default -> {
                // 当天：24 小时点
                LocalDateTime startAt = today.atStartOfDay();
                newUsers = toMapByLabel(userMapper.countNewUsersByHour(startAt, endAt));
                usageSec = toMapByLabel(usageSessionMapper.sumDurationSecByHour(startAt, endAt));
                apiCalls = toMapByLabel(toLabelRows(apiMetricHourlyMapper.metricTrendByHour(today)));
                newRecords = toMapByLabel(timelineRecordMapper.countByHour(startAt, endAt));
                newImages = toMapByLabel(recordMediaMapper.countByHour(startAt, endAt));
                labels = new ArrayList<>(24);
                for (int h = 0; h < 24; h++) {
                    labels.add(String.format("%02d", h));
                }
            }
        }

        List<AdminStatsTrendVO.Point> points = new ArrayList<>(labels.size());
        for (String label : labels) {
            points.add(AdminStatsTrendVO.Point.builder()
                    .label(label)
                    .newUsers(newUsers.getOrDefault(label, 0L))
                    .usageSec(usageSec.getOrDefault(label, 0L))
                    .apiCalls(apiCalls.getOrDefault(label, 0L))
                    .newRecords(newRecords.getOrDefault(label, 0L))
                    .newImages(newImages.getOrDefault(label, 0L))
                    .build());
        }
        return AdminStatsTrendVO.builder()
                .range(effective)
                .granularity(switch (effective) {
                    case "all" -> "month";
                    case "week", "month" -> "day";
                    default -> "hour";
                })
                .points(points)
                .build();
    }

    @Override
    public List<UsageRankVO> usageRank(String period) {
        String effective = normalizeRange(period);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = switch (effective) {
            case "week" -> today.minusDays(6);
            case "month" -> today.minusDays(29);
            case "all" -> ALL_SINCE;
            default -> today;
        };

        List<UsageRankRow> rows = usageSessionMapper.rankByDuration(
                start.atStartOfDay(), today.plusDays(1).atStartOfDay(), RANK_LIMIT);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // 昵称批量查询后脱敏，避免逐条查询
        List<Long> userIds = rows.stream().map(UsageRankRow::getUserId).toList();
        Map<Long, String> nicknameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? "" : u.getNickname(), (a, b) -> a));

        List<UsageRankVO> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            UsageRankRow row = rows.get(i);
            result.add(UsageRankVO.builder()
                    .rank(i + 1)
                    .nickname(maskNickname(nicknameMap.getOrDefault(row.getUserId(), ""), row.getUserId()))
                    .totalSec(row.getTotalSec() == null ? 0L : row.getTotalSec())
                    .build());
        }
        return result;
    }

    /** 归一化区间参数（非法值按 day）。 */
    private String normalizeRange(String range) {
        return switch (range == null ? "" : range) {
            case "week", "month", "all" -> range;
            default -> "day";
        };
    }

    /** 月份序列（含首尾），用于「全部」趋势补 0。 */
    private List<String> monthLabels(YearMonth first, YearMonth last) {
        List<String> labels = new ArrayList<>();
        if (first == null) {
            return labels;
        }
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            labels.add(m.toString());
        }
        return labels;
    }

    /** 取各指标月份 key 的最早月份（全空返回 null）。 */
    private YearMonth earliestMonth(List<Map<String, Long>> maps) {
        String min = null;
        for (Map<String, Long> map : maps) {
            for (String key : map.keySet()) {
                if (min == null || key.compareTo(min) < 0) {
                    min = key;
                }
            }
        }
        return min == null ? null : YearMonth.parse(min);
    }

    /** 趋力行 → 标签值行（仅取 cnt 作调用量）。 */
    private List<LabelValueRow> toLabelRows(List<MetricTrendRow> rows) {
        List<LabelValueRow> result = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (MetricTrendRow r : rows) {
                LabelValueRow row = new LabelValueRow();
                row.setLabel(r.getLabel());
                row.setValue(r.getCnt());
                result.add(row);
            }
        }
        return result;
    }

    /** 全接口按月聚合行（仅取 cnt 作调用量）。 */
    private List<MetricTrendRow> apiMetricTrendByMonth() {
        LocalDate today = LocalDate.now(ZONE);
        return apiMetricHourlyMapper.metricTrendByMonth(ALL_SINCE, today);
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
    private Map<String, Long> toMapByDay(List<DayValueRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> map = new HashMap<>();
        for (DayValueRow r : rows) {
            if (r.getDate() != null) {
                map.put(r.getDate().format(DAY_LABEL), r.getValue() == null ? 0L : r.getValue());
            }
        }
        return map;
    }

    /** 投影行转 Map（key=标签）。 */
    private Map<String, Long> toMapByLabel(List<LabelValueRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> map = new HashMap<>();
        for (LabelValueRow r : rows) {
            if (r.getLabel() != null) {
                map.put(r.getLabel(), r.getValue() == null ? 0L : r.getValue());
            }
        }
        return map;
    }

    /**
     * 昵称脱敏：保留首字符 + **（按码点，兼容 emoji）；
     * 无昵称回退「用户 + ID 后 4 位」。
     */
    private String maskNickname(String nickname, Long userId) {
        if (nickname != null && !nickname.isBlank()) {
            int cp = nickname.codePointAt(0);
            return new String(Character.toChars(cp)) + "**";
        }
        String idStr = String.valueOf(userId);
        return "用户" + (idStr.length() > 4 ? idStr.substring(idStr.length() - 4) : idStr);
    }
}
