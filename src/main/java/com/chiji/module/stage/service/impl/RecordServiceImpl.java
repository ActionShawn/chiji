package com.chiji.module.stage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.Aligner;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.Stage;
import com.chiji.entity.Tag;
import com.chiji.entity.TimelineRecord;
import com.chiji.enums.RecordKindEnum;
import com.chiji.module.stage.dto.CreateRecordRequest;
import com.chiji.module.stage.dto.MediaItem;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.mapper.TagMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.stage.service.RecordService;
import com.chiji.module.stage.vo.MediaItemVO;
import com.chiji.module.stage.vo.RecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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

    private final TimelineRecordMapper timelineRecordMapper;
    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;
    private final TagMapper tagMapper;
    private final RecordMediaMapper recordMediaMapper;
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

        // 3. 计算媒体类型汇总（无媒体=TEXT，全图=IMAGE，全视频=VIDEO，混合=MIXED）
        List<MediaItem> medias = req.medias() != null ? req.medias() : Collections.emptyList();
        RecordKindEnum mediaKind = computeMediaKind(medias);

        // 4. 构建记录实体
        TimelineRecord record = new TimelineRecord();
        record.setUserId(userId);
        record.setStageId(req.stageId());
        record.setAlignerId(alignerId);
        record.setText(req.text());
        record.setRecordDate(LocalDate.now());
        record.setWearHours(req.wearHours());
        record.setMediaKind(mediaKind.getCode());
        // badgeType 首期由标签语义推断，暂不开放直接设置

        timelineRecordMapper.insert(record);
        log.info("记录已保存, userId={}, recordId={}, stageId={}, alignerId={}, mediaKind={}",
                userId, record.getId(), req.stageId(), alignerId, mediaKind.getCode());

        // 5. 处理标签：查找/创建并关联，返回去重后的标签名列表供 VO 展示
        List<String> tagNames = req.tags() != null ? req.tags() : Collections.emptyList();
        List<String> savedTagNames = saveTags(userId, record.getId(), tagNames);

        // 6. 保存媒体（写入 record_media，独立 id 主键，BaseMapper.insert 自动填充审计字段）
        List<MediaItemVO> mediaVOs = saveMedias(record.getId(), medias);

        // 7. 返回 VO
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
    public List<RecordVO> listByUser(Long userId) {
        List<TimelineRecord> records = timelineRecordMapper.selectList(
                new LambdaQueryWrapper<TimelineRecord>()
                        .eq(TimelineRecord::getUserId, userId)
                        .orderByDesc(TimelineRecord::getRecordDate));
        return toVOList(records);
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
            recordMediaMapper.insert(media);
            result.add(MediaItemVO.builder()
                    .id(media.getId())
                    .type(media.getType())
                    .url(media.getUrl())
                    .duration(media.getDuration())
                    .sortOrder(media.getSortOrder())
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
                        .build())
                .toList();
    }
}
