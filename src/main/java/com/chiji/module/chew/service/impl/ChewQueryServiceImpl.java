package com.chiji.module.chew.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.dto.DayValueRow;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Aligner;
import com.chiji.entity.ChewSession;
import com.chiji.module.chew.mapper.ChewSessionMapper;
import com.chiji.module.chew.service.ChewQueryService;
import com.chiji.module.chew.support.ChewConstants;
import com.chiji.module.chew.support.ChewLabels;
import com.chiji.module.chew.support.ChewMath;
import com.chiji.module.chew.support.ChewTimes;
import com.chiji.module.chew.support.ChewVos;
import com.chiji.module.chew.vo.ChewDayDetailVO;
import com.chiji.module.chew.vo.ChewDayVO;
import com.chiji.module.chew.vo.ChewRecordVO;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.ChewStatsVO;
import com.chiji.module.chew.vo.TodayChewVO;
import com.chiji.module.stage.mapper.AlignerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 咬胶只读查询实现。
 * <p>
 * 聚合口径与 {@link ChewSessionMapper} 的「已结束」SQL 同源；今日展示值额外并入进行中实时净时长。
 */
@Service
@RequiredArgsConstructor
public class ChewQueryServiceImpl implements ChewQueryService {

    private final ChewSessionMapper chewSessionMapper;
    private final AlignerMapper alignerMapper;

    @Override
    public ChewSessionVO active(Long userId) {
        LocalDateTime now = ChewTimes.now();
        ChewSession running = findRunning(userId);
        return running == null ? null : ChewVos.toVO(running, now, labelOf(running));
    }

    @Override
    public TodayChewVO today(Long userId) {
        LocalDateTime now = ChewTimes.now();
        LocalDate date = now.toLocalDate();
        LocalDateTime start = ChewTimes.startOf(date);
        LocalDateTime end = ChewTimes.endOf(date);

        long endedNetSec = singleNetSec(chewSessionMapper.sumNetSecByDay(userId, start, end));
        int count = singleCount(chewSessionMapper.countByDay(userId, start, end));

        // reachedCount：当日已结束会话中 netSec >= 该会话 targetSec 的条数
        int reachedCount = 0;
        for (ChewSession s : chewSessionMapper.listOfDay(userId, start, end)) {
            if (s.getEndedAt() == null) {
                continue;
            }
            if (ChewMath.netSec(s, now) >= ChewVos.targetOf(s)) {
                reachedCount++;
            }
        }

        ChewSession running = findRunning(userId);
        long activeNet = running == null ? 0L : ChewMath.netSec(running, now);
        ChewSessionVO activeVO = running == null ? null : ChewVos.toVO(running, now, labelOf(running));

        return new TodayChewVO(
                date.toString(),
                endedNetSec,
                endedNetSec + activeNet,
                count,
                reachedCount,
                ChewConstants.GOAL_SEC,
                activeVO
        );
    }

    @Override
    public ChewDayVO calendar(Long userId, YearMonth month) {
        LocalDateTime start = ChewTimes.startOfMonth(month);
        LocalDateTime end = ChewTimes.endOfMonth(month);
        Map<LocalDate, Long> netMap = toLongMap(chewSessionMapper.sumNetSecByDay(userId, start, end));
        Map<LocalDate, Long> countMap = toLongMap(chewSessionMapper.countByDay(userId, start, end));

        int length = month.lengthOfMonth();
        List<String> days = new ArrayList<>(length);
        List<Long> netSec = new ArrayList<>(length);
        List<Integer> count = new ArrayList<>(length);
        for (int d = 1; d <= length; d++) {
            LocalDate day = month.atDay(d);
            days.add(day.toString());
            netSec.add(netMap.getOrDefault(day, 0L));
            count.add(countMap.getOrDefault(day, 0L).intValue());
        }
        return new ChewDayVO(month.toString(), ChewConstants.GOAL_SEC, days, netSec, count);
    }

    @Override
    public ChewDayDetailVO day(Long userId, LocalDate date) {
        LocalDateTime start = ChewTimes.startOf(date);
        LocalDateTime end = ChewTimes.endOf(date);
        List<ChewSession> ended = chewSessionMapper.listOfDay(userId, start, end).stream()
                .filter(s -> s.getEndedAt() != null)
                .collect(Collectors.toList());

        Map<Long, Aligner> alignerMap = loadAligners(ended);
        long netSec = 0L;
        List<ChewRecordVO> records = new ArrayList<>(ended.size());
        for (ChewSession s : ended) {
            long net = ChewMath.netSec(s, end);
            netSec += net;
            Aligner aligner = s.getAlignerId() == null ? null : alignerMap.get(s.getAlignerId());
            LocalDate startedDate = s.getStartedAt() == null ? null : s.getStartedAt().toLocalDate();
            records.add(new ChewRecordVO(
                    s.getId(),
                    millis(s.getStartedAt()),
                    millis(s.getEndedAt()),
                    net,
                    ChewLabels.of(aligner, startedDate)
            ));
        }
        return new ChewDayDetailVO(date.toString(), netSec, ended.size(), ChewConstants.GOAL_SEC, records);
    }

