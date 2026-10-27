// D:\Java Work Place\personal-develop\teeth-trace\chiji\src\main\java\com\chiji\module\devtest\controller\DevTestController.java
package com.chiji.module.devtest.controller;

import com.chiji.common.core.result.R;
import com.chiji.framework.wechat.WxSubscribeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开发者在线测试模块（仅开发者联调使用）。
 * <p>
 * 路由前缀 {@code /api/dev/**} 在 {@link com.chiji.common.config.SaTokenConfig} 中已加入
 * 认证白名单；控制器本身由 {@code chiji.dev-test.enabled}（环境变量 {@code CHIJI_DEV_TEST_ENABLED}）
 * 开关注册，默认关闭——关闭时 Bean 不存在，所有路由 404。仅应在联调环境临时打开。
 */
@Tag(name = "开发测试", description = "开发者联调专用，需开启 chiji.dev-test.enabled 才生效")
@RestController
@RequestMapping("/api/dev/test")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chiji.dev-test", name = "enabled", havingValue = "true")
public class DevTestController {

    /** 测试数据时区（与 WearTimes 一致）。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 测试用 `time1`（更换时间，纯日期）格式，与正式下发一致。 */
    private static final DateTimeFormatter CHANGE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 测试用 `thing2`（更换副数）示例。 */
    private static final String THING2 = "第 5 副";
    /** 测试用 `thing3` 文案（下标 = 时机偏移 + 1，镜像正式下发 ALIGNER_WX_TIP）。 */
    private static final String[] THING3 = {
            "明晚换上下一副，小齿陪着你", "记得换上下一副，小齿陪着你", "昨晚忘换啦？今晚补上，小齿陪你"};

    private final WxSubscribeClient wxSubscribeClient;

    /**
     * 按 openid 手动触发换副提醒订阅消息下发。
     * <p>
     * 直发 {@link WxSubscribeClient} 真实链路（微信网关 → 模板 → 用户微信），用于联调验证
     * 下发可用性与 errcode（如 43101 用户无订阅额度、47003 模板字段不符等）。
     * 不走 user_subscribe_quota 记账、不落站内消息。
     *
     * @param openid 目标用户微信 openid
     * @param offset 提醒时机偏移（-1 前一天 / 0 当天 / 1 后一天），决定模板文案变体，默认 0
     * @return 微信原始 errcode/errmsg（errcode == 0 表示成功；-1 为本地异常）
     */
    @Operation(summary = "手动触发换副提醒订阅消息",
            description = "按 openid 直发换副提醒订阅消息（免登录，不走额度记账），透传微信 errcode/errmsg")
    @PostMapping("/wx-subscribe-message")
    public R<WxSubscribeClient.WxSendResult> sendWxSubscribeMessage(
            @RequestParam(required = false) String openid,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        if (openid == null || openid.isBlank()) {
            return R.fail(400, "openid 不能为空");
        }
        if (offset == null || offset < -1 || offset > 1) {
            return R.fail(400, "offset 仅支持 -1 / 0 / 1");
        }
        return R.ok(wxSubscribeClient.trySendAlignerChangeMessage(openid, buildSampleData(offset)));
    }

    /** 构造与正式下发同构的模板数据（time1 / thing2 / thing3，文案按偏移取变体）。 */
    private Map<String, Object> buildSampleData(int offset) {
        // 前一天提醒的更换时间为明天，当天/后一天提醒为今天（与正式下发 pushAlignerChange 口径一致）
        LocalDate changeDate = LocalDate.now(ZONE).plusDays(offset < 0 ? 1 : 0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time1", Map.of("value", changeDate.format(CHANGE_DATE_FORMAT)));
        data.put("thing2", Map.of("value", THING2));
        data.put("thing3", Map.of("value", THING3[offset + 1]));
        return data;
    }
}
