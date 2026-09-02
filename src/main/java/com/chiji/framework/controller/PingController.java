// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\controller\PingController.java
package com.chiji.framework.controller;

import com.chiji.common.core.exception.ErrorCode;
import com.chiji.common.core.result.R;
import com.chiji.framework.ai.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 自检接口。
 * <p>
 * 用于验证服务与 AI 链路是否就绪，两个接口均已加入 Sa-Token 白名单，免登录访问：
 * <ul>
 *     <li>{@code GET /api/ping} —— 返回应用名与服务器当前时间；</li>
 *     <li>{@code GET /api/ai/ping} —— AI 链路自检。AI 服务由
 *     {@code @ConditionalOnProperty(chiji.ai.enabled=true)} 守卫（见 {@link AiChatService}），
 *     此处通过 {@link ObjectProvider} 懒取：启用时调用回复问候，未启用时返回友好提示。</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class PingController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** AI 服务提供者（未启用时为 empty，不影响启动）。 */
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    /**
     * 容器健康检查入口。
     * <p>
     * 微信云托管等容器平台默认对容器端口发起 {@code GET /} 探测，根路径返回 200 即视为健康。
     * Sa-Token 仅拦截 {@code /api/**}，本路径天然放行。
     *
     * @return 固定存活标识
     */
    @GetMapping("/")
    public R<String> health() {
        return R.ok("chiji-server is running");
    }

    /**
     * 微信云托管平台启动探测。
     * <p>
     * 容器启动后平台会请求 {@code GET /__tcb_probe__} 探活，必须返回 200 才认为容器就绪并分配流量。
     * 路径不在 {@code /api/**} 下，Sa-Token 天然放行。
     *
     * @return 固定存活标识
     */
    @GetMapping("/__tcb_probe__")
    public R<String> tcbProbe() {
        return R.ok("alive");
    }

    /**
     * 基础自检。
     *
     * @return 应用名与当前时间
     */
    @GetMapping("/api/ping")
    public R<PingResult> ping() {
        return R.ok(new PingResult("chiji-server", LocalDateTime.now().format(FORMATTER)));
    }

    /**
     * AI 链路自检。
     *
     * @return AI 模型的问候回复（未启用时返回提示）
     */
    @GetMapping("/api/ai/ping")
    public R<String> aiPing() {
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            return R.fail(ErrorCode.AI_CALL_FAILED.getCode(), "AI 服务未启用，请设置 CHIJI_AI_ENABLED=true 后重启");
        }
        return R.ok(aiChatService.chat("你好，请用一句话介绍你自己"));
    }

    /**
     * 自检结果体。
     *
     * @param appName    应用名
     * @param serverTime 服务器当前时间
     */
    public record PingResult(String appName, String serverTime) {
    }
}
