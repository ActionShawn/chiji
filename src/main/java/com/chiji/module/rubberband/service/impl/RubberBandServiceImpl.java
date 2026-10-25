package com.chiji.module.rubberband.service.impl;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.rubberband.dto.RubberBandDayRow;
import com.chiji.module.rubberband.dto.RubberBandPrescriptionSaveRequest;
import com.chiji.module.rubberband.entity.RubberBandPrescription;
import com.chiji.module.rubberband.entity.RubberBandRecord;
import com.chiji.module.rubberband.mapper.RubberBandPrescriptionMapper;
import com.chiji.module.rubberband.mapper.RubberBandRecordMapper;
import com.chiji.module.rubberband.service.RubberBandService;
import com.chiji.module.rubberband.vo.RubberBandIncrementVO;
import com.chiji.module.rubberband.vo.RubberBandPrescriptionVO;
import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import com.chiji.module.stage.support.TreatmentGateSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 橡皮筋每日计数服务实现（第 1 批 REQ-FB-006 勾选打卡 → 第 2 批 OPT-FB-04 次数计数）。
 * <p>
 * 关键设计（评审 1.x / OPT-FB-04 SQL 裁决）：
 * <ul>
 *   <li>幂等：唯一键 {@code uk_rb_user_date} 兜底并发双写，increment 走
 *       {@code INSERT ... ON DUPLICATE KEY UPDATE count = count + 1}，行锁保证原子累加、并发不丢更新</li>
 *   <li>禁逻辑删除行：行永不删除（否则唯一键槽位被占）；done 列冻结废弃，写入路径全部走 count</li>
 *   <li>仅当日：recordDate 由服务端取 now，不接受前端传日期（排除补录）</li>
 *   <li>单日上限：超限返回业务错误码（防误触/异常流量污染第 2 批节奏分析数据）</li>
 *   <li>聚合：连续天数（count&gt;0 连续自然日，今天未计数不断签）与本周 SUM 由服务端计算
 *       （连续天数可能超出 7 天窗口，禁止前端二次拉全量日期）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubberBandServiceImpl implements RubberBandService {

    /** 近 7 天点阵窗口天数（今天在最右）。 */
    private static final int RECENT_DAYS = 7;

    /**
     * 单日计数上限（评审建议 50，产品终裁口径）：
     * 防误触/异常流量污染第 2 批节奏分析数据；超限返回业务错误码。
     */
    private static final int DAILY_LIMIT = 50;

    /** 医嘱更新时间文案「MM.dd」格式。 */
    private static final DateTimeFormatter PRESCRIPTION_FMT = DateTimeFormatter.ofPattern("MM.dd", Locale.CHINA);

    private final RubberBandRecordMapper rubberBandRecordMapper;
    private final RubberBandPrescriptionMapper rubberBandPrescriptionMapper;
    private final UserMapper userMapper;

    @Override
    public RubberBandRecentVO recent(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(RECENT_DAYS - 1L);

        // 1. 近 7 天范围查询（一条 SQL 走 uk_rb_user_date 前缀）
        List<RubberBandDayRow> rows = rubberBandRecordMapper.selectRange(userId, start, today);
        Map<LocalDate, Integer> countMap = new HashMap<>(rows.size());
        for (RubberBandDayRow row : rows) {
            countMap.put(row.date(), row.count() == null ? 0 : row.count());
        }

        // 2. 补齐无记录日期（count=0，前端空描边渲染），日期升序 = 今天在最右
        List<RubberBandRecentVO.RubberBandDayVO> days = new ArrayList<>(RECENT_DAYS);
        for (int i = 0; i < RECENT_DAYS; i++) {
            LocalDate date = start.plusDays(i);
            days.add(new RubberBandRecentVO.RubberBandDayVO(date, countMap.getOrDefault(date, 0)));
        }

        // 3. 聚合：连续天数（可能 >7 天窗口，服务端算）+ 本周 SUM（周一起点口径与前端 §4.4 一致）
        int streakDays = calcStreakDays(userId, today);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        Integer weekCount = rubberBandRecordMapper.sumRange(userId, monday, today);

        int todayCount = countMap.getOrDefault(today, 0);
        return new RubberBandRecentVO(todayCount, days, streakDays, weekCount == null ? 0 : weekCount);
    }

    @Override
    public RubberBandIncrementVO increment(Long userId) {
        // 1. 门控（唯一事实源）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        TreatmentGateSupport.assertFixedBracket(user.getTreatmentType());

        // 2. 单日上限校验（防误触/异常流量污染节奏分析数据；回读以真实计数为准）
        LocalDate today = LocalDate.now();
        Integer current = rubberBandRecordMapper.selectCount(userId, today);
        int currentCount = current == null ? 0 : current;
        if (currentCount >= DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.RUBBER_BAND_DAILY_LIMIT);
        }

        // 3. upsert 原子累加（唯一键兜底并发双写，行锁保证并发不丢更新）
        rubberBandRecordMapper.incrementOnce(IdWorker.getId(), userId, today);

        // 4. 回读落库计数返回（并发冲突时以真实最终计数为准）
        Integer persisted = rubberBandRecordMapper.selectCount(userId, today);
        int finalCount = persisted == null ? currentCount + 1 : persisted;
        log.info("橡皮筋计数+1, userId={}, date={}, persistedCount={}", userId, today, finalCount);
        return new RubberBandIncrementVO(today, finalCount);
    }

    /**
     * 连续打卡天数：count&gt;0 连续自然日；今天未计数不断签（当日尚未结束），从昨天往回数。
     * 一次取全部 count&gt;0 日期（降序）后内存算连续段，避免按天逐日探测 SQL。
     */
    private int calcStreakDays(Long userId, LocalDate today) {
        List<LocalDate> dates = rubberBandRecordMapper.selectPositiveDatesDesc(userId);
        if (dates.isEmpty()) {
            return 0;
        }
        // 最新计数日必须是今天或昨天，否则连续段早已中断；今天未计数（最新=昨天）从昨天起算
        LocalDate first = dates.get(0);
        if (!first.equals(today) && !first.equals(today.minusDays(1))) {
            return 0;
        }
        int streak = 0;
        LocalDate cursor = first;
        for (LocalDate date : dates) {
            if (!date.equals(cursor)) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    @Override
    public RubberBandPrescriptionVO prescription(Long userId) {
        RubberBandPrescription entity = rubberBandPrescriptionMapper.selectOne(
                new LambdaQueryWrapper<RubberBandPrescription>().eq(RubberBandPrescription::getUserId, userId));
        return entity == null ? null : toPrescriptionVO(entity);
    }

    @Override
    public RubberBandPrescriptionVO savePrescription(Long userId, RubberBandPrescriptionSaveRequest request) {
        // 归一化：全字段 trim 后空置 null
        String tractionType = trimToNull(request.tractionType());
        String side = trimToNull(request.side());
        String position = trimToNull(request.position());
        Integer dailyTarget = request.dailyTarget();

        RubberBandPrescription entity = rubberBandPrescriptionMapper.selectOne(
                new LambdaQueryWrapper<RubberBandPrescription>().eq(RubberBandPrescription::getUserId, userId));

        // 全字段空 → 视为清空医嘱：逻辑删除该行并返回 null（前端回「去填写」空态）
        if (tractionType == null && side == null && position == null && dailyTarget == null) {
            if (entity != null) {
                rubberBandPrescriptionMapper.deleteById(entity.getId());
                log.info("橡皮筋医嘱已清空, userId={}", userId);
            }
            return null;
        }

        if (entity == null) {
            entity = new RubberBandPrescription();
            entity.setUserId(userId);
            entity.setTractionType(tractionType);
            entity.setSide(side);
            entity.setPosition(position);
            entity.setDailyTarget(dailyTarget);
            rubberBandPrescriptionMapper.insert(entity);
        } else {
            entity.setTractionType(tractionType);
            entity.setSide(side);
            entity.setPosition(position);
            entity.setDailyTarget(dailyTarget);
            rubberBandPrescriptionMapper.updateById(entity);
        }
        log.info("橡皮筋医嘱已保存, userId={}, tractionType={}, dailyTarget={}", userId, tractionType, dailyTarget);
        return toPrescriptionVO(entity);
    }

    /** 实体 → 医嘱 VO（updatedDate 文案「MM.dd」）。 */
    private RubberBandPrescriptionVO toPrescriptionVO(RubberBandPrescription entity) {
        LocalDateTime updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt();
        return new RubberBandPrescriptionVO(
                entity.getTractionType(),
                entity.getSide(),
                entity.getPosition(),
                entity.getDailyTarget(),
                updatedAt == null ? "" : updatedAt.format(PRESCRIPTION_FMT)
        );
    }

    /** 空串/空白归 null（医嘱字段归一化）。 */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
