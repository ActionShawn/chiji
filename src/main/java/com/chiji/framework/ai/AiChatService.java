// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\ai\AiChatService.java
package com.chiji.framework.ai;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 对话服务。
 * <p>
 * 仅当 {@code chiji.ai.enabled=true} 时注册（条件装配守卫），提供同步对话能力；
 * 调用失败统一包装为 {@link BusinessException}（AI_CALL_FAILED）。
 * <p>
 * 两类入口：
 * <ul>
 *     <li>{@link #chat(String)} —— 单轮（自检等场景，走 AiConfig 默认系统提示词）；</li>
 *     <li>{@link #chat(String, List)} —— 多轮（问答等业务场景，由业务模块指定系统提示词
 *         与历史轮次，prompt 级 system 覆盖默认提示词）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chiji.ai.enabled", havingValue = "true")
public class AiChatService {

    private final ChatClient chatClient;

    /**
     * 发送一条用户消息并返回模型回复。
     *
     * @param message 用户消息
     * @return 模型回复文本
     * @throws BusinessException 调用失败时抛出 AI_CALL_FAILED
     */
    public String chat(String message) {
        try {
            return chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("调用 AI 服务失败, message={}", message, e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, e.getMessage());
        }
    }

    /**
     * 多轮对话：携带系统提示词与历史轮次请求模型回复。
     * <p>
     * 历史轮次按时间升序传入（最后一条为最新 user 提问），由本服务组装为
     * Spring AI 的 UserMessage / AssistantMessage 序列；prompt 级 {@code system}
     * 会覆盖 {@code AiConfig} 里 ChatClient 的默认系统提示词。
     *
     * @param systemPrompt 业务系统提示词（职责边界 / 语气约束等，由调用方定义）
     * @param turns        对话轮次（升序，role 取 {@code user} / {@code assistant}）
     * @return 模型回复文本
     * @throws BusinessException 调用失败时抛出 AI_CALL_FAILED
     */
    public String chat(String systemPrompt, List<AiChatTurn> turns) {
        List<Message> messages = turns == null ? List.of() : turns.stream()
                .map(t -> "assistant".equals(t.role())
                        ? (Message) new AssistantMessage(t.content())
                        : (Message) new UserMessage(t.content()))
                .toList();
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .messages(messages)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("调用 AI 多轮对话失败, turns={}", messages.size(), e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, e.getMessage());
        }
    }

    /** 多轮对话的单条消息（role：user / assistant）。 */
    public record AiChatTurn(String role, String content) {
    }
}
