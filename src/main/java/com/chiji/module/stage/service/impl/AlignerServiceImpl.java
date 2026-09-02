package com.chiji.module.stage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.enums.AlignerFilmEnum;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.enums.StageStatusEnum;
import com.chiji.module.stage.dto.AlignerTimeUpdateRequest;
import com.chiji.module.stage.dto.RevertAlignerRequest;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.stage.support.AlignerNodeAssembler;
import com.chiji.module.stage.support.StageModeSupport;
import com.chiji.module.stage.vo.AlignerNodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 牙套副服务实现。
 * <p>
 * 批量预排期规则：
 * <ul>
 *   <li>第 1 副：startDate = stage.startDate，endDate = startDate + days - 1，state = ACTIVE，currentDay = 1</li>
 *   <li>第 N 副：startDate = 第N-1副 endDate + 1，endDate = startDate + days - 1，state = FUTURE</li>
 * </ul>
 * 换副规则（按上/下午半天区分当天归属，全天保持「新副.startDate = 旧副.endDate + 1」无缝衔接）：
 * <ul>
 *   <li>12:00 前换副：当前副 state → DONE，endDate = 昨天（当天整天归属新副，新副从今天开始）</li>
 *   <li>12:00 及之后换副：当前副 state → DONE，endDate = 今天（当天整天归属旧副，新副从明天开始，
 *       避免深夜换副后刚过零点新副就显示「第 2 天」）</li>
 *   <li>兜底：endDate 不早于 startDate（防「当天开始当天换」产生倒挂脏数据）</li>
 *   <li>下一副 state → ACTIVE，startDate = 旧副新 endDate + 1，currentDay = 1</li>
 *   <li>后续 FUTURE 副从新副 endDate 起按各副自身 totalDays 依次重排</li>
 * </ul>
 * 反向换副规则（撤回误操作，按上一副佩戴进度分情况，已戴天数 = today - startDate + 1）：
 * <ul>
 *   <li>上一副已戴满周期（超期后才换的副）：endDate = max(用户选择的延长终点, 今天)，
 *       用户在弹窗决定「再戴几天」（含今天起算），前端折算为 endDate 传入，未传默认只还今天</li>
 *   <li>上一副未戴满周期（提前换的副）：按原计划终点（startDate + 该副 totalDays - 1）恢复，还原原排期</li>
 *   <li>当前副 state → FUTURE，startDate = 上一副新 endDate + 1，endDate 按该副自身 totalDays</li>
 *   <li>后续 FUTURE 副从当前副起按各自 totalDays 依次重排</li>
 * </ul>
 * 最后一副换副 = 结束阶段：当前副按上述规则置 DONE 后，阶段同步置 ENDED（与换副同事务，保证原子性）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlignerServiceImpl implements AlignerService {

    private final AlignerMapper alignerMapper;
    private final StageMapper stageMapper;
    private final AlignerNodeAssembler alignerNodeAssembler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreateAligners(Stage stage) {
        boolean dual = StageModeSupport.isDual(stage);
        // 双模按膜片类型取各自天数（创建阶段时已校验非空），单模取 daysPerAligner
        int softDays = dual && stage.getSoftDays() != null ? stage.getSoftDays() : 1;
        int hardDays = dual && stage.getHardDays() != null ? stage.getHardDays() : 1;
        LocalDate startDate = stage.getStartDate();
        boolean hasStartDate = startDate != null;

        LocalDate cursor = hasStartDate ? startDate : null;
        int totalNodes = StageModeSupport.resolveTotalNodes(stage);
        List<Aligner> aligners = new ArrayList<>(totalNodes);
        for (int num = 1; num <= totalNodes; num++) {
            // 双模：num 全局连续，软奇硬偶（先软后硬）；单模：无膜片类型
            boolean soft = dual && num % 2 == 1;
            int days = dual ? (soft ? softDays : hardDays) : (stage.getDaysPerAligner() != null ? stage.getDaysPerAligner() : 1);
            Aligner aligner = new Aligner();
            aligner.setStageId(stage.getId());
            aligner.setNum(num);
            if (dual) {
                aligner.setFilmType(soft ? AlignerFilmEnum.SOFT.getCode() : AlignerFilmEnum.HARD.getCode());
            }
            aligner.setTotalDays(days);

            if (num == 1) {
                // 第 1 副默认 ACTIVE
                aligner.setState(AlignerStateEnum.ACTIVE.getCode());
                aligner.setCurrentDay(1);
                if (hasStartDate) {
                    aligner.setStartDate(cursor);
                    aligner.setEndDate(cursor.plusDays(days - 1));
                    cursor = aligner.getEndDate().plusDays(1);
                }
            } else {
                // 后续 FUTURE
                aligner.setState(AlignerStateEnum.FUTURE.getCode());
                if (hasStartDate) {
                    aligner.setStartDate(cursor);
                    aligner.setEndDate(cursor.plusDays(days - 1));
                    cursor = aligner.getEndDate().plusDays(1);
                }
            }
            aligners.add(aligner);
        }

        // 批量插入（MyBatis-Plus 默认逐条 insert，count 通常 ≤ 200，可接受）
        for (Aligner aligner : aligners) {
            alignerMapper.insert(aligner);
        }
        log.info("批量生成牙套副成功, stageId={}, totalNodes={}, dual={}, hasStartDate={}",
                stage.getId(), totalNodes, dual, hasStartDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AlignerNodeVO> finishAligner(Long userId, Long alignerId) {
        // 1. 查询当前副并校验归属
        Aligner current = alignerMapper.selectById(alignerId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "牙套副不存在");
        }
        Stage stage = stageMapper.selectById(current.getStageId());
        if (stage == null || !userId.equals(stage.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该牙套副");
        }
        if (!AlignerStateEnum.ACTIVE.getCode().equals(current.getState())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前副不是佩戴中状态，无法结束");
        }

        LocalDate today = LocalDate.now();

        // 2. 当前副 → DONE
        //    结束日按半天规则：12:00 前换副 = 昨天；12:00 及之后换副 = 今天
        //    兜底：endDate 不早于 startDate（防「当天开始当天换」倒挂）
        boolean afternoon = LocalTime.now().getHour() >= 12;
        LocalDate doneEndDate = afternoon ? today : today.minusDays(1);
        if (current.getStartDate() != null && doneEndDate.isBefore(current.getStartDate())) {
            doneEndDate = current.getStartDate();
        }
        alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                .eq(Aligner::getId, alignerId)
                .set(Aligner::getState, AlignerStateEnum.DONE.getCode())
                .set(Aligner::getEndDate, doneEndDate)
                .set(Aligner::getCurrentDay, null));

        // 3. 下一副 → ACTIVE，startDate = 上一副新 endDate + 1（全天保持无缝衔接不变式）：
        //    12:00 前换副旧副 endDate=昨天 → 新副从今天开始（今天整天归新副）；
        //    12:00 后换副旧副 endDate=今天（今天整天归旧副）→ 新副从明天开始，
        //    避免深夜（如 23:43）换副后刚过零点新副就显示「第 2 天」
        //    天数优先取该副自身 totalDays（可能被手动调整过），缺失回退阶段每副天数
        Aligner next = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, current.getStageId())
                .eq(Aligner::getNum, current.getNum() + 1));

        if (next == null) {
            // 已是最后一副：结束当前副即结束整个阶段（前端弹窗已按「结束阶段」二次确认）
            // 与换副同事务，保证「副 DONE + 阶段 ENDED」原子生效
            stageMapper.update(null, new LambdaUpdateWrapper<Stage>()
                    .eq(Stage::getId, stage.getId())
                    .set(Stage::getStatus, StageStatusEnum.ENDED.getCode()));
            log.info("结束最后一副并结束阶段, userId={}, stageId={}, alignerId={}", userId, current.getStageId(), alignerId);
        } else {
            // 天数优先取该副自身 totalDays（可能被手动调整过），缺失按膜片类型回退阶段配置（双模软/硬天数），再兜底 1
            Integer fallbackDays = StageModeSupport.resolveDefaultDays(stage, next);
            int nextDays = next.getTotalDays() != null && next.getTotalDays() > 0
                    ? next.getTotalDays()
                    : (fallbackDays != null && fallbackDays > 0 ? fallbackDays : 1);
            LocalDate nextStartDate = doneEndDate.plusDays(1);
            LocalDate nextEndDate = nextStartDate.plusDays(nextDays - 1);
            alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                    .eq(Aligner::getId, next.getId())
                    .set(Aligner::getState, AlignerStateEnum.ACTIVE.getCode())
                    .set(Aligner::getStartDate, nextStartDate)
                    .set(Aligner::getEndDate, nextEndDate)
                    .set(Aligner::getCurrentDay, 1));

            // 4. 联动重排后续 FUTURE 副（num > next.num）：按各副自身 totalDays 依次顺延
            int repackCount = repackAfter(current.getStageId(), next.getNum(), nextEndDate);
            log.info("换副成功, userId={}, stageId={}, finishedAlignerNum={}, nextAlignerNum={}, repackCount={}",
                    userId, current.getStageId(), current.getNum(), next.getNum(), repackCount);
        }

        // 5. 返回更新后的节点列表
        List<Aligner> all = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, current.getStageId())
                .orderByAsc(Aligner::getNum));
        return alignerNodeAssembler.toNodeList(all);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AlignerNodeVO> revertAligner(Long userId, Long alignerId, RevertAlignerRequest request) {
        // 1. 查询当前副并校验归属
        Aligner current = alignerMapper.selectById(alignerId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "牙套副不存在");
        }
        Stage stage = stageMapper.selectById(current.getStageId());
        if (stage == null || !userId.equals(stage.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该牙套副");
        }
        if (!AlignerStateEnum.ACTIVE.getCode().equals(current.getState())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前副不是佩戴中状态，无法换回");
        }

        // 2. 查找上一副（num-1），校验存在且为 DONE
        Aligner prev = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, current.getStageId())
                .eq(Aligner::getNum, current.getNum() - 1));
        if (prev == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已是第一副，无法换回");
        }
        if (!AlignerStateEnum.DONE.getCode().equals(prev.getState())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上一副不是已完成状态，无法换回");
        }

        LocalDate today = LocalDate.now();

        // 3. 上一副 → ACTIVE，endDate 按佩戴进度分情况：
        //    周期天数优先取该副自身 totalDays（可能被手动调整过），缺失按膜片类型回退阶段配置（双模软/硬天数）
        //    情况 A（已戴满周期，超期换的副）：endDate = max(用户选择的延长终点, 今天)，弹窗「再戴 X 天」含今天起算
        //    情况 B（未戴满周期，提前换的副）：按原计划终点恢复，还原原排期（planEnd >= today 恒成立）
        //    currentDay 如实按 today 推算（不截断，超期如实显示）
        Integer prevTotal = prev.getTotalDays();
        Integer prevFallback = StageModeSupport.resolveDefaultDays(stage, prev);
        int prevDays = prevTotal != null && prevTotal > 0 ? prevTotal
                : (prevFallback != null && prevFallback > 0 ? prevFallback : 1);
        LocalDate prevEndDate = null;
        Integer currentDay = 1;
        if (prev.getStartDate() != null) {
            long worn = ChronoUnit.DAYS.between(prev.getStartDate(), today) + 1;
            if (worn >= prevDays) {
                LocalDate reqEnd = request != null ? request.endDate() : null;
                if (reqEnd != null && reqEnd.isBefore(today)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "延长终点不能早于今天");
                }
                prevEndDate = reqEnd != null ? reqEnd : today;
            } else {
                prevEndDate = prev.getStartDate().plusDays(prevDays - 1);
            }
            currentDay = (int) Math.max(1, worn);
        }
        alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                .eq(Aligner::getId, prev.getId())
                .set(Aligner::getState, AlignerStateEnum.ACTIVE.getCode())
                .set(Aligner::getCurrentDay, currentDay)
                .set(Aligner::getEndDate, prevEndDate));

        // 4. 当前副 → FUTURE
        //    startDate = 上一副 endDate + 1；endDate = startDate + 天数 - 1；清 currentDay
        //    天数优先取该副自身 totalDays（可能被手动调整过），缺失按膜片类型回退阶段配置（双模软/硬天数）
        Integer currentFallback = StageModeSupport.resolveDefaultDays(stage, current);
        int currentDays = current.getTotalDays() != null && current.getTotalDays() > 0
                ? current.getTotalDays()
                : (currentFallback != null && currentFallback > 0 ? currentFallback : 1);
        LocalDate currentStartDate = prevEndDate != null ? prevEndDate.plusDays(1) : null;
        LocalDate currentEndDate = currentStartDate != null ? currentStartDate.plusDays(currentDays - 1) : null;
        alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                .eq(Aligner::getId, current.getId())
                .set(Aligner::getState, AlignerStateEnum.FUTURE.getCode())
                .set(Aligner::getStartDate, currentStartDate)
                .set(Aligner::getEndDate, currentEndDate)
                .set(Aligner::getCurrentDay, null));

        // 5. 联动重排后续 FUTURE 副（num > current.num）：按各副自身 totalDays 依次顺延
        int repackCount = 0;
        if (currentEndDate != null) {
            repackCount = repackAfter(current.getStageId(), current.getNum(), currentEndDate);
            log.info("换回上一副成功, userId={}, stageId={}, prevAlignerNum={}, currentAlignerNum={}, repackCount={}",
                    userId, current.getStageId(), prev.getNum(), current.getNum(), repackCount);
        } else {
            log.info("换回上一副成功, userId={}, stageId={}, prevAlignerNum={}, currentAlignerNum={}",
                    userId, current.getStageId(), prev.getNum(), current.getNum());
        }

        // 6. 返回更新后的节点列表
        List<Aligner> all = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, current.getStageId())
                .orderByAsc(Aligner::getNum));
        return alignerNodeAssembler.toNodeList(all);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AlignerNodeVO> updateAlignerTime(Long userId, Long alignerId, AlignerTimeUpdateRequest req) {
        // 1. 查询锚点副并校验归属
        Aligner anchor = alignerMapper.selectById(alignerId);
        if (anchor == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "牙套副不存在");
        }
        Stage stage = stageMapper.selectById(anchor.getStageId());
        if (stage == null || !userId.equals(stage.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该牙套副");
        }
        if (req.startDate() == null && req.endDate() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供开始或结束日期");
        }

        // 2. 计算锚点副新的起止日期与天数
        LocalDate newStart;
        LocalDate newEnd;
        int days;
        if (req.startDate() != null && req.endDate() != null) {
            // 两者都传：按给定起止调整
            newStart = req.startDate();
            newEnd = req.endDate();
            days = (int) ChronoUnit.DAYS.between(newStart, newEnd) + 1;
        } else if (req.startDate() != null) {
            // 仅改开始日期：天数不变，endDate = startDate + 天数 - 1
            newStart = req.startDate();
            Integer fallbackDays = StageModeSupport.resolveDefaultDays(stage, anchor);
            days = anchor.getTotalDays() != null && anchor.getTotalDays() > 0
                    ? anchor.getTotalDays()
                    : (fallbackDays != null && fallbackDays > 0 ? fallbackDays : 1);
            newEnd = newStart.plusDays(days - 1);
        } else {
            // 仅改结束日期：天数 = endDate - startDate + 1
            if (anchor.getStartDate() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该副还没有开始日期，请先设置开始日期");
            }
            newStart = anchor.getStartDate();
            newEnd = req.endDate();
            days = (int) ChronoUnit.DAYS.between(newStart, newEnd) + 1;
        }
        if (days < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        // 3. 校验不与上一副重叠：新开始日期必须不早于上一副结束日期的次日
        Aligner prev = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, anchor.getStageId())
                .eq(Aligner::getNum, anchor.getNum() - 1));
        if (prev != null && prev.getEndDate() != null && !newStart.isAfter(prev.getEndDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始日期与上一副时间重叠，请往后调整");
        }

        // 4. 更新锚点副
        alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                .eq(Aligner::getId, anchor.getId())
                .set(Aligner::getStartDate, newStart)
                .set(Aligner::getEndDate, newEnd)
                .set(Aligner::getTotalDays, days));
        // ACTIVE 副的 currentDay 按新 startDate 动态推算（保底第 1 天）
        if (AlignerStateEnum.ACTIVE.getCode().equals(anchor.getState())) {
            long diff = ChronoUnit.DAYS.between(newStart, LocalDate.now()) + 1;
            alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                    .eq(Aligner::getId, anchor.getId())
                    .set(Aligner::getCurrentDay, (int) Math.max(1, diff)));
        }

        // 5. 联动顺延后续副（num > anchor.num），保证时间不重叠、无缝衔接
        int repackCount = repackAfter(anchor.getStageId(), anchor.getNum(), newEnd);
        log.info("调整牙套时间成功, userId={}, stageId={}, alignerId={}, newStart={}, newEnd={}, days={}, repackCount={}",
                userId, anchor.getStageId(), anchor.getId(), newStart, newEnd, days, repackCount);

        // 6. 返回更新后的节点列表
        List<Aligner> all = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, anchor.getStageId())
                .orderByAsc(Aligner::getNum));
        return alignerNodeAssembler.toNodeList(all);
    }

    /**
     * 联动重排 {@code fromNum} 副之后的全部副：startDate = 上一副 endDate + 1，endDate = startDate + 天数 - 1。
     * 天数取各副自身 {@code totalDays}（保留用户手动调整的天数），缺失时回退该副历史排期天数。
     *
     * @param stageId 阶段 ID
     * @param fromNum 锚点副序号（其后开始重排，不含本副）
     * @param fromEnd 锚点副新的结束日期
     * @return 重排的副数量
     */
    private int repackAfter(Long stageId, int fromNum, LocalDate fromEnd) {
        LocalDate cursor = fromEnd.plusDays(1);
        List<Aligner> after = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stageId)
                .gt(Aligner::getNum, fromNum)
                .orderByAsc(Aligner::getNum));
        for (Aligner a : after) {
            int days = a.getTotalDays() != null && a.getTotalDays() > 0
                    ? a.getTotalDays()
                    : (a.getStartDate() != null && a.getEndDate() != null
                            ? (int) ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate()) + 1 : 1);
            alignerMapper.update(null, new LambdaUpdateWrapper<Aligner>()
                    .eq(Aligner::getId, a.getId())
                    .set(Aligner::getStartDate, cursor)
                    .set(Aligner::getEndDate, cursor.plusDays(days - 1)));
            cursor = cursor.plusDays(days);
        }
        return after.size();
    }
}
