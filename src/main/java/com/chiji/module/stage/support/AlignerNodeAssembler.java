package com.chiji.module.stage.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.Aligner;
import com.chiji.entity.RecordMedia;
import com.chiji.entity.Stage;
import com.chiji.entity.TimelineRecord;
import com.chiji.enums.AlignerFilmEnum;
import com.chiji.enums.AlignerStateEnum;
import com.chiji.module.stage.mapper.RecordMediaMapper;
import com.chiji.module.stage.mapper.TimelineRecordMapper;
import com.chiji.module.stage.vo.AlignerNodeVO;
import com.chiji.module.stage.vo.AlignerThumbVO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 牙套副 -> {@link AlignerNodeVO} 派生器。
 * <p>
 * 抽取首页聚合与按阶段查节点共用的派生逻辑，保证两处返回的节点形状一致。
 * 派生规则对齐前端 {@code project-card.wxml} 消费的字段：
 * <ul>
 *   <li>{@code label}：「第{num}副」</li>
 *   <li>{@code state}：枚举 name() 转小写（DONE→done / ACTIVE→active / FUTURE→future）</li>
 *   <li>{@code dates}：DONE/ACTIVE 时为「{start}–{end|今天}」，FUTURE 为 null</li>
 *   <li>{@code day/total/pct/progressLabel}：仅 ACTIVE 有值</li>
 *   <li>{@code thumbs}：从 timeline_record + record_media 派生，每副最多 MAX_THUMBS 条；
 *       有图片的记录展示照片缩略（photo），文字/视频记录展示文字摘要（text）</li>
 * </ul>
 */
@Component
public class AlignerNodeAssembler {

