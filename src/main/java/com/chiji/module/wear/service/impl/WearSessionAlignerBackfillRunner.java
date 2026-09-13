package com.chiji.module.wear.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.entity.Aligner;
import com.chiji.entity.WearSession;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.wear.mapper.WearSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史佩戴会话「副归属」一次性回填（启动时执行，幂等）。
 * <p>
 * 背景：日常打卡（WEAR_ON）早期版本创建会话时未写 {@code aligner_id}，导致这部分会话
 * 在「本副」统计（/api/wear/aligner/{id}/summary，按 wear_session.aligner_id 过滤）中漏算。
 * 本回填器在每次应用启动后扫描 {@code aligner_id IS NULL} 且已有开始时间的会话，
 * 按会话开始日期跨全部阶段匹配当时佩戴的副（{@link AlignerService#findWornAligner}），
 * 匹配到则补上归属；匹配不到（当天无已排期副，如阶段未开始）保持 NULL 跳过。
 * <p>
 * 更新语句带 {@code aligner_id IS NULL} 条件，重复执行不会覆盖新数据；
 * 无孤儿会话时直接返回，不产生任何查询之外的开销。
 * <p>
 * 位于 service 层：直接使用 {@code WearSessionMapper} 属分层守护测试（ArchUnit
 * {@code module_mapper_only_accessed_by_service_layer}）允许的范围，mapper 仅可由 service 层访问。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class WearSessionAlignerBackfillRunner implements ApplicationRunner {

    private final WearSessionMapper wearSessionMapper;
    private final AlignerService alignerService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill();
        } catch (Exception e) {
            // 回填失败不阻断启动：仅记录日志，下次启动会再次尝试
            log.warn("历史佩戴会话副归属回填失败，将在下次启动重试", e);
        }
    }

    private void backfill() {
        List<WearSession> orphans = wearSessionMapper.selectList(new LambdaQueryWrapper<WearSession>()
                .isNull(WearSession::getAlignerId)
                .isNotNull(WearSession::getStartedAt));
        if (orphans == null || orphans.isEmpty()) {
            return;
        }
        int fixed = 0;
        int skipped = 0;
        for (WearSession s : orphans) {
            // 历史会话无模式上下文，mode 传 null：跨全部阶段 best-effort 归属
            Aligner worn = alignerService.findWornAligner(s.getUserId(), s.getStartedAt().toLocalDate(), null);
            if (worn == null) {
                skipped++;
                continue;
            }
            // isNull 兜底条件：并发/重复执行时不覆盖已写入的归属
            int rows = wearSessionMapper.update(null, new LambdaUpdateWrapper<WearSession>()
                    .eq(WearSession::getId, s.getId())
                    .isNull(WearSession::getAlignerId)
                    .set(WearSession::getAlignerId, worn.getId()));
            if (rows > 0) {
                fixed++;
            }
        }
        log.info("历史佩戴会话副归属回填完成, 孤儿会话={}, 已回填={}, 无匹配跳过={}", orphans.size(), fixed, skipped);
    }
}
