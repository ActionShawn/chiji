package com.chiji.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.page.CursorPage;
import com.chiji.entity.SlowApiLog;
import com.chiji.module.admin.service.AdminMetricsService;
import com.chiji.module.admin.vo.ApiHourlyVO;
import com.chiji.module.admin.vo.ApiStatsVO;
import com.chiji.module.admin.vo.ApiTrendVO;
import com.chiji.module.admin.vo.SlowApiLogVO;
import com.chiji.module.track.dto.ApiHourlyRow;
import com.chiji.module.track.dto.ApiStatsRow;
import com.chiji.module.track.dto.MetricTrendRow;
import com.chiji.module.track.mapper.ApiMetricHourlyMapper;
import com.chiji.module.track.mapper.SlowApiLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端接口监控服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsServiceImpl implements AdminMetricsService {

    /** 业务时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 天数上限 */
    private static final int DAYS_MAX = 30;

    /** 每页条数上限 */
    private static final int LIMIT_MAX = 50;

    /** 「全部」区间起算日（远早于业务上线，等效不设下限） */
    private static final LocalDate ALL_SINCE = LocalDate.of(2000, 1, 1);

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final ApiMetricHourlyMapper apiMetricHourlyMapper;
    private final SlowApiLogMapper slowApiLogMapper;

    @Override
    public List<ApiStatsVO> apiList(String range, int days) {
        LocalDate today = LocalDate.now(ZONE);
        // range=all 时等效不设下限，否则按天数（1-30）收敛
        LocalDate start = "all".equalsIgnoreCase(range)
                ? ALL_SINCE
                : today.minusDays(Math.min(Math.max(days, 1), DAYS_MAX) - 1L);

        List<ApiStatsRow> rows = apiMetricHourlyMapper.aggregateByApi(start, today);
        return rows.stream().map(this::toApiStatsVO).collect(Collectors.toList());
    }

    @Override
    public ApiTrendVO trend(String range) {
        String effective = switch (range == null ? "" : range) {
            case "week", "month", "all" -> range;
            default -> "day";
        };
        LocalDate today = LocalDate.now(ZONE);

        Map<String, MetricTrendRow> rowMap;
        List<String> labels;
        switch (effective) {
            case "week", "month" -> {
                LocalDate first = today.minusDays("week".equals(effective) ? 6 : 29);
                rowMap = toRowMap(apiMetricHourlyMapper.metricTrendByDay(first, today));
                labels = new ArrayList<>();
                for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
                    labels.add(d.format(DAY_LABEL));
                }
            }
            case "all" -> {
                rowMap = toRowMap(apiMetricHourlyMapper.metricTrendByMonth(ALL_SINCE, today));
                // 无数据月份补 0：从最早月份到当月
                String min = null;
                for (String key : rowMap.keySet()) {
                    if (min == null || key.compareTo(min) < 0) {
                        min = key;
                    }
                }
                labels = new ArrayList<>();
                if (min != null) {
                    YearMonth last = YearMonth.from(today);
                    for (YearMonth m = YearMonth.parse(min); !m.isAfter(last); m = m.plusMonths(1)) {
                        labels.add(m.toString());
                    }
                }
            }
            default -> {
                rowMap = toRowMap(apiMetricHourlyMapper.metricTrendByHour(today));
                labels = new ArrayList<>(24);
                for (int h = 0; h < 24; h++) {
                    labels.add(String.format("%02d", h));
                }
            }
        }

        List<ApiTrendVO.Point> points = new ArrayList<>(labels.size());
        for (String label : labels) {
            MetricTrendRow row = rowMap.get(label);
            points.add(ApiTrendVO.Point.builder()
                    .label(label)
                    .cnt(row == null || row.getCnt() == null ? 0L : row.getCnt())
                    .errCnt(row == null || row.getErrCnt() == null ? 0L : row.getErrCnt())
                    .maxMs(row == null || row.getMaxMs() == null ? 0L : row.getMaxMs())
                    .build());
        }
        return ApiTrendVO.builder()
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
    public ApiHourlyVO hourly(String api, LocalDate statDate) {
        if (api == null || api.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "api 不能为空");
        }
        LocalDate date = statDate == null ? LocalDate.now(ZONE) : statDate;
        Map<Integer, ApiHourlyRow> rowMap = apiMetricHourlyMapper.hourlyByApi(api, date).stream()
                .filter(r -> r.getHour() != null)
                .collect(Collectors.toMap(ApiHourlyRow::getHour, r -> r, (a, b) -> a));

        List<ApiHourlyVO.Hour> hours = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            ApiHourlyRow row = rowMap.get(h);
            hours.add(ApiHourlyVO.Hour.builder()
                    .hour(h)
                    .cnt(row == null || row.getCnt() == null ? 0L : row.getCnt())
                    .errCnt(row == null || row.getErrCnt() == null ? 0L : row.getErrCnt())
                    .slowCnt(row == null || row.getSlowCnt() == null ? 0L : row.getSlowCnt())
                    .maxMs(row == null || row.getMaxMs() == null ? 0 : row.getMaxMs())
                    .build());
        }
        return ApiHourlyVO.builder().api(api).date(date.toString()).hours(hours).build();
    }

    @Override
    public CursorPage<SlowApiLogVO> slowList(String api, LocalDate statDate, int limit, Long cursor) {
        int pageSize = Math.min(Math.max(limit, 1), LIMIT_MAX);

        LambdaQueryWrapper<SlowApiLog> wrapper = new LambdaQueryWrapper<SlowApiLog>()
                .orderByDesc(SlowApiLog::getId)
                .last("LIMIT " + (pageSize + 1));
        if (api != null && !api.isBlank()) {
            wrapper.eq(SlowApiLog::getApi, api);
        }
        if (statDate != null) {
            wrapper.between(SlowApiLog::getCreatedAt,
                    statDate.atStartOfDay(), statDate.plusDays(1).atStartOfDay());
        }
        if (cursor != null) {
            wrapper.lt(SlowApiLog::getId, cursor);
        }

        List<SlowApiLog> rows = slowApiLogMapper.selectList(wrapper);
        boolean hasMore = rows.size() > pageSize;
        List<SlowApiLog> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        if (pageRows.isEmpty()) {
            return CursorPage.empty();
        }

        List<SlowApiLogVO> vos = pageRows.stream().map(this::toSlowVO).collect(Collectors.toList());
        Long nextLastId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new CursorPage<>(vos, nextLastId, hasMore);
    }

    /** 趋力行转 Map（key=标签，过滤空标签）。 */
    private Map<String, MetricTrendRow> toRowMap(List<MetricTrendRow> rows) {
        Map<String, MetricTrendRow> map = new HashMap<>();
        if (rows != null) {
            for (MetricTrendRow r : rows) {
                if (r.getLabel() != null) {
                    map.put(r.getLabel(), r);
                }
            }
        }
        return map;
    }

    /** 聚合行 → VO（失败率保留 4 位小数）。 */
    private ApiStatsVO toApiStatsVO(ApiStatsRow row) {
        long cnt = row.getCnt() == null ? 0L : row.getCnt();
        long errCnt = row.getErrCnt() == null ? 0L : row.getErrCnt();
        double errRate = cnt > 0 ? Math.round(errCnt * 10000.0 / cnt) / 10000.0 : 0.0;
        return ApiStatsVO.builder()
                .api(row.getApi())
                .cnt(cnt)
                .errCnt(errCnt)
                .errRate(errRate)
                .slowCnt(row.getSlowCnt() == null ? 0L : row.getSlowCnt())
                .maxMs(row.getMaxMs() == null ? 0 : row.getMaxMs())
                .build();
    }

    /** 明细行 → VO。 */
    private SlowApiLogVO toSlowVO(SlowApiLog row) {
        return SlowApiLogVO.builder()
                .id(row.getId())
                .api(row.getApi())
                .userId(row.getUserId())
                .costMs(row.getCostMs() == null ? 0 : row.getCostMs())
                .success(row.getSuccess() != null && row.getSuccess() == 1)
                .errorMsg(row.getErrorMsg())
                .createdAt(row.getCreatedAt())
                .build();
    }
}
