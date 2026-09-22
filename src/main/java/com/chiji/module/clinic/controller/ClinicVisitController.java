package com.chiji.module.clinic.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.clinic.dto.VisitCompleteRequest;
import com.chiji.module.clinic.dto.VisitSaveRequest;
import com.chiji.module.clinic.service.ClinicVisitService;
import com.chiji.module.clinic.vo.ClinicDayVO;
import com.chiji.module.clinic.vo.ClinicMonthVO;
import com.chiji.module.clinic.vo.ClinicVisitVO;
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

import java.time.LocalDate;

/**
 * 复诊档案接口（日程/记录 CRUD + 标记完成 + 月/日视图）。
 */
@Tag(name = "小齿档案 · 复诊日程")
@RestController
@RequestMapping("/api/clinic/visits")
@RequiredArgsConstructor
public class ClinicVisitController {

    private final ClinicVisitService visitService;

    @Operation(summary = "创建复诊日程", description = "新增一条 PLANNED 已确认日程")
    @PostMapping
    public R<ClinicVisitVO> create(@Valid @RequestBody VisitSaveRequest req) {
        return R.ok(visitService.create(SecurityUtil.getCurrentUserId(), req));
    }

    @Operation(summary = "编辑复诊日程", description = "仅 PLANNED 可编辑")
    @PutMapping("/{id}")
    public R<ClinicVisitVO> update(@PathVariable Long id, @Valid @RequestBody VisitSaveRequest req) {
        return R.ok(visitService.update(SecurityUtil.getCurrentUserId(), id, req));
    }

    @Operation(summary = "删除日程/记录", description = "DONE 记录联动逻辑删投影时光轴记录")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        visitService.delete(SecurityUtil.getCurrentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "标记就诊完成", description = "PLANNED → DONE，补录内容/照片，可带出复诊费、生成下次日程")
    @PostMapping("/{id}/complete")
    public R<ClinicVisitVO> complete(@PathVariable Long id, @Valid @RequestBody VisitCompleteRequest req) {
        return R.ok(visitService.complete(SecurityUtil.getCurrentUserId(), id, req));
    }

    @Operation(summary = "月视图", description = "自绘月历数据源：本月日程/记录 + 预约窗口锚点（预计戴完日）+ 阶段预计完成日；锚点按首页选中阶段计算")
    @GetMapping("/month")
    public R<ClinicMonthVO> monthView(@RequestParam(required = false) String month,
                                      @RequestParam(required = false) Long stageId) {
        return R.ok(visitService.monthView(SecurityUtil.getCurrentUserId(), month, stageId));
    }

    @Operation(summary = "日视图", description = "当日日程/记录 + 当日花费")
    @GetMapping("/day")
    public R<ClinicDayVO> dayView(@RequestParam LocalDate date) {
        return R.ok(visitService.dayView(SecurityUtil.getCurrentUserId(), date));
    }

    @Operation(summary = "最近一次就诊", description = "预填诊所/医生与「距上次复诊」计算")
    @GetMapping("/last")
    public R<ClinicVisitVO> lastVisit() {
        return R.ok(visitService.lastVisit(SecurityUtil.getCurrentUserId()));
    }
}
