package com.chiji.module.clinic.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.clinic.dto.CategoryCreateRequest;
import com.chiji.module.clinic.dto.CategorySortRequest;
import com.chiji.module.clinic.service.ClinicCategoryService;
import com.chiji.module.clinic.vo.ClinicCategoryVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 花费分类接口（内置五类只读；自定义增删排序）。
 */
@Tag(name = "小齿档案 · 花费分类")
@RestController
@RequestMapping("/api/clinic/categories")
@RequiredArgsConstructor
public class ClinicCategoryController {

    private final ClinicCategoryService categoryService;

    @Operation(summary = "分类列表", description = "内置五类 + 用户自定义合并，内置排前")
    @GetMapping
    public R<List<ClinicCategoryVO>> list() {
        return R.ok(categoryService.list(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "新增自定义分类", description = "名称同用户下唯一（含内置名）")
    @PostMapping
    public R<ClinicCategoryVO> create(@Valid @RequestBody CategoryCreateRequest req) {
        return R.ok(categoryService.create(SecurityUtil.getCurrentUserId(), req));
    }

    @Operation(summary = "删除自定义分类", description = "内置分类拒绝删除；流水保留")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(SecurityUtil.getCurrentUserId(), id);
        return R.ok();
    }

    @Operation(summary = "自定义分类排序", description = "全量顺序覆盖，内置分类固定排最前")
    @PutMapping("/sort")
    public R<Void> sort(@Valid @RequestBody CategorySortRequest req) {
        categoryService.sort(SecurityUtil.getCurrentUserId(), req);
        return R.ok();
    }
}
