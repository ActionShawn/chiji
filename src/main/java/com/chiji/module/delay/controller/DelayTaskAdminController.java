package com.chiji.module.delay.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.delay.service.DelayTaskService;
import com.chiji.module.delay.vo.DelayTaskAdminVO;
import com.chiji.module.delay.vo.DelayTaskPageVO;
import com.chiji.module.delay.vo.DelayTaskStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端延迟任务运维接口。
 * <p>
 * 受 Sa-Token 登录校验 + 管理员角色校验双重保护（见 SaTokenConfig 的 /api/admin/** 规则）。
 * 仅提供观测与干预（重试/取消），无删除入口——终态行清理统一由 CleanJob 按保留期执行。
 */
@Tag(name = "管理端-延迟任务", description = "延迟队列任务列表/详情/健康度统计/手动重试/手动取消")
@RestController
@RequestMapping("/api/admin/delay-tasks")
@RequiredArgsConstructor
public class DelayTaskAdminController {

    private final DelayTaskService delayTaskService;

    /**
     * 分页筛选任务列表（投递时间倒序）。
     *
     * @param page      页码（1 起，默认 1）
     * @param size      每页条数（1-100，默认 20）
     * @param status    状态筛选：PENDING/RUNNING/DONE/CANCELLED/FAILED（可空）
     * @param taskType  任务类型筛选（可空）
     * @param owner     归属模块筛选（可空）
     * @param keyword   biz_key / summary 模糊匹配（可空）
     * @param beginTime 计划触发时间范围起（可空）
     * @param endTime   计划触发时间范围止（可空）
     */
    @Operation(summary = "任务列表", description = "按状态/类型/模块/关键字/计划触发时间范围筛选，投递时间倒序")
    @GetMapping
    public R<DelayTaskPageVO> page(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String taskType,
                                   @RequestParam(required = false) String owner,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(delayTaskService.page(page, size, status, taskType, owner, keyword, beginTime, endTime));
    }

    /**
     * 健康度统计（概览卡）。
     */
    @Operation(summary = "健康度统计", description = "五状态计数/今日投递与触发/过期积压/触发偏差/红黄绿健康度")
    @GetMapping("/stats")
    public R<DelayTaskStatsVO> stats() {
        return R.ok(delayTaskService.stats());
    }

    /**
     * 任务详情（含 payload 全文、last_error 与完整时间线）。
     */
    @Operation(summary = "任务详情", description = "payload 全文 + created_at→execute_at→executed_at→finished_at 时间线")
    @GetMapping("/{id}")
    public R<DelayTaskAdminVO> detail(@PathVariable Long id) {
        return R.ok(delayTaskService.detail(id));
    }

    /**
     * 手动重试（仅 FAILED 行）：重置回 PENDING、retry_count 清零、立即到期。
     */
    @Operation(summary = "手动重试", description = "仅 FAILED 行：retry_count 清零并立即到期重新执行")
    @PostMapping("/{id}/retry")
    public R<Boolean> retry(@PathVariable Long id) {
        return R.ok(delayTaskService.retryFailed(id));
    }

    /**
     * 手动取消（仅 PENDING/RUNNING 行）。
     */
    @Operation(summary = "手动取消", description = "仅 PENDING/RUNNING 行：置 CANCELLED 并记录终态时间")
    @PostMapping("/{id}/cancel")
    public R<Boolean> cancel(@PathVariable Long id) {
        return R.ok(delayTaskService.cancelById(id));
    }
}