    /** 日期范围展示「MM.dd」格式。 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM.dd", Locale.CHINA);

    /** 印记缩略时间「MM.dd HH:mm」格式（精确到时分，从创建时间派生）。 */
    private static final DateTimeFormatter THUMB_TIME_FMT = DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.CHINA);

    /** 每个牙套副最多展示的印记缩略条数（首页位置狭小，横向滚动查看）。 */
    private static final int MAX_THUMBS = 6;

    /** 文字摘要最大长度（超出截断加省略号）。 */
    private static final int THUMB_TEXT_MAX = 12;

    private final TimelineRecordMapper timelineRecordMapper;
    private final RecordMediaMapper recordMediaMapper;

    public AlignerNodeAssembler(TimelineRecordMapper timelineRecordMapper,
                                RecordMediaMapper recordMediaMapper) {
        this.timelineRecordMapper = timelineRecordMapper;
        this.recordMediaMapper = recordMediaMapper;
    }

    /**
     * 批量派生节点列表（按 aligner 顺序，idx 从 0 递增；无阶段上下文，按单模派生标签）。
     *
     * @param aligners 牙套副实体列表（已按 num 升序）
     * @return 节点 VO 列表
     */
    public List<AlignerNodeVO> toNodeList(List<Aligner> aligners) {
        return toNodeList(aligners, null);
    }

    /**
     * 批量派生节点列表（携带阶段上下文：双模阶段输出软硬膜标签与组号）。
     * <p>
     * 批量加载各牙套副的印记缩略，避免逐副 N+1 查询。
     *
     * @param aligners 牙套副实体列表（已按 num 升序）
     * @param stage    所属阶段（可空，空时按单模派生）
     * @return 节点 VO 列表
     */
    public List<AlignerNodeVO> toNodeList(List<Aligner> aligners, Stage stage) {
        if (aligners == null || aligners.isEmpty()) {
            return List.of();
        }
        boolean dual = StageModeSupport.isDual(stage);
        Map<Long, List<AlignerThumbVO>> thumbsMap = loadThumbsMap(aligners);
        List<AlignerNodeVO> nodes = new ArrayList<>(aligners.size());
        for (int i = 0; i < aligners.size(); i++) {
            Aligner a = aligners.get(i);
            nodes.add(toNode(a, i, dual, thumbsMap.getOrDefault(a.getId(), List.of())));
        }
        return nodes;
    }

    /**
     * 单个牙套副派生为节点 VO（含其印记缩略，无阶段上下文按单模派生）。
     *
     * @param a   牙套副实体
     * @param idx 节点下标（从 0 递增）
     * @return 节点 VO
     */
    public AlignerNodeVO toNode(Aligner a, int idx) {
        return toNode(a, idx, false, loadThumbsMap(List.of(a)).getOrDefault(a.getId(), List.of()));
    }

    private AlignerNodeVO toNode(Aligner a, int idx, boolean dual, List<AlignerThumbVO> thumbs) {
        String state = a.getState() == null ? "future" : a.getState().toLowerCase();

        // 标签与组号：双模按组展示「第N组·软膜/硬膜」（软奇硬偶），单模「第N副」
        String film = a.getFilmType();
        AlignerFilmEnum filmEnum = AlignerFilmEnum.getByCode(film);
        int groupNum = dual ? (a.getNum() + 1) / 2 : a.getNum();
        String label;
        if (dual && filmEnum != null) {
            label = "第" + groupNum + "组·" + filmEnum.getDesc();
        } else {
            label = "第" + a.getNum() + "副";
        }

        String dates = null;
        Integer day = null;
        // total 对所有副都输出（数据库 total_days 字段都有值），供前端精确计算阶段总天数
        Integer total = a.getTotalDays();
        Integer pct = null;
        String progressLabel = null;

        AlignerStateEnum stateEnum = AlignerStateEnum.getByCode(a.getState());
        if (stateEnum != AlignerStateEnum.FUTURE) {
            // DONE 或 ACTIVE 都展示日期范围
            String start = a.getStartDate() == null ? "" : a.getStartDate().format(DATE_FMT);
            String end;
            if (stateEnum == AlignerStateEnum.ACTIVE) {
                end = "今天";
            } else {
                end = a.getEndDate() == null ? "" : a.getEndDate().format(DATE_FMT);
            }
            dates = start + "–" + end;

            // active 节点派生进度
            if (stateEnum == AlignerStateEnum.ACTIVE) {
                Integer totalDays = a.getTotalDays();
                // 佩戴天数按 startDate 动态推算（today - startDate + 1），
                // 使「昨天开始、今天即第 2 天」无需定时任务自动更新；startDate 缺失时回退 current_day。
                Integer currentDay = resolveCurrentDay(a, LocalDate.now());
                day = currentDay;
                if (currentDay != null && totalDays != null && totalDays > 0) {
                    pct = Math.round((float) currentDay / totalDays * 100);
                }
                if (currentDay != null && totalDays != null) {
                    progressLabel = "第" + currentDay + "/" + totalDays + "天";
                }
            } else if (stateEnum == AlignerStateEnum.DONE
                    && a.getStartDate() != null && a.getEndDate() != null) {
                // DONE 副输出实际佩戴天数（endDate - startDate + 1），供前端精确计算已走天数
                day = (int) ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate()) + 1;
            }
        }

        return AlignerNodeVO.builder()
                .id(a.getId())
                .idx(idx)
                .label(label)
                .num(a.getNum())
                .groupNum(groupNum)
                .filmType(film)
                .state(state)
                .startDate(a.getStartDate() == null ? null : a.getStartDate().toString())
                .endDate(a.getEndDate() == null ? null : a.getEndDate().toString())
                .dates(dates)
                .day(day)
                .total(total)
                .pct(pct)
                .progressLabel(progressLabel)
                .thumbs(thumbs)
                .build();
    }

    // ─────────────────────── 印记缩略派生 ───────────────────────

    /**
     * 批量加载各牙套副的印记缩略列表（alignerId -> thumbs）。
     * <p>
     * 一次性查出这些牙套副下的最近记录（按创建时间倒序），每副最多取 MAX_THUMBS 条；
     * 再批量查出这些记录的媒体，图片记录展示照片缩略，文字/视频记录展示文字摘要。
     */
    private Map<Long, List<AlignerThumbVO>> loadThumbsMap(List<Aligner> aligners) {
        List<Long> alignerIds = aligners.stream()
                .map(Aligner::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (alignerIds.isEmpty()) {
            return Map.of();
        }
        // selectList 受 @TableLogic 约束，自动过滤已删除记录
        List<TimelineRecord> records = timelineRecordMapper.selectList(
                new LambdaQueryWrapper<TimelineRecord>()
                        .in(TimelineRecord::getAlignerId, alignerIds)
                        .orderByDesc(TimelineRecord::getCreatedAt));
        if (records.isEmpty()) {
            return Map.of();
        }
        // 每个牙套副最多取最近 MAX_THUMBS 条
        Map<Long, List<TimelineRecord>> byAligner = new HashMap<>();
        for (TimelineRecord r : records) {
            List<TimelineRecord> list = byAligner.computeIfAbsent(r.getAlignerId(), k -> new ArrayList<>());
            if (list.size() < MAX_THUMBS) {
                list.add(r);
            }
        }
        // 批量加载这些记录下的媒体
        List<Long> recordIds = byAligner.values().stream()
                .flatMap(List::stream)
                .map(TimelineRecord::getId)
                .toList();
        Map<Long, List<RecordMedia>> mediaMap = loadMediaMap(recordIds);

        Map<Long, List<AlignerThumbVO>> result = new HashMap<>();
        byAligner.forEach((alignerId, list) ->
                result.put(alignerId, list.stream().map(r -> toThumb(r, mediaMap)).toList()));
        return result;
    }

    /**
     * 批量加载记录媒体列表（recordId -> medias，按 sortOrder 升序）。
     */
    private Map<Long, List<RecordMedia>> loadMediaMap(List<Long> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        List<RecordMedia> medias = recordMediaMapper.selectList(
                new LambdaQueryWrapper<RecordMedia>()
                        .in(RecordMedia::getRecordId, recordIds)
                        .orderByAsc(RecordMedia::getSortOrder));
        return medias.stream().collect(Collectors.groupingBy(RecordMedia::getRecordId));
    }

    /**
     * 单条记录 -> 缩略 VO。
     * <p>
     * 时间精确到时分：优先取创建时间（MM.dd HH:mm），缺失时回退记录日期（MM.dd）。
     * <b>一条记录只生成一个缩略项</b>，图文保持关联展示：
     * 记录含图片时展示照片缩略（photo），若有正文则一并携带文字摘要；
     * 无图片时按文字摘要展示（text），纯视频记录用「🎬 视频」占位。
     */
    private AlignerThumbVO toThumb(TimelineRecord r, Map<Long, List<RecordMedia>> mediaMap) {
        String date = r.getCreatedAt() != null
                ? r.getCreatedAt().format(THUMB_TIME_FMT)
                : (r.getRecordDate() == null ? "" : r.getRecordDate().format(DATE_FMT));
        List<RecordMedia> medias = mediaMap.getOrDefault(r.getId(), List.of());
        RecordMedia image = medias.stream()
                .filter(m -> "IMAGE".equals(m.getType()))
                .findFirst()
                .orElse(null);
        String text = (r.getText() == null || r.getText().isBlank())
                ? ""
                : truncate(r.getText(), THUMB_TEXT_MAX);
        if (image != null) {
            return AlignerThumbVO.builder()
                    .recordId(r.getId())
                    .type("photo")
                    .text(text)
                    .url(image.getUrl())
                    .date(date)
                    .build();
        }
        return AlignerThumbVO.builder()
                .recordId(r.getId())
                .type("text")
                .text(text.isEmpty() ? "🎬 视频" : text)
                .date(date)
                .build();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    /**
     * 解析当前佩戴天数（供 ACTIVE 副派生进度）。
     * <p>
     * 优先按 {@code startDate} 动态推算：{@code today - startDate + 1}，保底第 1 天，
     * 使「昨天开始、今天即第 2 天」随日期自动更新，无需依赖任何定时任务；
     * {@code startDate} 缺失时回退数据库 {@code current_day} 字段。
     * 不设上限，如实反映超期佩戴（如 7 天疗程第 10 天显示第 10 天）。
     *
     * @param a     牙套副实体（须为 ACTIVE 或提供 startDate）
     * @param today 参考日期
     * @return 当前佩戴天数；无法推算时返回 null
     */
    public static Integer resolveCurrentDay(Aligner a, LocalDate today) {
        if (a.getStartDate() != null && today != null) {
            long diff = ChronoUnit.DAYS.between(a.getStartDate(), today) + 1;
            return (int) Math.max(1, diff);
        }
        return a.getCurrentDay();
    }
}
