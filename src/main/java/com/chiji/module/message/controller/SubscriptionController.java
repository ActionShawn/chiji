package com.chiji.module.message.controller;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.result.R;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.message.dto.SubscribeRecordRequest;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.module.message.vo.SubscribeQuotaVO;
import com.chiji.module.message.vo.TakeoffQuotaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订阅消息接口（前端 {@code wx.requestSubscribeMessage} 结果上报与额度查询）。
 * <p>
 * 受 Sa-Token 保护；用户同意订阅时额度 +1，不同意不做任何记录（每次换副都会重新弹窗）。
 */
@Tag(name = "订阅消息", description = "一次性订阅消息授权记账")
@Slf4j
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    /** 同意授权。 */
    private static final String RESULT_ACCEPT = "accept";
    /** 未同意授权。 */
    private static final String RESULT_REJECT = "reject";

    private final SubscribeQuotaService subscribeQuotaService;

    @Operation(summary = "上报换副提醒订阅结果", description = "accept 时额度 +1；返回最新剩余额度")
    @PostMapping("/aligner-change/record")
    public R<SubscribeQuotaVO> recordAlignerChange(@RequestBody SubscribeRecordRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        String result = req == null || req.result() == null ? "" : req.result().trim().toLowerCase();
        if (!RESULT_ACCEPT.equals(result) && !RESULT_REJECT.equals(result)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "订阅结果不合法");
        }
        String scene = WearReminderTypeEnum.ALIGNER_CHANGE.getCode();
        if (RESULT_ACCEPT.equals(result)) {
            subscribeQuotaService.recordAccept(userId, scene);
        }
        return R.ok(new SubscribeQuotaVO(subscribeQuotaService.remain(userId, scene)));
    }

    /**
     * 上报小齿档案订阅结果（就诊提醒 / 预约提醒共用端点，scene 区分）。
     * <p>
     * accept 时对应场景额度 +1；reject 不做任何记录（下次窗口会重新弹授权）。
     */
    @Operation(summary = "上报小齿档案订阅结果", description = "scene=CLINIC_VISIT_REMIND/CLINIC_BOOK_REMIND；accept 时额度 +1")
    @PostMapping("/clinic/record")
    public R<SubscribeQuotaVO> recordClinic(@RequestBody SubscribeRecordRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        String result = req == null || req.result() == null ? "" : req.result().trim().toLowerCase();
        if (!RESULT_ACCEPT.equals(result) && !RESULT_REJECT.equals(result)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "订阅结果不合法");
        }
        String scene = req.scene() == null ? "" : req.scene().trim();
        WearReminderTypeEnum type = WearReminderTypeEnum.getByCode(scene);
        if (type != WearReminderTypeEnum.CLINIC_VISIT_REMIND
                && type != WearReminderTypeEnum.CLINIC_BOOK_REMIND) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "订阅场景不合法");
        }
        if (RESULT_ACCEPT.equals(result)) {
            subscribeQuotaService.recordAccept(userId, scene);
        }
        return R.ok(new SubscribeQuotaVO(subscribeQuotaService.remain(userId, scene)));
    }

    /**
     * 上报摘下超时提醒订阅结果。
     * <p>
     * accept：当日累计授权数 +1（记入当日，授权调起判断口径）；reject：仅留痕不计数。
     * 拒绝原因（{@code reject}/其他取值）只记日志，不落库——PRD 边界 2：未授权不影响后续窗口。
     */
    @Operation(summary = "上报摘下超时提醒订阅结果",
            description = "accept 时当日累计授权数 +1；reject 仅留痕；返回最新当日额度快照")
    @PostMapping("/takeoff-timeout/record")
    public R<TakeoffQuotaVO> recordTakeoffTimeout(@RequestBody SubscribeRecordRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        String result = req == null || req.result() == null ? "" : req.result().trim().toLowerCase();
        if (!RESULT_ACCEPT.equals(result) && !RESULT_REJECT.equals(result)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_PARAM_INVALID, "订阅结果不合法");
        }
        String scene = SubscribeQuotaService.SCENE_TAKEOFF_TIMEOUT;
        if (RESULT_ACCEPT.equals(result)) {
            subscribeQuotaService.recordAccept(userId, scene);
        } else {
            log.info("摘下超时提醒授权被拒绝, userId={}, result={}", userId, result);
        }
        SubscribeQuotaService.TakeoffDailyQuota quota = subscribeQuotaService.takeoffDailyQuota(userId);
        return R.ok(new TakeoffQuotaVO(quota.acceptedToday(), quota.remainToday(),
                SubscribeQuotaService.TAKEOFF_DAILY_ACCEPT_LIMIT));
    }

    /**
     * 查询摘下超时提醒当日额度快照。
     * <p>
     * {@code todayAccepted} 为当日累计授权数（前端授权调起判断口径：{@code < dailyLimit} 才调起弹窗）；
     * {@code remain} 为当日剩余可下发额度（通知设置页「今日剩余提醒次数」展示用）。
     */
    @Operation(summary = "查询摘下超时提醒当日额度",
            description = "todayAccepted=当日累计授权数；remain=当日剩余可下发额度；dailyLimit=每日授权上限")
    @GetMapping("/takeoff-timeout/quota")
    public R<TakeoffQuotaVO> takeoffTimeoutQuota() {
        Long userId = SecurityUtil.getCurrentUserId();
        SubscribeQuotaService.TakeoffDailyQuota quota = subscribeQuotaService.takeoffDailyQuota(userId);
        return R.ok(new TakeoffQuotaVO(quota.acceptedToday(), quota.remainToday(),
                SubscribeQuotaService.TAKEOFF_DAILY_ACCEPT_LIMIT));
    }
}
