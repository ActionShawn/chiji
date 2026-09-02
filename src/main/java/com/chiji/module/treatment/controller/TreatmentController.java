package com.chiji.module.treatment.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.enums.TreatmentTypeEnum;
import com.chiji.module.treatment.dto.UpdateTreatmentTypeRequest;
import com.chiji.module.treatment.service.TreatmentService;
import com.chiji.module.treatment.vo.TreatmentTypeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 治疗方式接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 提供治疗方式列表查询、用户当前治疗方式查询与更新，
 * 用于初始化页选择治疗方式及「我的」页修改。
 */
@Tag(name = "治疗方式", description = "治疗方式查询与选择")
@RestController
@RequestMapping("/api/treatment")
@RequiredArgsConstructor
public class TreatmentController {

    private final TreatmentService treatmentService;

    /**
     * 查询所有治疗方式列表。
     * <p>
     * 返回所有治疗方式（含未开放的），前端根据 available 字段决定卡片是否可选。
     *
     * @return 治疗方式 VO 列表
     */
    @Operation(summary = "查询治疗方式列表", description = "返回所有治疗方式（含未开放），前端据 available 决定是否可选")
    @GetMapping("/types")
    public R<List<TreatmentTypeVO>> listTreatmentTypes() {
        return R.ok(treatmentService.listTreatmentTypes());
    }

    /**
     * 查询当前用户的治疗方式。
     * <p>
     * app.js onLaunch 时调用，用于路由分发：
     * 未选择 → 初始化页；CLEAR_ALIGNER → 隐形矫正首页；其它 → 敬请期待页。
     *
     * @return 治疗方式 VO（未选择时 data 为 null）
     */
    @Operation(summary = "查询当前用户治疗方式", description = "返回当前用户治疗方式，未选择返回 null")
    @GetMapping("/current")
    public R<TreatmentTypeVO> getCurrentTreatmentType() {
        Long userId = SecurityUtil.getCurrentUserId();
        String code = treatmentService.getUserTreatmentType(userId);
        if (code == null) {
            return R.ok(null);
        }
        TreatmentTypeEnum enumItem = TreatmentTypeEnum.getByCode(code);
        if (enumItem == null) {
            return R.ok(null);
        }
        return R.ok(TreatmentTypeVO.of(enumItem));
    }

    /**
     * 更新当前用户的治疗方式。
     * <p>
     * 用于初始化页选择治疗方式，以及「我的」页修改治疗方式。
     *
     * @param request 更新请求
     * @return 更新后的治疗方式 VO
     */
    @Operation(summary = "更新用户治疗方式", description = "初始化页选择或「我的」页修改治疗方式")
    @PutMapping("/current")
    public R<TreatmentTypeVO> updateTreatmentType(@Valid @RequestBody UpdateTreatmentTypeRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        TreatmentTypeVO vo = treatmentService.updateUserTreatmentType(userId, request);
        return R.ok(vo, "治疗方式已更新");
    }
}
