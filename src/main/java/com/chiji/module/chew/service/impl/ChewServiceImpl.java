package com.chiji.module.chew.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Aligner;
import com.chiji.entity.ChewSession;
import com.chiji.module.chew.dto.ChewStartRequest;
import com.chiji.module.chew.dto.ChewStateChangeRequest;
import com.chiji.module.chew.mapper.ChewSessionMapper;
import com.chiji.module.chew.service.ChewQueryService;
import com.chiji.module.chew.service.ChewService;
import com.chiji.module.chew.support.ChewConstants;
import com.chiji.module.chew.support.ChewLabels;
import com.chiji.module.chew.support.ChewMath;
import com.chiji.module.chew.support.ChewSourceEnum;
import com.chiji.module.chew.support.ChewTimes;
import com.chiji.module.chew.support.ChewVos;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.TodayChewVO;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.service.AlignerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 咬胶会话状态机实现。
 * <p>
 * 关键实现约定（与方案 §4.2 片段的必要偏差）：
 * <ul>
 *   <li>{@code application.yml} 未配 {@code update-strategy}，默认 {@code NOT_NULL}，
 *       {@code updateById(entity)} 会跳过 null 字段，无法把 {@code pausedAt}/{@code endedAt} 置空；
 *       故状态变更一律走 {@code LambdaUpdateWrapper} 显式 {@code set(..., null)} 并显式回填
 *       {@code updatedAt}（{@code update(null, wrapper)} 不触发 MetaObjectHandler）。</li>
 *   <li>自定义 {@code @Insert INSERT IGNORE} 不触发 MetaObjectHandler，雪花 id 与审计字段由本类显式赋值。</li>
 *   <li>「进行中/回读/幂等」查询全部带 {@code deleted = 0}（{@code @TableLogic} 自动追加）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChewServiceImpl implements ChewService {

    private final ChewSessionMapper chewSessionMapper;
    private final AlignerService alignerService;
    private final AlignerMapper alignerMapper;
    private final ChewQueryService chewQueryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChewSessionVO start(Long userId, ChewStartRequest req) {
        LocalDateTime now = ChewTimes.now();

        // 幂等：同用户已有进行中会话 → 直接返回既有
        ChewSession running = findRunning(userId);
        if (running != null) {
            return toVO(running, now);
        }

        int target = resolveTarget(req == null ? null : req.targetSec());
        Aligner active = alignerService.findActiveAligner(userId, req == null ? null : req.mode());

        ChewSession s = new ChewSession();
        s.setId(IdWorker.getId());
        s.setUserId(userId);
        s.setStageId(active == null ? null : active.getStageId());
        s.setAlignerId(active == null ? null : active.getId());
        s.setStartedAt(now);
        s.setEndedAt(null);
        s.setPauseSec(0);
        s.setPausedAt(null);
        s.setTargetSec(target);
        s.setSource(ChewSourceEnum.REALTIME.getCode());
        s.setEdited(0);
        s.setClientSessionId(req == null ? null : req.clientSessionId());
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        s.setDeleted(0);

        int rows = chewSessionMapper.insertIgnore(s);
        if (rows == 0) {
            // 唯一键冲突（同 clientSessionId 或并发）：回读进行中会话返回
            ChewSession again = findRunning(userId);
            if (again != null) {
                return toVO(again, ChewTimes.now());
            }
            throw new BusinessException(ErrorCode.CHEW_SESSION_ALREADY_RUNNING);
        }
        return toVO(s, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChewSessionVO pause(Long userId, Long id, ChewStateChangeRequest req) {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = requireOwned(userId, id);
        if (s.getEndedAt() != null) {
            throw new BusinessException(ErrorCode.CHEW_SESSION_FORBIDDEN, "已结束的咬胶记录不能暂停");
        }
        if (s.getPausedAt() != null) {
            // 已暂停 → 幂等原样返回（不改变暂停起点）
            return toVO(s, now);
        }
        Integer target = applyTarget(s, req);
        s.setPausedAt(now);
        LambdaUpdateWrapper<ChewSession> w = new LambdaUpdateWrapper<ChewSession>()
                .eq(ChewSession::getId, id)
                .set(ChewSession::getPausedAt, now)
                .set(ChewSession::getUpdatedAt, now);
        if (target != null) {
            w.set(ChewSession::getTargetSec, target);
        }
        chewSessionMapper.update(null, w);
        return toVO(s, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChewSessionVO resume(Long userId, Long id, ChewStateChangeRequest req) {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = requireOwned(userId, id);
        if (s.getEndedAt() != null) {
            throw new BusinessException(ErrorCode.CHEW_SESSION_FORBIDDEN, "已结束的咬胶记录不能继续");
        }
        if (s.getPausedAt() == null) {
            // 非暂停 → 幂等原样返回
            return toVO(s, now);
        }
        Integer target = applyTarget(s, req);
        int pauseSec = nz(s.getPauseSec()) + ChewMath.secondsBetween(s.getPausedAt(), now);
        s.setPauseSec(pauseSec);
        s.setPausedAt(null);
        LambdaUpdateWrapper<ChewSession> w = new LambdaUpdateWrapper<ChewSession>()
                .eq(ChewSession::getId, id)
                .set(ChewSession::getPauseSec, pauseSec)
                .set(ChewSession::getPausedAt, null)
                .set(ChewSession::getUpdatedAt, now);
        if (target != null) {
            w.set(ChewSession::getTargetSec, target);
        }
        chewSessionMapper.update(null, w);
        return toVO(s, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChewSessionVO restart(Long userId, Long id, ChewStateChangeRequest req) {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = requireOwned(userId, id);
        if (s.getEndedAt() != null) {
            throw new BusinessException(ErrorCode.CHEW_SESSION_FORBIDDEN, "已结束的咬胶记录不能重来");
        }
        Integer target = applyTarget(s, req);
        // 复位同一行：保留原 stage/aligner 快照，起点归零
        s.setStartedAt(now);
        s.setPauseSec(0);
        s.setPausedAt(null);
        LambdaUpdateWrapper<ChewSession> w = new LambdaUpdateWrapper<ChewSession>()
                .eq(ChewSession::getId, id)
                .set(ChewSession::getStartedAt, now)
                .set(ChewSession::getPauseSec, 0)
                .set(ChewSession::getPausedAt, null)
                .set(ChewSession::getUpdatedAt, now);
        if (target != null) {
            w.set(ChewSession::getTargetSec, target);
        }
        chewSessionMapper.update(null, w);
        return toVO(s, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChewSessionVO finish(Long userId, Long id, ChewStateChangeRequest req) {
        LocalDateTime now = ChewTimes.now();
        ChewSession s = requireOwned(userId, id);
        if (s.getEndedAt() != null) {
            // 已结束 → 幂等返回既有，不覆盖 endedAt
            return toVO(s, now);
        }
        Integer target = applyTarget(s, req);
        int pauseSec = nz(s.getPauseSec());
        if (s.getPausedAt() != null) {
            // 结束前结算未结暂停段
            pauseSec += ChewMath.secondsBetween(s.getPausedAt(), now);
        }
        s.setPauseSec(pauseSec);
        s.setPausedAt(null);
        s.setEndedAt(now);

        if (ChewMath.netSec(s, now) < ChewConstants.MIN_VALID_NET_SEC) {
            // 误触：净时长 < 5s 视为无效，逻辑删除（返回 null）
            chewSessionMapper.deleteById(id);
            return null;
        }
        LambdaUpdateWrapper<ChewSession> w = new LambdaUpdateWrapper<ChewSession>()
                .eq(ChewSession::getId, id)
                .set(ChewSession::getPauseSec, pauseSec)
                .set(ChewSession::getPausedAt, null)
                .set(ChewSession::getEndedAt, now)
                .set(ChewSession::getUpdatedAt, now);
        if (target != null) {
            w.set(ChewSession::getTargetSec, target);
        }
        chewSessionMapper.update(null, w);
        return toVO(s, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TodayChewVO discard(Long userId, Long id) {
        ChewSession s = chewSessionMapper.selectById(id);
        if (s == null || !userId.equals(s.getUserId())) {
            // 不存在或非本人：不区分、不泄露
            throw new BusinessException(ErrorCode.CHEW_SESSION_NOT_FOUND);
        }
        if (s.getEndedAt() != null) {
            throw new BusinessException(ErrorCode.CHEW_SESSION_FORBIDDEN, "已结束的记录不能丢弃");
        }
        chewSessionMapper.deleteById(id);
        // 丢弃即时回显今日累计（此时进行中已消失）
        return chewQueryService.today(userId);
    }

    /** 当前进行中会话：依赖 {@code @TableLogic} 自动追加 {@code deleted = 0}。 */
    private ChewSession findRunning(Long userId) {
        return chewSessionMapper.selectOne(new LambdaQueryWrapper<ChewSession>()
                .eq(ChewSession::getUserId, userId)
                .isNull(ChewSession::getEndedAt)
                .orderByDesc(ChewSession::getStartedAt)
                .last("LIMIT 1"));
    }

    /** 归属校验：不存在或非本人统一抛 {@code CHEW_SESSION_NOT_FOUND}。 */
    private ChewSession requireOwned(Long userId, Long id) {
        ChewSession s = chewSessionMapper.selectById(id);
        if (s == null || !userId.equals(s.getUserId())) {
            throw new BusinessException(ErrorCode.CHEW_SESSION_NOT_FOUND);
        }
        return s;
    }

    /** 目标调整：请求提供时校验并写入实体，返回新值（无变化/未提供返回 null）。 */
    private Integer applyTarget(ChewSession s, ChewStateChangeRequest req) {
        if (req == null || req.targetSec() == null) {
            return null;
        }
        int target = resolveTarget(req.targetSec());
        if (target == ChewVos.targetOf(s)) {
            return null;
        }
        s.setTargetSec(target);
        return target;
    }

    /** 目标时长校验：null → 默认档；越界抛 {@code CHEW_PARAM_INVALID}。 */
    private int resolveTarget(Integer targetSec) {
        if (targetSec == null) {
            return ChewConstants.DEFAULT_TARGET_SEC;
        }
        if (targetSec < ChewConstants.MIN_TARGET_SEC || targetSec > ChewConstants.MAX_TARGET_SEC) {
            throw new BusinessException(ErrorCode.CHEW_PARAM_INVALID,
                    "目标时长需在 " + ChewConstants.MIN_TARGET_SEC + "–" + ChewConstants.MAX_TARGET_SEC + " 秒之间");
        }
        return targetSec;
    }

    private ChewSessionVO toVO(ChewSession s, LocalDateTime now) {
        return ChewVos.toVO(s, now, labelOf(s));
    }

    private String labelOf(ChewSession s) {
        if (s == null || s.getAlignerId() == null) {
            return "";
        }
        Aligner aligner = alignerMapper.selectById(s.getAlignerId());
        LocalDate startedDate = s.getStartedAt() == null ? null : s.getStartedAt().toLocalDate();
        return ChewLabels.of(aligner, startedDate);
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
