// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\ai\AiChatService.java
package com.chiji.framework.ai;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * AI 对话服务。
 * <p>
 * 仅当 {@code chiji.ai.enabled=true} 时注册（条件装配守卫），提供同步对话能力；
 * 调用失败统一包装为 {@link BusinessException}（AI_CALL_FAILED）。
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
}
