package com.chiji.module.stage.controller;

import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.stage.dto.AlignerTimeUpdateRequest;
import com.chiji.module.stage.dto.RevertAlignerRequest;
import com.chiji.module.stage.service.AlignerService;
import com.chiji.module.stage.vo.AlignerNodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 牙套副接口。
 * <p>
 * 受 Sa-Token 保护，需携带 Authorization 请求头。
 * 提供手动换副（结束当前副、开始下一副）及反向换副（撤回误操作）。
 */
@Tag(name = "牙套副", description = "牙套副的换副操作")
@RestController
@RequestMapping("/api/aligners")
@RequiredArgsConstructor
public class AlignerController {

    private final AlignerService alignerService;

    /**
     * 手动结束当前副并开始下一副。
     * <p>
     * 当前副 state → DONE（endDate = 昨天）；下一副 state → ACTIVE（startDate = 今天，currentDay = 1）；
     * 后续 FUTURE 副按 daysPerAligner 联动重排 startDate/endDate。
     *
     * @param id 要结束的牙套副 ID（当前 ACTIVE 副的 ID）
     * @return 更新后的节点列表（供前端刷新展示）
     */
    @Operation(summary = "结束当前副并开始下一副", description = "手动换副：当前副 DONE，下一副 ACTIVE，后续联动重排")
    @PostMapping("/{id}/finish")
    public R<List<AlignerNodeVO>> finishAligner(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<AlignerNodeVO> nodes = alignerService.finishAligner(userId, id);
        return R.ok(nodes, "已换到下一副");
    }

    /**
     * 反向换副：撤回「换下一副」误操作，支持给上一副「多戴几天」。
     * <p>
     * 当前 ACTIVE 副 state → FUTURE；上一副 DONE state → ACTIVE，endDate 按佩戴进度分情况：
     * 已戴满周期（超期换的副）取 max(用户指定延长终点, 今天)；未戴满周期按原计划终点恢复；
     * 后续 FUTURE 副按各副自身天数联动重排 startDate/endDate。
     *
     * @param id      当前 ACTIVE 副的 ID（即将被回退为 FUTURE）
     * @param request 换回请求（endDate 可选，仅超期场景用于指定「再戴几天」折算的佩戴终点）
     * @return 更新后的节点列表（供前端刷新展示）
     */
    @Operation(summary = "换回上一副", description = "反向换副：当前 ACTIVE → FUTURE，上一副 DONE → ACTIVE；超期场景可传 endDate 指定延长终点，后续联动重排")
    @PostMapping("/{id}/revert")
    public R<List<AlignerNodeVO>> revertAligner(@PathVariable Long id,
                                                @RequestBody(required = false) RevertAlignerRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<AlignerNodeVO> nodes = alignerService.revertAligner(userId, id, request);
        return R.ok(nodes, "已换回上一副");
    }

    /**
     * 调整单副开始/结束时间（锚点式联动，批量顺延后续副）。
     * <p>
     * 仅传 {@code startDate}：该副天数不变，endDate 自动顺延；仅传 {@code endDate}：该副天数随之变化。
     * 修改后该副之后的全部副按各自天数自动顺延，保证时间不重叠、无缝衔接。
     *
     * @param id 要调整的牙套副 ID
     * @param req 时间调整请求（startDate / endDate 至少一个）
     * @return 更新后的节点列表（供前端刷新展示）
     */
    @Operation(summary = "调整牙套时间", description = "调整单副开始/结束时间，后续副按各自天数自动顺延，保证不重叠")
    @PutMapping("/{id}/time")
    public R<List<AlignerNodeVO>> updateAlignerTime(@PathVariable Long id, @RequestBody AlignerTimeUpdateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<AlignerNodeVO> nodes = alignerService.updateAlignerTime(userId, id, req);
        return R.ok(nodes, "时间已调整，后续副已自动顺延");
    }
}
