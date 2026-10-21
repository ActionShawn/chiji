package com.chiji.module.adjustment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Stage;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.User;
import com.chiji.enums.RecordBadgeTypeEnum;
import com.chiji.enums.RecordKindEnum;
import com.chiji.enums.RecordModeEnum;
import com.chiji.module.adjustment.dto.AdjustmentSaveRequest;
import com.chiji.module.adjustment.entity.AdjustmentRecord;
import com.chiji.module.adjustment.mapper.AdjustmentRecordMapper;
import com.chiji.module.adjustment.service.AdjustmentRecordService;
import com.chiji.module.adjustment.vo.AdjustmentPageVO;
import com.chiji.module.adjustment.vo.AdjustmentVO;
import com.chiji.module.adjustment.vo.FixedAdjustmentPanelVO;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.stage.support.TreatmentGateSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 复诊调整记录服务实现（固定托槽第 1 批，REQ-FB-004）。
 * <p>
 * 双写与联动设计要点（评审 4.1）：
 * <ul>
 *   <li>新增：同事务先 INSERT timeline_record(badge_type=ADJUSTMENT, mediaKind=TEXT,
 *       text=加力内容拼接摘要, recordDate=adjustDate) → 拿到 id → INSERT adjustment_record
 *       并回填 timeline_record_id</li>
 *   <li>编辑：刷新 timeline_record.text 与 recordDate，再刷新 adjustment_record 各字段</li>
 *   <li>列表侧删：先逻辑删除 timeline_record → 再逻辑删除 adjustment_record（同事务）</li>
 *   <li>时光轴侧删：由 {@link com.chiji.module.stage.service.impl.RecordServiceImpl#deleteRecord} 删除后回调
 *       {@link #deleteByTimelineRecordId(Long)} 反查并联动删除（badge_type=ADJUSTMENT 才联动）</li>
 * </ul>
 * 不触碰 aligner 与换副 reconcile，零回填。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentRecordServiceImpl implements AdjustmentRecordService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AdjustmentRecordMapper adjustmentRecordMapper;
    private final TimelineRecordMapper timelineRecordMapper;
    private final StageMapper stageMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdjustmentVO create(Long userId, AdjustmentSaveRequest req) {
        // 1. 门控（唯一事实源）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        TreatmentGateSupport.assertFixedBracket(user.getTreatmentType());

        // 2. 校验阶段归属与模式
        Stage stage = assertStageForFixedBracket(userId, req.stageId());

        // 3. 校验请求体其余字段
        LocalDate adjustDate = resolveAdjustDate(req.adjustDate());
        assertContents(req.contents(), req.contentExtra());
        Integer wireNo = req.wireNo();
        if (wireNo != null && wireNo < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "弓丝序号为正整数");
        }

        // 4. 同事务双写：先 timeline_record，再 adjustment_record
        String summary = buildSummary(req.contents(), req.contentExtra());
        TimelineRecord timeline = new TimelineRecord();
        timeline.setUserId(userId);
        timeline.setStageId(req.stageId());
        timeline.setAlignerId(null);  // 固定托槽无 aligner
        timeline.setMediaKind(RecordKindEnum.TEXT.getCode());
        timeline.setText(summary);
        timeline.setRecordDate(adjustDate);
        timeline.setBadgeType(RecordBadgeTypeEnum.ADJUSTMENT.getCode());
        timelineRecordMapper.insert(timeline);

        AdjustmentRecord record = new AdjustmentRecord();
        record.setUserId(userId);
        record.setStageId(req.stageId());
        record.setTimelineRecordId(timeline.getId());
        record.setWireNo(wireNo);
        record.setContents(toJson(req.contents()));
        record.setContentExtra(trimToNull(req.contentExtra()));
        record.setAdjustDate(adjustDate);
        record.setNote(trimToNull(req.note()));
        adjustmentRecordMapper.insert(record);

        log.info("复诊调整记录已保存, userId={}, recordId={}, timelineId={}, stageId={}, adjustDate={}",
                userId, record.getId(), timeline.getId(), req.stageId(), adjustDate);

        return toVO(record, stage.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdjustmentVO update(Long userId, Long id, AdjustmentSaveRequest req) {
        AdjustmentRecord record = assertOwnRecord(userId, id);

        // 1. 门控
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        TreatmentGateSupport.assertFixedBracket(user.getTreatmentType());

        // 2. 校验阶段归属与模式
        Stage stage = assertStageForFixedBracket(userId, req.stageId());

        // 3. 校验其余字段
        LocalDate adjustDate = resolveAdjustDate(req.adjustDate());
        assertContents(req.contents(), req.contentExtra());
        Integer wireNo = req.wireNo();
        if (wireNo != null && wireNo < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "弓丝序号为正整数");
        }

        // 4. 同事务双写刷新：timeline text/recordDate + adjustment 各字段
        String summary = buildSummary(req.contents(), req.contentExtra());
        TimelineRecord timeline = timelineRecordMapper.selectById(record.getTimelineRecordId());
        if (timeline != null) {
            timeline.setText(summary);
            timeline.setRecordDate(adjustDate);
            timelineRecordMapper.updateById(timeline);
        } else {
            // 时光轴记录已被独立删除：补建一条联动记录，保持双向数据一致
            TimelineRecord rebuilt = new TimelineRecord();
            rebuilt.setUserId(userId);
            rebuilt.setStageId(req.stageId());
            rebuilt.setMediaKind(RecordKindEnum.TEXT.getCode());
            rebuilt.setText(summary);
            rebuilt.setRecordDate(adjustDate);
            rebuilt.setBadgeType(RecordBadgeTypeEnum.ADJUSTMENT.getCode());
            timelineRecordMapper.insert(rebuilt);
            record.setTimelineRecordId(rebuilt.getId());
        }

        record.setStageId(req.stageId());
        record.setWireNo(wireNo);
        record.setContents(toJson(req.contents()));
        record.setContentExtra(trimToNull(req.contentExtra()));
        record.setAdjustDate(adjustDate);
        record.setNote(trimToNull(req.note()));
        adjustmentRecordMapper.updateById(record);

        log.info("复诊调整记录已更新, userId={}, recordId={}", userId, id);
        return toVO(record, stage.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        AdjustmentRecord record = assertOwnRecord(userId, id);

        // 列表侧删：先逻辑删除时光轴，再删自身（同事务）
        if (record.getTimelineRecordId() != null) {
            timelineRecordMapper.deleteById(record.getTimelineRecordId());
        }
        adjustmentRecordMapper.deleteById(id);
        log.info("复诊调整记录已删除, userId={}, recordId={}", userId, id);
    }

    @Override
    public AdjustmentPageVO list(Long userId, Integer page, Integer size) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size <= 0 || size > 50 ? 20 : size;
        Page<AdjustmentRecord> p = new Page<>(pageNo, pageSize);
        IPage<AdjustmentRecord> result = adjustmentRecordMapper.selectPage(p,
                new LambdaQueryWrapper<AdjustmentRecord>()
                        .eq(AdjustmentRecord::getUserId, userId)
                        .orderByDesc(AdjustmentRecord::getAdjustDate)
                        .orderByDesc(AdjustmentRecord::getCreatedAt));
        List<AdjustmentRecord> records = result.getRecords();
        if (records.isEmpty()) {
            return new AdjustmentPageVO(result.getTotal(), pageNo, pageSize, Collections.emptyList());
        }
        Map<Long, Stage> stageMap = loadStageMap(records);
        List<AdjustmentVO> items = records.stream()
                .map(r -> toVO(r, stageMap.get(r.getStageId()) != null
                        ? stageMap.get(r.getStageId()).getName() : null))
                .toList();
        return new AdjustmentPageVO(result.getTotal(), pageNo, pageSize, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByTimelineRecordId(Long timelineRecordId) {
        if (timelineRecordId == null) {
            return;
        }
        List<AdjustmentRecord> list = adjustmentRecordMapper.selectList(
                new LambdaQueryWrapper<AdjustmentRecord>()
                        .eq(AdjustmentRecord::getTimelineRecordId, timelineRecordId));
        for (AdjustmentRecord r : list) {
            adjustmentRecordMapper.deleteById(r.getId());
            log.info("复诊调整记录随时光轴联动删除, recordId={}, timelineRecordId={}",
                    r.getId(), timelineRecordId);
        }
    }

    @Override
    public FixedAdjustmentPanelVO panel(Long userId) {
        // 1. 累计调整次数（Row 5「累计调整 N 次」）
        Long total = adjustmentRecordMapper.selectCount(
                new LambdaQueryWrapper<AdjustmentRecord>().eq(AdjustmentRecord::getUserId, userId));
        if (total == null || total == 0) {
            return new FixedAdjustmentPanelVO(0L, null, Collections.emptyList());
        }

        // 2. 最近 10 条（Row 2 调整历史轴数据源，adjust_date 倒序、同日 createdAt 倒序）
        List<AdjustmentRecord> records = adjustmentRecordMapper.selectList(
                new LambdaQueryWrapper<AdjustmentRecord>()
                        .eq(AdjustmentRecord::getUserId, userId)
                        .orderByDesc(AdjustmentRecord::getAdjustDate)
                        .orderByDesc(AdjustmentRecord::getCreatedAt)
                        .last("LIMIT 10"));
        Map<Long, Stage> stageMap = loadStageMap(records);
        List<AdjustmentVO> items = records.stream()
                .map(r -> toVO(r, stageMap.get(r.getStageId()) != null
                        ? stageMap.get(r.getStageId()).getName() : null))
                .toList();

        // nextVisitDate 由 HomeController 并入小齿档案口径（本模块不跨模块依赖 clinic）
        return new FixedAdjustmentPanelVO(total, null, items);
    }

    // ───────────────────────── 私有方法 ─────────────────────────

    private Stage assertStageForFixedBracket(Long userId, Long stageId) {
        if (stageId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择记录归属的阶段");
        }
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null || !userId.equals(stage.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该阶段");
        }
        if (!RecordModeEnum.FIXED_BRACKET.getCode().equals(stage.getMode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "复诊调整仅适用于固定托槽阶段");
        }
        return stage;
    }

    private AdjustmentRecord assertOwnRecord(Long userId, Long id) {
        AdjustmentRecord record = adjustmentRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "复诊调整记录不存在");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该记录");
        }
        return record;
    }

    private void assertContents(List<String> contents, String contentExtra) {
        for (String c : contents) {
            if (!AdjustmentSaveRequest.PRESET_CONTENTS.contains(c)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "加力内容含不支持的取值：" + c);
            }
        }
        if (contents.contains(AdjustmentSaveRequest.CONTENT_EXTRA_KEY)) {
            if (contentExtra == null || contentExtra.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选择「其他」时需补充自定义内容");
            }
        }
    }

    private LocalDate resolveAdjustDate(LocalDate adjustDate) {
        LocalDate date = adjustDate != null ? adjustDate : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调整日期不能晚于今天");
        }
        return date;
    }

    private String buildSummary(List<String> contents, String contentExtra) {
        List<String> parts = new ArrayList<>(contents.size());
        for (String c : contents) {
            if (AdjustmentSaveRequest.CONTENT_EXTRA_KEY.equals(c) && contentExtra != null && !contentExtra.isBlank()) {
                parts.add("其他：" + contentExtra.trim());
            } else {
                parts.add(c);
            }
        }
        return String.join("、", parts);
    }

    private String toJson(List<String> contents) {
        try {
            return JSON.writeValueAsString(contents);
        } catch (Exception e) {
            throw new IllegalStateException("加力内容序列化失败", e);
        }
    }

    private List<String> fromJson(String contents) {
        if (contents == null || contents.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(contents, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("加力内容 JSON 解析失败, contents={}, err={}", contents, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Map<Long, Stage> loadStageMap(List<AdjustmentRecord> records) {
        List<Long> stageIds = records.stream()
                .map(AdjustmentRecord::getStageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (stageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Stage> stages = stageMapper.selectBatchIds(stageIds);
        return stages.stream()
                .collect(Collectors.toMap(Stage::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
    }

    private AdjustmentVO toVO(AdjustmentRecord record, String stageName) {
        return AdjustmentVO.builder()
                .id(record.getId())
                .stageId(record.getStageId())
                .stageName(stageName)
                .wireNo(record.getWireNo())
                .contents(fromJson(record.getContents()))
                .contentExtra(record.getContentExtra())
                .adjustDate(record.getAdjustDate())
                .note(record.getNote())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
