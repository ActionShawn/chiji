package com.chiji.module.chew.controller;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.result.R;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.chew.dto.ChewStartRequest;
import com.chiji.module.chew.dto.ChewStateChangeRequest;
import com.chiji.module.chew.service.ChewQueryService;
import com.chiji.module.chew.service.ChewService;
import com.chiji.module.chew.vo.ChewDayDetailVO;
import com.chiji.module.chew.vo.ChewDayVO;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.ChewStatsVO;
import com.chiji.module.chew.vo.TodayChewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 咬胶计时接口（#1–#10、#12）。
 * <p>
 * 统一前缀 {@code /api/chew}，受 Sa-Token 保护（{@code SecurityUtil.getCurrentUserId()}），
 * 返回 {@code R<T>}；时长字段一律秒，时刻字段返回毫秒时间戳。
 * <p>
 * 注意：#11（PUT {@code /sessions/{id}/target}）本期不启用，故不暴露该端点；
 * 「+1 分钟」随状态变更请求 {@code ChewStateChangeRequest.targetSec} 上送。
 */
@Tag(name = "咬胶计时", description = "咬胶会话状态机 + 今日/日历/统计")
@RestController
@RequestMapping("/api/chew")
@RequiredArgsConstructor
public class ChewController {

    private final ChewService chewService;
    private final ChewQueryService chewQueryService;

    @Operation(summary = "开始咬胶", description = "同用户已有进行中会话则幂等返回既有；clientSessionId 唯一键兜底防重复")
    @PostMapping("/sessions")
    public R<ChewSessionVO> start(@Valid @RequestBody ChewStartRequest request) {
        return R.ok(chewService.start(SecurityUtil.getCurrentUserId(), request));
    }

    @Operation(summary = "暂停咬胶", description = "已暂停则原样返回（幂等）；可携带 targetSec 覆盖目标")
    @PostMapping("/sessions/{id}/pause")
    public R<ChewSessionVO> pause(@PathVariable Long id,
                                  @Valid @RequestBody(required = false) ChewStateChangeRequest request) {
        return R.ok(chewService.pause(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "继续咬胶", description = "非暂停则原样返回（幂等）；结算本段暂停时长")
    @PostMapping("/sessions/{id}/resume")
    public R<ChewSessionVO> resume(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) ChewStateChangeRequest request) {
        return R.ok(chewService.resume(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "重来咬胶", description = "复位同一行（不新建），起点归零并保留原归属快照")
    @PostMapping("/sessions/{id}/restart")
    public R<ChewSessionVO> restart(@PathVariable Long id,
                                    @Valid @RequestBody(required = false) ChewStateChangeRequest request) {
        return R.ok(chewService.restart(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "结束咬胶", description = "已结束则幂等返回既有（不覆盖结束时刻）；净时长<5s 视为误触丢弃，返回 null")
    @PostMapping("/sessions/{id}/finish")
    public R<ChewSessionVO> finish(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) ChewStateChangeRequest request) {
        return R.ok(chewService.finish(SecurityUtil.getCurrentUserId(), id, request));
    }

    @Operation(summary = "进行中咬胶会话", description = "无进行中返回 null")
    @GetMapping("/sessions/active")
    public R<ChewSessionVO> active() {
        return R.ok(chewQueryService.active(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "今日咬胶累计", description = "netSec 含进行中实时净时长；endedNetSec 仅已结束；含首页静默态字段")
    @GetMapping("/today")
    public R<TodayChewVO> today() {
        return R.ok(chewQueryService.today(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "咬胶统计", description = "range = LAST_7 / CURRENT_MONTH / LAST_30 / YEAR（空回退 LAST_7）")
    @GetMapping("/stats")
    public R<ChewStatsVO> stats(@RequestParam(required = false) String range) {
        return R.ok(chewQueryService.stats(SecurityUtil.getCurrentUserId(), range));
    }

    @Operation(summary = "咬胶月历", description = "month = YYYY-MM；返回该月每日汇总平行数组（一律已结束口径）")
    @GetMapping("/calendar")
    public R<ChewDayVO> calendar(@RequestParam String month) {
        return R.ok(chewQueryService.calendar(SecurityUtil.getCurrentUserId(), parseMonth(month)));
    }

    @Operation(summary = "某日咬胶明细", description = "date = YYYY-MM-DD；返回当日汇总 + 逐条记录（一律已结束口径）")
    @GetMapping("/day")
    public R<ChewDayDetailVO> day(@RequestParam String date) {
        return R.ok(chewQueryService.day(SecurityUtil.getCurrentUserId(), parseDate(date)));
    }

    @Operation(summary = "丢弃未结束咬胶会话", description = "仅 RUNNING/PAUSED 可丢弃（软删）；已结束→71003；不存在/非本人→71001；返回今日累计")
    @DeleteMapping("/sessions/{id}")
    public R<TodayChewVO> discard(@PathVariable Long id) {
        return R.ok(chewService.discard(SecurityUtil.getCurrentUserId(), id));
    }

    /** 解析 yyyy-MM-dd，非法抛咬胶参数错误（避免 500）。 */
    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.CHEW_PARAM_INVALID, "日期格式应为 yyyy-MM-dd");
        }
    }

    /** 解析 yyyy-MM，非法抛咬胶参数错误（避免 500）。 */
    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.CHEW_PARAM_INVALID, "月份格式应为 yyyy-MM");
        }
    }
}
