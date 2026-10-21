package com.chiji.module.rubberband.service.impl;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.rubberband.dto.RubberBandDayRow;
import com.chiji.module.rubberband.entity.RubberBandRecord;
import com.chiji.module.rubberband.mapper.RubberBandRecordMapper;
import com.chiji.module.rubberband.service.RubberBandService;
import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import com.chiji.module.rubberband.vo.RubberBandToggleVO;
import com.chiji.module.stage.support.TreatmentGateSupport;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 橡皮筋每日打卡服务实现（固定托槽第 1 批，REQ-FB-006）。
 * <p>
 * 关键设计（评审 1.x / 4.1）：
 * <ul>
 *   <li>幂等：唯一键 {@code uk_rb_user_date} 兜底并发双写，toggle 走
 *       {@code INSERT ... ON DUPLICATE KEY UPDATE done = VALUES(done)}，落库后回读返回最终态</li>
 *   <li>禁逻辑删除行：取消打卡 = done 0↔1 翻转，行永不删除（否则唯一键槽位被占）</li>
 *   <li>仅当日：recordDate 由服务端取 now，不接受前端传日期（REQ-FB-006 明确排除补录）</li>
 *   <li>跨天口径：服务端时区当日（与现有打卡口径一致），跨天后无行即「今日未打卡」</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubberBandServiceImpl implements RubberBandService {

    /** 近 7 天点阵窗口天数（今天在最右）。 */
    private static final int RECENT_DAYS = 7;

    /** 打卡状态：已打卡。 */
    private static final int DONE = 1;

    /** 打卡状态：已取消。 */
    private static final int UNDONE = 0;

    private final RubberBandRecordMapper rubberBandRecordMapper;
    private final UserMapper userMapper;

    @Override
    public RubberBandRecentVO recent(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(RECENT_DAYS - 1L);

        // 1. 近 7 天范围查询（一条 SQL 走 uk_rb_user_date 前缀）
        List<RubberBandDayRow> rows = rubberBandRecordMapper.selectRange(userId, start, today);
        Map<LocalDate, Boolean> doneMap = new HashMap<>(rows.size());
        for (RubberBandDayRow row : rows) {
            doneMap.put(row.date(), row.done() != null && row.done() == DONE);
        }

        // 2. 补齐无记录日期（done=false，前端空描边渲染），日期升序 = 今天在最右
        List<RubberBandRecentVO.RubberBandDayVO> days = new ArrayList<>(RECENT_DAYS);
        for (int i = 0; i < RECENT_DAYS; i++) {
            LocalDate date = start.plusDays(i);
            days.add(new RubberBandRecentVO.RubberBandDayVO(date, doneMap.getOrDefault(date, Boolean.FALSE)));
        }

        boolean todayDone = doneMap.getOrDefault(today, Boolean.FALSE);
        return new RubberBandRecentVO(todayDone, days);
    }

    @Override
    public RubberBandToggleVO toggle(Long userId) {
        // 1. 门控（唯一事实源）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        TreatmentGateSupport.assertFixedBracket(user.getTreatmentType());

        // 2. 读当前状态，计算翻转目标（无行/已取消 → 打卡；已打卡 → 取消）
        LocalDate today = LocalDate.now();
        Integer current = rubberBandRecordMapper.selectDone(userId, today);
        int target = (current != null && current == DONE) ? UNDONE : DONE;

        // 3. upsert 幂等写入（唯一键兜底并发双写）
        rubberBandRecordMapper.upsertDone(IdWorker.getId(), userId, today, target);

        // 4. 回读落库状态返回（并发冲突时以真实最终态为准）
        Integer persisted = rubberBandRecordMapper.selectDone(userId, today);
        boolean finalDone = persisted != null && persisted == DONE;
        log.info("橡皮筋打卡翻转, userId={}, date={}, target={}, persistedDone={}", userId, today, target, finalDone);
        return new RubberBandToggleVO(today, finalDone);
    }
}
