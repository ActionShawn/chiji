package com.chiji.module.stage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.entity.Aligner;
import com.chiji.entity.Stage;
import com.chiji.enums.AlignerFilmEnum;
import com.chiji.enums.RecordModeEnum;
import com.chiji.enums.StageStatusEnum;
import com.chiji.module.stage.mapper.AlignerMapper;
import com.chiji.module.stage.mapper.StageMapper;
import com.chiji.module.stage.service.HomeService;
import com.chiji.module.stage.support.AlignerNodeAssembler;
import com.chiji.module.stage.support.StageModeSupport;
import com.chiji.module.stage.vo.AlignerNodeVO;
import com.chiji.module.stage.vo.HomeBannerVO;
import com.chiji.module.stage.vo.HomeSummaryVO;
import com.chiji.module.stage.vo.StageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页聚合服务实现。
 * <p>
 * 阶段状态为 {@link StageStatusEnum#ACTIVE} / {@link StageStatusEnum#ENDED} 两态：
 * 同一时间至多一个启用阶段，首页默认展示该启用阶段的牙套节点。
 * 用户切换阶段查看其它阶段节点走 {@code GET /api/stages/{id}/aligners}。
 * 数据派生规则严格对齐前端 {@code client/utils/mock.js} 与 {@code project-card.wxml} 的字段形状。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    /** 「开始日期 yyyy.MM.dd」格式。 */
    private static final DateTimeFormatter START_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA);

    /** 「上次编辑 yyyy.MM.dd HH:mm」格式（精确到时分）。 */
    private static final DateTimeFormatter EDITED_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.CHINA);

    private final StageMapper stageMapper;
    private final AlignerMapper alignerMapper;
    private final AlignerNodeAssembler alignerNodeAssembler;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public HomeSummaryVO getHomeSummary(Long userId, String mode) {
        // 记录模式即工作台：按 mode 过滤阶段，单/双模数据彼此独立；缺省按 CLEAR_SINGLE（兼容存量数据与旧客户端）
        RecordModeEnum modeEnum = RecordModeEnum.getByCode(mode);
        RecordModeEnum effective = modeEnum != null ? modeEnum : RecordModeEnum.CLEAR_SINGLE;

        // 1. 查询当前用户该模式下的全部阶段（按 sort_order 升序）
        //    存量数据 mode 为 null 的阶段视作 CLEAR_SINGLE，保证旧数据仍在单模工作台可见
        List<Stage> allStages = stageMapper.selectList(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getUserId, userId)
                .orderByAsc(Stage::getSortOrder));
        List<Stage> stages = allStages.stream()
                .filter(s -> effective.getCode().equals(normalizeMode(s.getMode())))
                .toList();
        if (stages.isEmpty()) {
            // 该模式下无阶段数据：返回空聚合，前端会显示空状态
            return HomeSummaryVO.builder()
                    .banner(emptyBanner())
                    .stages(List.of())
                    .activeStageIndex(-1)
                    .activeAlignerIndex(-1)
                    .alignerNodes(List.of())
                    .build();
        }

        // 2. 派生 StageVO 列表（含开始日期与上次编辑时间）
        List<StageVO> stageVOs = toStageVOList(stages);

        // 3. 找到启用阶段下标（ACTIVE，同一时间至多一个）
        int activeStageIndex = -1;
        Stage activeStage = null;
        for (int i = 0; i < stages.size(); i++) {
            if (StageStatusEnum.ACTIVE.name().equals(stages.get(i).getStatus())) {
                activeStageIndex = i;
                activeStage = stages.get(i);
                break;
            }
        }

        // 4. 无启用阶段：返回阶段列表与空横幅、空节点
        if (activeStage == null) {
            return HomeSummaryVO.builder()
                    .banner(emptyBanner())
                    .stages(stageVOs)
                    .activeStageIndex(activeStageIndex)
                    .activeAlignerIndex(-1)
                    .alignerNodes(List.of())
                    .build();
        }

        // 5. 查询启用阶段的牙套副（按 num 升序）
        List<Aligner> aligners = alignerMapper.selectList(new LambdaQueryWrapper<Aligner>()
                .eq(Aligner::getStageId, activeStage.getId())
                .orderByAsc(Aligner::getNum));

        // 6. 派生 AlignerNodeVO 列表 + 基于 active aligner 派生横幅
        List<AlignerNodeVO> nodes = alignerNodeAssembler.toNodeList(aligners, activeStage);
        Aligner activeAligner = aligners.stream()
                .filter(a -> "ACTIVE".equals(a.getState()))
                .findFirst()
                .orElse(null);
        HomeBannerVO banner = buildBanner(activeStage, activeAligner);

        // 7. 当前佩戴副在 nodes 中的下标（前端首页直定位当前副，无需再找）
        int activeAlignerIndex = -1;
        if (activeAligner != null) {
            for (AlignerNodeVO node : nodes) {
                if ("active".equals(node.state()) && node.id().equals(activeAligner.getId())) {
                    activeAlignerIndex = node.idx();
                    break;
                }
            }
        }

        return HomeSummaryVO.builder()
                .banner(banner)
                .stages(stageVOs)
                .activeStageIndex(activeStageIndex)
                .activeAlignerIndex(activeAlignerIndex)
                .alignerNodes(nodes)
                .build();
    }

    /**
     * 归一化阶段的模式编码：null / 未知编码按 CLEAR_SINGLE（兼容存量数据）。
     */
    private String normalizeMode(String stageMode) {
        return RecordModeEnum.getByCode(stageMode) != null ? stageMode : RecordModeEnum.CLEAR_SINGLE.getCode();
    }

    /**
     * 批量派生 StageVO。
     * <ul>
     *     <li>{@code meta}：「{count}副 · 每副{daysPerAligner}天 · 约{count*daysPerAligner}天」</li>
     *     <li>{@code status}：枚举 name() 转小写（ACTIVE→active / ENDED→ended）</li>
     *     <li>{@code startDate}：阶段开始日期（yyyy.MM.dd），取 stage.startDate，缺失回退创建日期</li>
     *     <li>{@code edited}：「上次编辑 yyyy.MM.dd HH:mm」，取该阶段下所有关联数据的最新时间</li>
     * </ul>
     */
    private List<StageVO> toStageVOList(List<Stage> stages) {
        List<Long> stageIds = stages.stream().map(Stage::getId).toList();
        // 批量查询各阶段关联数据的最新时间（牙套副 + 记录），避免逐阶段 N+1
        Map<Long, LocalDateTime> alignerLatest = latestByStage("aligner", stageIds);
        Map<Long, LocalDateTime> recordLatest = latestByStage("timeline_record", stageIds);
        return stages.stream()
                .map(s -> {
                    LocalDateTime latest = maxOf(s.getCreatedAt(), s.getUpdatedAt(),
                            alignerLatest.get(s.getId()), recordLatest.get(s.getId()));
                    return toStageVO(s, latest);
                })
                .toList();
    }

    /**
     * 查询各阶段下某张表关联数据的最新时间（stageId -> 最新时间）。
     * <p>
     * 聚合 created_at / updated_at 取较大者；自动过滤逻辑删除记录。
     *
     * @param table    表名（aligner / timeline_record）
     * @param stageIds 阶段 ID 列表
     * @return stageId -> 该阶段下关联数据的最新时间
     */
    private Map<Long, LocalDateTime> latestByStage(String table, List<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = stageIds.stream().map(v -> "?").collect(Collectors.joining(","));
        String sql = "SELECT stage_id, MAX(created_at) AS max_created, MAX(updated_at) AS max_updated "
                + "FROM " + table + " WHERE stage_id IN (" + placeholders + ") AND deleted = 0 GROUP BY stage_id";
        Map<Long, LocalDateTime> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            long stageId = rs.getLong("stage_id");
            Timestamp created = rs.getTimestamp("max_created");
            Timestamp updated = rs.getTimestamp("max_updated");
            result.put(stageId, maxOf(toLocalDateTime(created), toLocalDateTime(updated)));
        }, stageIds.toArray());
        return result;
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static LocalDateTime maxOf(LocalDateTime... times) {
        LocalDateTime max = null;
        for (LocalDateTime t : times) {
            if (t == null) {
                continue;
            }
            if (max == null || t.isAfter(max)) {
                max = t;
            }
        }
        return max;
    }

    /**
     * 派生单个 StageVO。
     * <p>
     * 开始日期优先取 {@code stage.startDate}（用户开启阶段时填写），缺失时回退阶段创建日期；
     * 上次编辑时间取传入的 {@code lastEdited}（已合并阶段、牙套副、记录的最新时间），精确到时分。
     */
    private StageVO toStageVO(Stage stage, LocalDateTime lastEdited) {
        String meta = StageModeSupport.buildStageMeta(stage);
        String status = stage.getStatus() == null ? "ended" : stage.getStatus().toLowerCase();
        LocalDate start = stage.getStartDate() != null ? stage.getStartDate()
                : (stage.getCreatedAt() != null ? stage.getCreatedAt().toLocalDate() : null);
        String startDate = start == null ? "" : start.format(START_FMT);
        String edited = lastEdited == null ? "上次编辑 -" : "上次编辑 " + lastEdited.format(EDITED_FMT);
        return StageVO.builder()
                .id(stage.getId())
                .name(stage.getName())
                .meta(meta)
                .status(status)
                .startDate(startDate)
                .edited(edited)
                .build();
    }

    /**
     * 基于 stage + active aligner 派生鼓励横幅。
     * <p>
     * 按整体进度（已完成副数 + 当前副内进度）划分为多个阶段，匹配暖心文案：
     * <ul>
     *   <li>0%     → 🌱 新旅程开始啦，慢慢来～</li>
     *   <li>＜10%  → 🌱 刚起步，每一步都算数～</li>
     *   <li>＜25%  → 🌿 渐入佳境，感觉怎么样～</li>
     *   <li>＜40%  → 🌿 稳步前进，慢慢习惯了吧～</li>
     *   <li>＜50%  → 🌸 快到一半啦，坚持很酷～</li>
     *   <li>＜60%  → 🌸 过半啦！变化悄悄发生～</li>
     *   <li>＜75%  → ✨ 后半程啦，越来越好了～</li>
     *   <li>＜90%  → ✨ 接近终点，再坚持一下～</li>
     *   <li>＜100% → 🌟 最后一程，马上到啦～</li>
     *   <li>=100% → 🎉 恭喜，这一程走完啦～</li>
     * </ul>
     * 文案控制在 12 字以内，适配单行 nowrap 显示。
     */
    private HomeBannerVO buildBanner(Stage stage, Aligner activeAligner) {
        if (activeAligner == null || stage == null) {
            return emptyBanner();
        }
        // 佩戴天数按 startDate 动态推算（today - startDate + 1），与节点派生逻辑保持一致
        Integer day = AlignerNodeAssembler.resolveCurrentDay(activeAligner, LocalDate.now());
        Integer total = activeAligner.getTotalDays();
        Integer num = activeAligner.getNum();
        // 进度分母按模式取总节点数：单模 = 总副数；双模 = 总组数 × 2（软+硬）
        int totalCount = StageModeSupport.resolveTotalNodes(stage);
        if (day == null || total == null || num == null || totalCount == 0 || total == 0) {
            return emptyBanner();
        }

        // 整体进度 = (已完成副数 + 当前副内进度) / 总副数
        int doneCount = num - 1;
        double pct = (doneCount + (double) day / total) / totalCount * 100;
        int pctInt = (int) Math.round(pct);

        // 副内剩余天数
        int remaining = Math.max(0, total - day);

        String emoji;
        String title;
        if (pctInt == 0) {
            emoji = "🌱";
            title = "新旅程开始啦，慢慢来～";
        } else if (pctInt < 10) {
            emoji = "🌱";
            title = "刚起步，每一步都算数～";
        } else if (pctInt < 25) {
            emoji = "🌿";
            title = "渐入佳境，感觉怎么样～";
        } else if (pctInt < 40) {
            emoji = "🌿";
            title = "稳步前进，慢慢习惯了吧～";
        } else if (pctInt < 50) {
            emoji = "🌸";
            title = "快到一半啦，坚持很酷～";
        } else if (pctInt < 60) {
            emoji = "🌸";
            title = "过半啦！变化悄悄发生～";
        } else if (pctInt < 75) {
            emoji = "✨";
            title = "后半程啦，越来越好了～";
        } else if (pctInt < 90) {
            emoji = "✨";
            title = "接近终点，再坚持一下～";
        } else if (pctInt < 100) {
            emoji = "🌟";
            title = "最后一程，马上到啦～";
        } else {
            emoji = "🎉";
            title = "恭喜，这一程走完啦～";
        }
        // 换副提示按膜片类型分叉：软膜到期换硬膜（同序号），硬膜/单模到期换下一副
        String sub;
        if (AlignerFilmEnum.SOFT.getCode().equals(activeAligner.getFilmType())) {
            sub = "还有" + remaining + "天就可以换硬膜了";
        } else {
            sub = "还有" + remaining + "天就可以换下一副了";
        }
        return HomeBannerVO.builder()
                .emoji(emoji)
                .title(title)
                .sub(sub)
                .build();
    }

    /**
     * 空横幅（无 active 节点时的兜底）。
     */
    private HomeBannerVO emptyBanner() {
        return HomeBannerVO.builder()
                .emoji("🌿")
                .title("开始记录你的矫正旅程吧～")
                .sub("每一副都是一小步")
                .build();
    }
}
