package com.chiji.module.rubberband.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.rubberband.service.RubberBandService;
import com.chiji.module.rubberband.vo.RubberBandIncrementVO;
import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 橡皮筋每日计数接口（固定托槽第 1 批 REQ-FB-006 → 第 2 批 OPT-FB-04 次数计数）。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 仅治疗方式为 FIXED_BRACKET 的用户可访问（服务层门控 403）。
 * 零提醒零订阅消息红线：本模块不接入任何推送链路。
 * 第 1 批 toggle 端点废止不兼容双轨（旧客户端命中 404 走既有失败回滚分支，仅 toast 降级）。
 */
@Tag(name = "橡皮筋打卡", description = "固定托槽橡皮筋每日计数（纯手动，仅当日，多次 +1）")
@RestController
@RequestMapping("/api/rubber-band")
@RequiredArgsConstructor
public class RubberBandController {

    private final RubberBandService rubberBandService;

    /**
     * 查询今日计数 + 近 7 天点阵 + 连续天数/本周次数。
     *
     * @return 今日计数与近 7 天逐日次数（日期升序，今天在最右）+ streakDays/weekCount 聚合
     */
    @Operation(summary = "今日计数与近7天点阵", description = "近 7 天逐日计数，无记录日 count=0；含连续天数与本周次数聚合")
    @GetMapping("/recent")
    public R<RubberBandRecentVO> recent() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(rubberBandService.recent(userId));
    }

    /**
     * 当日计数 +1（仅当日，不支持补录；upsert 原子累加幂等，单日上限超限返回业务错误码）。
     * <p>
     * 无请求体：recordDate 由服务端取当日。
     *
     * @return 落库后的当日累计次数（含本次 +1）
     */
    @Operation(summary = "当日计数+1", description = "仅当日可操作；upsert 原子累加，以落库计数返回；超单日上限返回业务错误码")
    @PostMapping("/increment")
    public R<RubberBandIncrementVO> increment() {
        Long userId = SecurityUtil.getCurrentUserId();
        RubberBandIncrementVO vo = rubberBandService.increment(userId);
        return R.ok(vo, vo.count() != null && vo.count() == 1 ? "已打卡，继续保持～" : "已记一次更换");
    }
}
