package com.chiji.module.clinic.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.clinic.dto.ExpenseSaveRequest;
import com.chiji.module.clinic.service.ClinicExpenseService;
import com.chiji.module.clinic.vo.ClinicExpenseVO;
import com.chiji.module.clinic.vo.ExpenseStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 矫正花费接口（流水 CRUD + 列表筛选 + 统计聚合）。
 */
@Tag(name = "小齿档案 · 花费")
@RestController
@RequestMapping("/api/clinic/expenses")
@RequiredArgsConstructor
public class ClinicExpenseController {

    private final ClinicExpenseService expenseService;

    @Operation(summary = "新增一笔花费")
    @PostMapping
    public R<ClinicExpenseVO> create(@Valid @RequestBody ExpenseSaveRequest req) {
        return R.ok(expenseService.create(SecurityUtil.getCurrentUserId(), req));
    }

    @Operation(summary = "编辑一笔花费")
    @PutMapping("/{id}")
    public R<ClinicExpenseVO> update(@PathVariable Long id, @Valid @RequestBody ExpenseSaveRequest req) {
        return R.ok(expenseService.update(SecurityUtil.getCurrentUserId(), id, req));
    }

    @Operation(summary = "删除一笔花费")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        expenseService.delete(SecurityUtil.getCurrentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "流水列表", description = "按月 + 可选分类筛选，日期倒序")
    @GetMapping
    public R<List<ClinicExpenseVO>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long categoryId) {
        return R.ok(expenseService.list(SecurityUtil.getCurrentUserId(), month, categoryId));
    }

    @Operation(summary = "花费统计", description = "累计总额 + 分类占比 + 近 12 个月趋势")
    @GetMapping("/stats")
    public R<ExpenseStatsVO> stats() {
        return R.ok(expenseService.stats(SecurityUtil.getCurrentUserId()));
    }
}
