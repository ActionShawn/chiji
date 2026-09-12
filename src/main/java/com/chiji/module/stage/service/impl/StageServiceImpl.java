package com.chiji.module.stage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.enums.RecordModeEnum;
import com.chiji.enums.StageStatusEnum;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.module.message.service.ProgressReminderService;
import com.chiji.module.stage.dto.CreateStageRequest;
import com.chiji.module.stage.dto.UpdateStageRequest;
import com.chiji.module.stage.dto.UpdateStageStatusRequest;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.stage.service.StageService;
import com.chiji.module.stage.service.assembler.AlignerNodeAssembler;
import com.chiji.module.stage.support.StageModeSupport;
import com.chiji.module.stage.vo.AlignerNodeVO;
import com.chiji.module.stage.vo.StageDetailVO;
import com.chiji.module.stage.vo.StageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 矫正阶段服务实现。
 * <p>
 * 阶段状态为两态：同一记录模式下至多一个 {@link StageStatusEnum#ACTIVE} 阶段（模式即工作台，
 * 单模 / 双模的启用阶段互不影响），新建或启用阶段时自动把同模式旧启用阶段置为 {@link StageStatusEnum#ENDED}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageServiceImpl implements StageService {

    /** 「上次编辑 MM.dd HH:mm」格式，与 HomeServiceImpl 保持一致。 */
    private static final DateTimeFormatter EDITED_FMT = DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.CHINA);

    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;
    private final AlignerNodeAssembler alignerNodeAssembler;
    private final AlignerService alignerService;
    private final ProgressReminderService progressReminderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StageVO createStage(Long userId, CreateStageRequest request) {
        // 解析记录模式：缺省 CLEAR_SINGLE（兼容旧客户端），非法编码直接拒绝
        RecordModeEnum mode = RecordModeEnum.getByCode(request.mode());
        if (request.mode() != null && !request.mode().isBlank() && mode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未知的记录模式");
        }
        if (mode == null) {
            mode = RecordModeEnum.CLEAR_SINGLE;
        }
        // 按模式校验天数：单模要 days，双模要 softDays/hardDays（品牌周期不同，由用户按医嘱填写）
        if (mode.isDual()) {
            if (request.softDays() == null || request.hardDays() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "双模模式需要分别填写软膜与硬膜佩戴天数");
            }
        } else if (request.days() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写每副佩戴天数");
        }

        // 当前起始副：缺省第 1 副；双模总节点数 = 组数 × 2
        int totalNodes = mode.isDual() ? request.count() * 2 : request.count();
        int startNum = request.startAlignerNum() == null ? 1 : request.startAlignerNum();
        if (startNum < 1 || startNum > totalNodes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "起始副需在 1-" + totalNodes + " 之间");
        }

        // 同一时间至多一个启用阶段（按记录模式隔离）：先把当前用户该模式下的 ACTIVE 阶段置为 ENDED
        endActiveStages(userId, mode.getCode());

        // 排序权重：追加到当前用户阶段列表末尾
        Integer maxSort = stageMapper.selectMaxSortOrder(userId);
        int sortOrder = (maxSort == null ? 0 : maxSort + 1);

        Stage stage = new Stage();
        stage.setUserId(userId);
        stage.setMode(mode.getCode());
        stage.setName(request.name());
        stage.setCount(request.count());
        stage.setDaysPerAligner(mode.isDual() ? null : request.days());
        stage.setSoftDays(mode.isDual() ? request.softDays() : null);
        stage.setHardDays(mode.isDual() ? request.hardDays() : null);
        stage.setStartDate(parseStartDate(request.startDate()));
        stage.setStatus(StageStatusEnum.ACTIVE.getCode());
        stage.setSortOrder(sortOrder);
        stageMapper.insert(stage);

        // 批量生成牙套副节点：单模 N 副 / 双模 N 组（软+硬 2N 个节点），第 startNum 个节点 ACTIVE（之前 DONE），按 startDate 排期
        alignerService.batchCreateAligners(stage, startNum);

        log.info("创建阶段成功, userId={}, stageId={}, mode={}, name={}, 旧启用阶段已结束, count={}, startNum={}",
                userId, stage.getId(), mode.getCode(), stage.getName(), stage.getCount(), startNum);
        return toStageVO(stage);
    }

    @Override
    public List<AlignerNodeVO> getStageAligners(Long userId, Long stageId) {
        // 校验阶段归属，防止越权查看他人阶段
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getId, stageId)
                .eq(Stage::getUserId, userId));
        if (stage == null) {
            return List.of();
        }
        List<Aligner> aligners = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stageId)
                .orderByAsc(Aligner::getNum));
        return alignerNodeAssembler.toNodeList(aligners, stage);
    }

    @Override
    public StageDetailVO getStageDetail(Long userId, Long stageId) {
        Stage stage = loadOwnedStage(userId, stageId);
        return toStageDetailVO(stage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StageDetailVO updateStage(Long userId, Long stageId, UpdateStageRequest request) {
        Stage stage = loadOwnedStage(userId, stageId);

        // 记录模式创建后不可改，天数按阶段当前模式校验
        boolean dual = RecordModeEnum.CLEAR_DUAL.getCode().equals(stage.getMode());
        if (dual) {
            if (request.softDays() == null || request.hardDays() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "双模模式需要分别填写软膜与硬膜佩戴天数");
            }
        } else if (request.days() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写每副佩戴天数");
        }

        // 起始副范围：双模总节点 = 组数 × 2
        int totalNodes = dual ? request.count() * 2 : request.count();
        int startNum = request.startAlignerNum() == null ? 1 : request.startAlignerNum();
        if (startNum < 1 || startNum > totalNodes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "起始副需在 1-" + totalNodes + " 之间");
        }

        stage.setName(request.name());
        stage.setCount(request.count());
        stage.setDaysPerAligner(dual ? null : request.days());
        stage.setSoftDays(dual ? request.softDays() : null);
        stage.setHardDays(dual ? request.hardDays() : null);
        stage.setStartDate(parseStartDate(request.startDate()));
        stageMapper.updateById(stage);

        // 对齐节点：补/删副、状态重排、统一排期（缩量时尾部有记录会抛业务异常回滚）
        alignerService.reconcileAligners(stage, startNum);

        log.info("编辑阶段成功, userId={}, stageId={}, count={}, startNum={}, hasStartDate={}",
                userId, stageId, request.count(), startNum, stage.getStartDate() != null);
        return toStageDetailVO(stage);
    }

    /**
     * 按归属加载阶段，不存在或越权抛 STAGE_NOT_FOUND。
     */
    private Stage loadOwnedStage(Long userId, Long stageId) {
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getId, stageId)
                .eq(Stage::getUserId, userId));
        if (stage == null) {
            throw new BusinessException(ErrorCode.STAGE_NOT_FOUND);
        }
        return stage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StageVO updateStageStatus(Long userId, Long stageId, UpdateStageStatusRequest request) {
        // 校验阶段归属
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getId, stageId)
                .eq(Stage::getUserId, userId));
        if (stage == null) {
            throw new BusinessException(ErrorCode.STAGE_NOT_FOUND);
        }

        String targetStatus = request.status().toUpperCase();
        // 状态未变化：直接返回
        if (targetStatus.equals(stage.getStatus())) {
            return toStageVO(stage);
        }

        // 切到 ACTIVE：先把当前用户该模式下其它 ACTIVE 阶段置为 ENDED，保持「同模式内唯一启用」
        // （模式即工作台，单模 / 双模的启用阶段互不影响）
        boolean wasActive = StageStatusEnum.ACTIVE.getCode().equals(stage.getStatus());
        if (StageStatusEnum.ACTIVE.getCode().equals(targetStatus)) {
            endActiveStages(userId, stage.getMode() != null ? stage.getMode() : RecordModeEnum.CLEAR_SINGLE.getCode());
        }

        stageMapper.update(null, new LambdaUpdateWrapper<Stage>()
                .eq(Stage::getId, stageId)
                .set(Stage::getStatus, targetStatus));

        stage.setStatus(targetStatus);
        log.info("更新阶段状态, userId={}, stageId={}, {}->{}", userId, stageId, targetStatus, stage.getStatus());

        // 显式结束启用阶段（至少走完一副）时归档「阶段结束」里程碑；自然戴完走换副路径，同日去重兜底
        if (wasActive && StageStatusEnum.ENDED.getCode().equals(targetStatus)) {
            try {
                progressReminderService.stageEndedMilestone(userId, stageId);
            } catch (Exception e) {
                log.warn("阶段结束里程碑消息归档失败, userId={}, stageId={}", userId, stageId, e);
            }
        }
        return toStageVO(stage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStage(Long userId, Long stageId) {
        // 校验阶段归属
        Stage stage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getId, stageId)
                .eq(Stage::getUserId, userId));
        if (stage == null) {
            throw new BusinessException(ErrorCode.STAGE_NOT_FOUND);
        }

        // 逻辑删除阶段
        stageMapper.delete(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getId, stageId)
                .eq(Stage::getUserId, userId));
        // 连带逻辑删除其下所有 aligner（数据保留可恢复，查询时自动过滤）
        alignerMapper.logicDeleteByStageId(stageId);

        log.info("删除阶段(逻辑删除), userId={}, stageId={}, name={}", userId, stageId, stage.getName());
    }

    /**
     * 把当前用户指定记录模式下所有 ACTIVE 阶段置为 ENDED（同模式内唯一启用，跨模式互不影响）。
     */
    private void endActiveStages(Long userId, String mode) {
        int updated = stageMapper.update(null, new LambdaUpdateWrapper<Stage>()
                .eq(Stage::getUserId, userId)
                .eq(Stage::getStatus, StageStatusEnum.ACTIVE.getCode())
                .eq(Stage::getMode, mode)
                .set(Stage::getStatus, StageStatusEnum.ENDED.getCode()));
        if (updated > 0) {
            log.info("结束旧启用阶段, userId={}, mode={}, count={}", userId, mode, updated);
        }
    }

    /**
     * 解析开始日期，支持 ISO yyyy-MM-dd；无法解析或为空时返回 null。
     */
    private LocalDate parseStartDate(String startDate) {
        if (startDate == null || startDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(startDate.trim());
        } catch (Exception e) {
            log.warn("开始日期格式无法解析，已忽略: {}", startDate);
            return null;
        }
    }

    /**
     * 派生 StageVO，meta/edited 规则与 HomeServiceImpl.toStageVO 保持一致。
     */
    private StageVO toStageVO(Stage stage) {
        String meta = StageModeSupport.buildStageMeta(stage);
        String status = stage.getStatus() == null ? "ended" : stage.getStatus().toLowerCase();
        LocalDateTime updatedAt = stage.getUpdatedAt() != null ? stage.getUpdatedAt() : LocalDateTime.now();
        String edited = "上次编辑 " + updatedAt.format(EDITED_FMT);
        return StageVO.builder()
                .id(stage.getId())
                .name(stage.getName())
                .meta(meta)
                .status(status)
                .edited(edited)
                .build();
    }

    /**
     * 派生 StageDetailVO（编辑表单回显）。startAlignerNum 取阶段内 ACTIVE 副 num，无则回退 1。
     */
    private StageDetailVO toStageDetailVO(Stage stage) {
        Integer startNum = 1;
        Aligner active = alignerMapper.selectOne(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, stage.getId())
                .eq(Aligner::getState, AlignerStateEnum.ACTIVE.getCode())
                .orderByAsc(Aligner::getNum)
                .last("limit 1"));
        if (active != null) {
            startNum = active.getNum();
        }
        return StageDetailVO.builder()
                .id(stage.getId())
                .name(stage.getName())
                .mode(stage.getMode())
                .count(stage.getCount())
                .days(stage.getDaysPerAligner())
                .softDays(stage.getSoftDays())
                .hardDays(stage.getHardDays())
                .startDate(stage.getStartDate() == null ? null : stage.getStartDate().toString())
                .startAlignerNum(startNum)
                .build();
    }
}
