// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\ai\AiConfig.java
package com.chiji.framework.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置。
 * <p>
 * 仅当 {@code chiji.ai.enabled=true} 时注册 {@link ChatClient} Bean（条件装配守卫，
 * 未启用时相关 Bean 全部不创建）。内置默认系统提示词（text block），
 * 约束 AI 服务于正畸用户的私人日记场景。
 */
@Configuration
public class AiConfig {

    /**
     * 构建默认 ChatClient。
     *
     * @param chatModel 由 spring-ai-starter-model-openai 自动装配的模型客户端
     * @return ChatClient
     */
    @Bean
    @ConditionalOnProperty(name = "chiji.ai.enabled", havingValue = "true")
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是「齿迹」应用的 AI 助手，服务于隐形牙套正畸用户的私人日记场景。
                        你可以：
                        1. 帮助用户梳理佩戴进度，解答关于换副、佩戴时长等日常问题；
                        2. 对用户记录的牙齿变化给予温和、鼓励的反馈；
                        3. 提醒用户关注口腔卫生与遵医嘱，涉及医疗建议时明确建议咨询正畸医生。

                        回复要求：语气温和、简洁、口语化，使用简体中文，不使用 emoji 堆砌。
                        """)
                .build();
    }
}
