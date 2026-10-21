package com.chiji.module.stage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Aligner;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.Stage;
import com.chiji.entity.Tag;
import com.chiji.entity.TimelineRecord;
import com.chiji.entity.User;
import com.chiji.enums.RecordBadgeTypeEnum;
import com.chiji.enums.RecordKindEnum;
import com.chiji.enums.RecordModeEnum;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.stage.dto.CreateRecordRequest;
import com.chiji.module.stage.dto.MediaItem;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.mapper.TagMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.stage.service.RecordService;
import com.chiji.module.stage.support.TreatmentGateSupport;
import com.chiji.module.stage.vo.MediaItemVO;
import com.chiji.module.stage.vo.RecordVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 时光轴记录服务实现。
 * <p>
 * 保存记录时同步处理标签与媒体：
 * - 标签：系统预设标签直接关联，自定义标签自动创建后关联；
 * - 媒体：按 medias 列表写入 record_media，并据此计算 mediaKind（TEXT/IMAGE/VIDEO/MIXED）。
 * mediaKind 与 record_media.type 共同构成结构化多模态记录，为后续 AI 上下文调用做数据准备。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordServiceImpl implements RecordService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 记录正文最短字数（与前端「至少 3 个字」口径一致）。 */
    private static final int TEXT_MIN = 3;

    /** 记录正文最长字数（疼痛记录备注同限）。 */
    private static final int TEXT_MAX = 300;

    /** 疼痛程度取值上限（0-5，REQ-FB-007）。 */
    private static final int PAIN_SCORE_MAX = 5;

    /** 疼痛部位单标签最长字数（预设标签+自定义 ≤20 字）。 */
    private static final int PAIN_PART_MAX = 20;

    private final TimelineRecordMapper timelineRecordMapper;
    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;
    private final TagMapper tagMapper;
    private final RecordMediaMapper recordMediaMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordVO createRecord(Long userId, CreateRecordRequest req) {
        // 1. 校验阶段归属
        if (req.stageId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择记录归属的阶段");
        }
        Stage stage = stageMapper.selectById(req.stageId());
        if (stage == null || !userId.equals(stage.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该阶段");
        }

        // 2. 校验牙套副归属（如果传了 alignerId）
        Long alignerId = req.alignerId();
        Aligner aligner = null;
        if (alignerId != null) {
            aligner = alignerMapper.selectById(alignerId);
            if (aligner == null || !req.stageId().equals(aligner.getStageId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "牙套副不属于该阶段");
            }
        }

        // 3. 疼痛记录判定与校验（REQ-FB-007：painScore/painPart 任一携带即判定为疼痛记录）
        boolean isPain = isPainRequest(req);
        Integer painScore = null;
        List<String> painPart = null;
        String text = req.text();
        if (isPain) {
            // 第 2 批回补放开点：若疼痛开放给隐形用户，仅需调整本处门控调用
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            TreatmentGateSupport.assertFixedBracket(user.getTreatmentType());
            painScore = assertPainFields(req);
            painPart = normalizePainPart(req.painPart());
            // 互斥：疼痛记录禁传佩戴时长（badge 与 wearHours 同构互斥）
            if (req.wearHours() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "疼痛记录不支持同时记录佩戴时长");
            }
            // 正文（备注）可空；空时派生中性摘要供时光轴 snippet 兜底
            if (text == null || text.isBlank()) {
                text = buildPainSummary(painScore, painPart);
            } else {
                assertTextLength(text);
            }
        } else {
            // 普通记录：与原 DTO @NotBlank/@Size 等价的强校验（隐形路径行为不变，BE-FB-06）
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.RECORD_TEXT_REQUIRED);
            }
            if (text.length() < TEXT_MIN) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "正文3-300字");
            }
            assertTextLength(text);
        }

        // 4. 计算媒体类型汇总（无媒体=TEXT，全图=IMAGE，全视频=VIDEO，混合=MIXED）
        List<MediaItem> medias = req.medias() != null ? req.medias() : Collections.emptyList();
        RecordKindEnum mediaKind = computeMediaKind(medias);

        // 5. 构建记录实体
        TimelineRecord record = new TimelineRecord();
        record.setUserId(userId);
        record.setStageId(req.stageId());
        record.setAlignerId(alignerId);
        record.setText(text);
        record.setRecordDate(LocalDate.now());
        record.setWearHours(req.wearHours());
        record.setMediaKind(mediaKind.getCode());
        if (isPain) {
            record.setBadgeType(RecordBadgeTypeEnum.PAIN.getCode());
            record.setPainScore(painScore);
            record.setPainPart(toJsonArray(painPart));
        }
        // 其余 badgeType（FOLLOW_UP/MILESTONE/STAGE_SWITCH）由对应链路写入，本接口不开放设置

        timelineRecordMapper.insert(record);
        log.info("记录已保存, userId={}, recordId={}, stageId={}, alignerId={}, mediaKind={}, isPain={}",
                userId, record.getId(), req.stageId(), alignerId, mediaKind.getCode(), isPain);

        // 6. 处理标签：查找/创建并关联，返回去重后的标签名列表供 VO 展示
        List<String> tagNames = req.tags() != null ? req.tags() : Collections.emptyList();
        List<String> savedTagNames = saveTags(userId, record.getId(), tagNames);

        // 7. 保存媒体（写入 record_media，独立 id 主键，BaseMapper.insert 自动填充审计字段）
        List<MediaItemVO> mediaVOs = saveMedias(record.getId(), medias);

        // 8. 返回 VO
        return RecordVO.builder()
                .id(record.getId())
                .stageId(record.getStageId())
                .alignerId(record.getAlignerId())
                .stageName(stage.getName())
                .alignerNum(aligner != null ? aligner.getNum() : null)
                .mediaKind(record.getMediaKind())
                .text(record.getText())
                .recordDate(record.getRecordDate())
                .createdAt(record.getCreatedAt())
                .wearHours(record.getWearHours())
                .badgeType(record.getBadgeType())
                .painScore(record.getPainScore())
                .painPart(painPart)
                .tags(savedTagNames)
                .medias(mediaVOs)
                .mediaUrls(mediaVOs.stream().map(MediaItemVO::getUrl).toList())
                .build();
    }

    @Override
    public List<RecordVO> listByAligner(Long userId, Long alignerId) {
        // 校验牙套副归属
        Aligner aligner = alignerMapper.selectById(alignerId);
        if (aligner == null) {
            return Collections.emptyList();
        }
        Stage stage = stageMapper.selectById(aligner.getStageId());
        if (stage == null || !userId.equals(stage.getUserId())) {
            return Collections.emptyList();
        }

        List<TimelineRecord> records = timelineRecordMapper.selectList(
                new LambdaQueryWrapper<TimelineRecord>()
                        .eq(TimelineRecord::getAlignerId, alignerId)
                        .orderByDesc(TimelineRecord::getRecordDate));
        return toVOList(records);
    }

    @Override
    public List<RecordVO> listByUser(Long userId, String mode) {
        // 查询该用户全部记录（现状查询不动）
        List<TimelineRecord> records = timelineRecordMapper.selectList(
                new LambdaQueryWrapper<TimelineRecord>()
                        .eq(TimelineRecord::getUserId, userId)
                        .orderByDesc(TimelineRecord::getRecordDate));

        // mode 缺省（null/空串）= 旧客户端路径：不过滤，现状全量返回（存量零变化红线——
        // 若缺省归一 CLEAR_SINGLE，双模用户旧客户端的全量时光轴会丢失双模阶段记录）
        if (mode == null || mode.isBlank()) {
            return toVOList(records);
        }

        // 显式传 mode：归一（未知编码按 CLEAR_SINGLE，与 HomeServiceImpl 口径一致）后按 stage 归属过滤
        RecordModeEnum modeEnum = RecordModeEnum.getByCode(mode);
        String currentMode = modeEnum != null ? modeEnum.getCode() : RecordModeEnum.CLEAR_SINGLE.getCode();

        // 该用户全部 stage 的 {id → mode} 映射（含已逻辑删除 stage：删阶段不删记录，
        // 记录仍按阶段原 mode 归属过滤，评审 3.3 雷 1）
        Map<Long, String> stageModeMap = new HashMap<>();
        for (Stage s : stageMapper.selectAllModesByUserId(userId)) {
            stageModeMap.put(s.getId(), normalizeMode(s.getMode()));
        }

        // 内存过滤（评审 3.1 方案 A）：stageId 无映射的孤儿记录兜底保留可见（评审 3.3 雷 2）
        List<TimelineRecord> filtered = records.stream()
                .filter(r -> r.getStageId() == null || !stageModeMap.containsKey(r.getStageId())
                        || currentMode.equals(stageModeMap.get(r.getStageId())))
                .toList();
        return toVOList(filtered);
    }

    /**
     * 归一化阶段的模式编码：null / 未知编码按 CLEAR_SINGLE（兼容存量数据，与 HomeServiceImpl 一致）。
     */
    private String normalizeMode(String stageMode) {
        return RecordModeEnum.getByCode(stageMode) != null ? stageMode : RecordModeEnum.CLEAR_SINGLE.getCode();
    }

    @Override
    public List<RecordVO> listByStage(Long userId, Long stageId) {
        // 校验阶段归属，越权/不存在返回空列表
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null || !userId.equals(stage.getUserId())) {
            return Collections.emptyList();
        }
        List<TimelineRecord> records = timelineRecordMapper.selectList(
                new LambdaQueryWrapper<TimelineRecord>()
                        .eq(TimelineRecord::getStageId, stageId)
                        .eq(TimelineRecord::getUserId, userId)
                        .orderByDesc(TimelineRecord::getRecordDate));
        return toVOList(records);
    }

    @Override
    public RecordVO getRecord(Long userId, Long recordId) {
        // selectById 受 @TableLogic 约束，已删除记录查不到，返回「记录不存在」
        TimelineRecord record = timelineRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该记录");
        }
        // 复用列表转 VO 的批量组装（含阶段名/副号/标签/媒体），单条无 N+1
        return toVOList(List.of(record)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordVO updateRecord(Long userId, Long recordId, CreateRecordRequest req) {
        // 1. 校验记录存在与归属
        TimelineRecord record = timelineRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该记录");
        }

        // 2. 疼痛判定与校验（与 createRecord 同口径）
        boolean isPain = isPainRequest(req);
        Integer painScore = null;
        List<String> painPart = null;
        String text = req.text();
        if (isPain) {
            // 第 2 批回补放开点：若疼痛开放给隐形用户，仅需调整本处门控调用
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            TreatmentGateSupport.assertFixedBracket(user.getTreatmentType());
            painScore = assertPainFields(req);
            painPart = normalizePainPart(req.painPart());
            if (req.wearHours() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "疼痛记录不支持同时记录佩戴时长");
            }
            if (text == null || text.isBlank()) {
                text = buildPainSummary(painScore, painPart);
            } else {
                assertTextLength(text);
            }
        } else {
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.RECORD_TEXT_REQUIRED);
            }
            if (text.length() < TEXT_MIN) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "正文3-300字");
            }
            assertTextLength(text);
        }

        // 3. 计算媒体类型汇总并更新正文（阶段/牙套副保持原值不变）
        List<MediaItem> medias = req.medias() != null ? req.medias() : Collections.emptyList();
        RecordKindEnum mediaKind = computeMediaKind(medias);
        record.setText(text);
        record.setWearHours(req.wearHours());
        record.setMediaKind(mediaKind.getCode());
        record.setPainScore(painScore);
        record.setPainPart(painPart != null ? toJsonArray(painPart) : null);
        // 徽标：编辑为疼痛 → PAIN；由疼痛改回普通 → 清除 PAIN 徽标；其余徽标（里程碑等）保持不变
        if (isPain) {
            record.setBadgeType(RecordBadgeTypeEnum.PAIN.getCode());
        } else if (RecordBadgeTypeEnum.PAIN.getCode().equals(record.getBadgeType())) {
            record.setBadgeType(null);
        }
        timelineRecordMapper.updateById(record);

        // 4. 重建标签关联：先清空旧关联（联合主键无逻辑删除，物理删除），再按新标签关联
        jdbcTemplate.update("DELETE FROM record_tag_relation WHERE record_id = ?", recordId);
        List<String> tagNames = req.tags() != null ? req.tags() : Collections.emptyList();
        saveTags(userId, recordId, tagNames);

        // 5. 重建媒体：@TableLogic 逻辑删除旧媒体，再写入新媒体
        recordMediaMapper.delete(new LambdaQueryWrapper<RecordMedia>()
                .eq(RecordMedia::getRecordId, recordId));
        saveMedias(recordId, medias);

        log.info("记录已更新, userId={}, recordId={}, mediaKind={}", userId, recordId, mediaKind.getCode());

        // 5. 复用列表转 VO（重新读取标签与媒体），返回最新数据
        return toVOList(List.of(record)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long userId, Long recordId) {
        // selectById 受 @TableLogic 约束，已删除记录查不到，返回「记录不存在」
        TimelineRecord record = timelineRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该记录");
        }
        // @TableLogic 逻辑删除：UPDATE deleted = 1，数据可恢复
        timelineRecordMapper.deleteById(recordId);
        // 时光轴侧删联动：badge_type=ADJUSTMENT 的记录对应一条 adjustment_record，
        // 由 AdjustmentRecordService 反查并逻辑删除（评审 4.1 双向删除链路）。
        // 使用 jdbcTemplate 直查避免本类与 adjustment 模块循环依赖。
        String badge = record.getBadgeType();
        if (RecordBadgeTypeEnum.ADJUSTMENT.getCode().equals(badge)) {
            jdbcTemplate.update("UPDATE adjustment_record SET deleted = 1, updated_at = NOW() "
                    + "WHERE timeline_record_id = ? AND deleted = 0", recordId);
            log.info("复诊调整记录随时光轴联动删除, userId={}, recordId={}", userId, recordId);
        }
        log.info("记录已删除, userId={}, recordId={}", userId, recordId);
    }

    // ─────────────────────── 私有方法 ───────────────────────

    /**
     * 记录列表转 VO 列表：批量加载阶段与牙套信息，避免逐条 N+1 查询。
     */
    private List<RecordVO> toVOList(List<TimelineRecord> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Stage> stageMap = loadStageMap(records);
        Map<Long, Aligner> alignerMap = loadAlignerMap(records);
        return records.stream().map(r -> toVO(r, stageMap, alignerMap)).toList();
    }

    /**
     * 批量查询记录涉及的全部阶段，返回 id → Stage 映射。
     */
    private Map<Long, Stage> loadStageMap(List<TimelineRecord> records) {
        List<Long> stageIds = records.stream()
                .map(TimelineRecord::getStageId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (stageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return stageMapper.selectBatchIds(stageIds).stream()
                .collect(Collectors.toMap(Stage::getId, s -> s));
    }

    /**
     * 批量查询记录涉及的全部牙套副，返回 id → Aligner 映射。
     */
    private Map<Long, Aligner> loadAlignerMap(List<TimelineRecord> records) {
        List<Long> alignerIds = records.stream()
                .map(TimelineRecord::getAlignerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (alignerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return alignerMapper.selectBatchIds(alignerIds).stream()
                .collect(Collectors.toMap(Aligner::getId, a -> a));
    }

    /**
     * 保存标签关联：系统预设标签直接关联，自定义标签自动创建后关联。
     * <p>
     * 标签查找规则：先查 user_id=0 的系统预设，再查当前用户的自定义标签，
     * 都不存在则创建新标签（归属当前用户）。
     * 使用 batchInsert 批量插入关联（record_tag_relation 表联合主键无独立 id 列）。
     *
     * @return 去重后实际关联的标签名列表（保留首次出现顺序），供 VO 展示
     */
    private List<String> saveTags(Long userId, Long recordId, List<String> tagNames) {
        // 对标签名去重（保留首次出现顺序），避免重复关联触发联合主键冲突
        LinkedHashSet<String> dedupedNames = new LinkedHashSet<>();
        for (String name : tagNames) {
            String trimmed = name == null ? "" : name.trim();
            if (!trimmed.isEmpty()) {
                dedupedNames.add(trimmed);
            }
        }
        if (dedupedNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object[]> batchArgs = new ArrayList<>();
        for (String trimmed : dedupedNames) {
            // JdbcTemplate 原生查询标签 ID（完全绕过 MyBatis-Plus 实体映射）
            Long tagId = null;
            try {
                tagId = jdbcTemplate.queryForObject(
                        "SELECT id FROM tag WHERE name = ? AND (user_id = 0 OR user_id = ?) AND deleted = 0 LIMIT 1",
                        Long.class, trimmed, userId);
            } catch (Exception e) {
                // queryForObject 在查不到时抛 EmptyResultDataAccessException
            }
            log.info("查找标签: name={}, userId={}, tagId={}", trimmed, userId, tagId);

            if (tagId == null) {
                // 创建用户自定义标签
                Tag tag = new Tag();
                tag.setUserId(userId);
                tag.setName(trimmed);
                tag.setSortOrder(999);
                tagMapper.insert(tag);
                tagId = tag.getId();
                log.info("创建自定义标签, userId={}, tagName={}, tagId={}", userId, trimmed, tagId);
            }

            batchArgs.add(new Object[]{recordId, tagId});
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO record_tag_relation (record_id, tag_id) VALUES (?, ?)", batchArgs);
        }
        return new ArrayList<>(dedupedNames);
    }

    /**
     * 实体转 VO（含标签名与媒体查询，并填充阶段名/牙套副号供时光轴展示）。
     */
    private RecordVO toVO(TimelineRecord record, Map<Long, Stage> stageMap, Map<Long, Aligner> alignerMap) {
        List<String> tagNames = getTagNames(record.getId());
        List<MediaItemVO> medias = getMedias(record.getId());
        Stage stage = record.getStageId() != null ? stageMap.get(record.getStageId()) : null;
        Aligner aligner = record.getAlignerId() != null ? alignerMap.get(record.getAlignerId()) : null;
        return RecordVO.builder()
                .id(record.getId())
                .stageId(record.getStageId())
                .alignerId(record.getAlignerId())
                .stageName(stage != null ? stage.getName() : null)
                .alignerNum(aligner != null ? aligner.getNum() : null)
                .mediaKind(record.getMediaKind())
                .text(record.getText())
                .recordDate(record.getRecordDate())
                .createdAt(record.getCreatedAt())
                .wearHours(record.getWearHours())
                .badgeType(record.getBadgeType())
                .painScore(record.getPainScore())
                .painPart(fromJsonArray(record.getPainPart()))
                .tags(tagNames)
                .medias(medias)
                .mediaUrls(medias.stream().map(MediaItemVO::getUrl).toList())
                .build();
    }

    /**
     * 查询记录关联的标签名列表（JdbcTemplate 原生查询避免联合主键实体兼容问题）。
     */
    private List<String> getTagNames(Long recordId) {
        List<Long> tagIds = jdbcTemplate.queryForList(
                "SELECT tag_id FROM record_tag_relation WHERE record_id = ?",
                Long.class, recordId);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        return tags.stream().map(Tag::getName).toList();
    }

    // ─────────────────────── 疼痛记录相关（REQ-FB-007） ───────────────────────

    /**
     * 判定是否疼痛记录：painScore 或 painPart 任一携带即判定（BE-FB-06 口径）。
     */
    private boolean isPainRequest(CreateRecordRequest req) {
        return req.painScore() != null
                || (req.painPart() != null && !req.painPart().isEmpty());
    }

    /**
     * 校验疼痛字段：评分必填且 0-5（REQ-FB-007「评分必填」+ 日常用语档位）。
     *
     * @return 校验通过的疼痛程度
     */
    private Integer assertPainFields(CreateRecordRequest req) {
        Integer score = req.painScore();
        if (score == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择疼痛程度");
        }
        if (score < 0 || score > PAIN_SCORE_MAX) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "疼痛程度需在0-5之间");
        }
        return score;
    }

    /**
     * 归一化疼痛部位标签：trim、去空项、去重（保留首次出现顺序）、单标签 ≤20 字。
     */
    private List<String> normalizePainPart(List<String> painPart) {
        if (painPart == null || painPart.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String part : painPart) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > PAIN_PART_MAX) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "部位标签最多20字");
            }
            deduped.add(trimmed);
        }
        return new ArrayList<>(deduped);
    }

    /**
     * 疼痛记录正文派生摘要（备注为空时的时光轴 snippet 兜底，中性表述无诊断含义）。
     * 形如「疼痛 3/5」或「疼痛 3/5 · 部位：上排、牙龈」。
     */
    private String buildPainSummary(Integer score, List<String> parts) {
        StringBuilder sb = new StringBuilder("疼痛 ").append(score).append("/5");
        if (parts != null && !parts.isEmpty()) {
            sb.append(" · 部位：").append(String.join("、", parts));
        }
        return sb.toString();
    }

    /**
     * 字符串列表 → JSON 数组字符串（空列表返回 null）。
     */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(list);
        } catch (Exception e) {
            throw new IllegalStateException("疼痛部位序列化失败", e);
        }
    }

    /**
     * JSON 数组字符串 → 字符串列表（解析失败返回 null，调用方按可空字段兜底渲染）。
     */
    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("疼痛部位 JSON 解析失败, err={}", e.getMessage());
            return null;
        }
    }

    /**
     * 正文长度上限校验（创建/更新共用；疼痛备注与普通正文同限 300 字）。
     */
    private void assertTextLength(String text) {
        if (text.length() > TEXT_MAX) {
            throw new BusinessException(ErrorCode.RECORD_TEXT_TOO_LONG);
        }
    }

    // ─────────────────────── 媒体相关 ───────────────────────

    /**
     * 按媒体列表计算记录内容类型汇总。
     * <p>
     * 无媒体 → TEXT；全图 → IMAGE；全视频 → VIDEO；图+视频混合 → MIXED。
     */
    private RecordKindEnum computeMediaKind(List<MediaItem> medias) {
        if (medias == null || medias.isEmpty()) {
            return RecordKindEnum.TEXT;
        }
        boolean hasImage = medias.stream().anyMatch(m -> "IMAGE".equals(m.type()));
        boolean hasVideo = medias.stream().anyMatch(m -> "VIDEO".equals(m.type()));
        if (hasImage && hasVideo) {
            return RecordKindEnum.MIXED;
        }
        return hasImage ? RecordKindEnum.IMAGE : RecordKindEnum.VIDEO;
    }

    /**
     * 批量保存媒体（写入 record_media）。
     * <p>
     * record_media 表有独立雪花 id 主键，使用 BaseMapper.insert 循环插入（最多 6 条），
     * MyMetaObjectHandler 自动填充 createdAt/updatedAt/deleted。
     *
     * @return 已保存的媒体 VO 列表（含生成的 id）
     */
    private List<MediaItemVO> saveMedias(Long recordId, List<MediaItem> medias) {
        if (medias == null || medias.isEmpty()) {
            return Collections.emptyList();
        }
        List<MediaItemVO> result = new ArrayList<>(medias.size());
        for (int i = 0; i < medias.size(); i++) {
            MediaItem m = medias.get(i);
            RecordMedia media = new RecordMedia();
            media.setRecordId(recordId);
            media.setType(m.type());
            media.setUrl(m.url());
            // 图片强制 duration 为 null，对齐 record_media.duration 语义
            media.setDuration("IMAGE".equals(m.type()) ? null : m.duration());
            media.setSortOrder(m.sortOrder() != null ? m.sortOrder() : i);
            media.setViewType(m.viewType());
            recordMediaMapper.insert(media);
            result.add(MediaItemVO.builder()
                    .id(media.getId())
                    .type(media.getType())
                    .url(media.getUrl())
                    .duration(media.getDuration())
                    .sortOrder(media.getSortOrder())
                    .viewType(media.getViewType())
                    .build());
        }
        return result;
    }

    /**
     * 查询记录关联的媒体列表（按 sort_order 升序）。
     */
    private List<MediaItemVO> getMedias(Long recordId) {
        List<RecordMedia> list = recordMediaMapper.selectList(
                new LambdaQueryWrapper<RecordMedia>()
                        .eq(RecordMedia::getRecordId, recordId)
                        .orderByAsc(RecordMedia::getSortOrder));
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(m -> MediaItemVO.builder()
                        .id(m.getId())
                        .type(m.getType())
                        .url(m.getUrl())
                        .duration(m.getDuration())
                        .sortOrder(m.getSortOrder())
                        .viewType(m.getViewType())
                        .build())
                .toList();
    }
}