    @Override
    public ChewStatsVO stats(Long userId, String range) {
        DateWindow window = resolveWindow(range);
        LocalDateTime start = ChewTimes.startOf(window.startDate());
        LocalDateTime end = ChewTimes.endOf(window.endDate());
        Map<LocalDate, Long> netMap = toLongMap(chewSessionMapper.sumNetSecByDay(userId, start, end));
        Map<LocalDate, Long> countMap = toLongMap(chewSessionMapper.countByDay(userId, start, end));

        List<ChewStatsVO.StatsDayVO> days = new ArrayList<>();
        long totalNetSec = 0L;
        int sessionCount = 0;
        int activeDayCount = 0;
        int reachedDayCount = 0;
        for (LocalDate d = window.startDate(); !d.isAfter(window.endDate()); d = d.plusDays(1)) {
            long net = netMap.getOrDefault(d, 0L);
            int cnt = countMap.getOrDefault(d, 0L).intValue();
            days.add(new ChewStatsVO.StatsDayVO(d.toString(), net, cnt));
            totalNetSec += net;
            sessionCount += cnt;
            if (cnt > 0) {
                activeDayCount++;
            }
            if (net >= ChewConstants.GOAL_SEC) {
                reachedDayCount++;
            }
        }
        long avg = activeDayCount == 0 ? 0L : Math.round((double) totalNetSec / activeDayCount);
        return new ChewStatsVO(
                window.range(),
                window.startDate().toString(),
                window.endDate().toString(),
                ChewConstants.GOAL_SEC,
                totalNetSec,
                sessionCount,
                activeDayCount,
                reachedDayCount,
                avg,
                days
        );
    }

    /** 当前进行中会话：依赖 {@code @TableLogic} 自动追加 {@code deleted = 0}。 */
    private ChewSession findRunning(Long userId) {
        return chewSessionMapper.selectOne(new LambdaQueryWrapper<ChewSession>()
                .eq(ChewSession::getUserId, userId)
                .isNull(ChewSession::getEndedAt)
                .orderByDesc(ChewSession::getStartedAt)
                .last("LIMIT 1"));
    }

    private long singleNetSec(List<DayValueRow> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).getValue() == null) {
            return 0L;
        }
        return rows.get(0).getValue();
    }

    private int singleCount(List<DayValueRow> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).getValue() == null) {
            return 0;
        }
        return rows.get(0).getValue().intValue();
    }

    private Map<LocalDate, Long> toLongMap(List<DayValueRow> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        if (rows != null) {
            for (DayValueRow row : rows) {
                if (row.getDate() != null) {
                    map.put(row.getDate(), row.getValue() == null ? 0L : row.getValue());
                }
            }
        }
        return map;
    }

    /** 批量取归属副（selectList 自动带 deleted = 0）。 */
    private Map<Long, Aligner> loadAligners(List<ChewSession> sessions) {
        Set<Long> ids = sessions.stream()
                .map(ChewSession::getAlignerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Aligner> map = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Aligner a : alignerMapper.selectList(new LambdaQueryWrapper<Aligner>().in(Aligner::getId, ids))) {
                map.put(a.getId(), a);
            }
        }
        return map;
    }

    private String labelOf(ChewSession s) {
        if (s == null || s.getAlignerId() == null) {
            return "";
        }
        Aligner aligner = alignerMapper.selectById(s.getAlignerId());
        LocalDate startedDate = s.getStartedAt() == null ? null : s.getStartedAt().toLocalDate();
        return ChewLabels.of(aligner, startedDate);
    }

    private Long millis(LocalDateTime t) {
        return t == null ? null : ChewTimes.toEpochMillis(t);
    }

    /** 规整统计口径并解析区间；空回退 LAST_7，非法抛 {@code CHEW_PARAM_INVALID}。 */
    private DateWindow resolveWindow(String range) {
        String normalized = range == null ? "" : range.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            normalized = ChewConstants.RANGE_LAST_7;
        }
        LocalDate today = ChewTimes.today();
        return switch (normalized) {
            case ChewConstants.RANGE_LAST_7 -> new DateWindow(normalized, today.minusDays(6), today);
            case ChewConstants.RANGE_CURRENT_MONTH -> new DateWindow(normalized, today.withDayOfMonth(1), today);
            case ChewConstants.RANGE_LAST_30 -> new DateWindow(normalized, today.minusDays(29), today);
            case ChewConstants.RANGE_YEAR -> new DateWindow(normalized, today.withDayOfYear(1), today);
            default -> throw new BusinessException(ErrorCode.CHEW_PARAM_INVALID,
                    "range 取值应为 LAST_7 / CURRENT_MONTH / LAST_30 / YEAR");
        };
    }

    /** 统计区间（含起止日）。 */
    private record DateWindow(String range, LocalDate startDate, LocalDate endDate) {
    }
}
