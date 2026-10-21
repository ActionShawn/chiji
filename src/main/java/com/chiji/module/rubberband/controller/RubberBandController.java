package com.chiji.module.rubberband.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.rubberband.service.RubberBandService;
import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import com.chiji.module.rubberband.vo.RubberBandToggleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 橡皮筋每日打卡接口（固定托槽第 1 批，REQ-FB-006）。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 仅治疗方式为 FIXED_BRACKET 的用户可访问（服务层门控 403）。
 * 零提醒零订阅消息红线：本模块不接入任何推送链路。
 */
@Tag(name = "橡皮筋打卡", description = "固定托槽橡皮筋每日打卡（纯手动，仅当日）")
@RestController
@RequestMapping("/api/rubber-band")
@RequiredArgsConstructor
public class RubberBandController {

    private final RubberBandService rubberBandService;

    /**
     * 查询今日状态 + 近 7 天点阵。
     *
     * @return 今日打卡状态与近 7 天逐日状态（日期升序，今天在最右）
     */
    @Operation(summary = "今日状态与近7天点阵", description = "近 7 天逐日打卡状态，无记录日 done=false")
    @GetMapping("/recent")
    public R<RubberBandRecentVO> recent() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(rubberBandService.recent(userId));
    }

    /**
     * 打卡/取消翻转（仅当日，幂等）。
     * <p>
     * 无请求体：recordDate 由服务端取当日，第 1 批明确排除补录历史日期。
     *
     * @return 落库后的最终状态（true 已打卡 / false 已取消）
     */
    @Operation(summary = "打卡/取消翻转", description = "仅当日可操作；upsert 幂等，以落库状态返回")
    @PostMapping("/toggle")
    public R<RubberBandToggleVO> toggle() {
        Long userId = SecurityUtil.getCurrentUserId();
        RubberBandToggleVO vo = rubberBandService.toggle(userId);
        return R.ok(vo, Boolean.TRUE.equals(vo.done()) ? "已打卡，继续保持～" : "已取消今日打卡");
    }
}
