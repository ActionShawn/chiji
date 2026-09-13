package com.chiji.module.message.controller;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.result.R;
import com.chiji.enums.WearReminderTypeEnum;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.message.dto.SubscribeRecordRequest;
import com.chiji.module.message.service.SubscribeQuotaService;
import com.chiji.module.message.vo.SubscribeQuotaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
}
