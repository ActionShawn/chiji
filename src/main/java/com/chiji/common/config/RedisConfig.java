// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\config\RedisConfig.java
package com.chiji.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 * <p>
 * 提供通用 {@link RedisTemplate}：key 使用字符串序列化，value 使用 JSON 序列化，
 * 便于读写任意 Java 对象。
 */
@Configuration
public class RedisConfig {

    /**
     * 配置字符串 key + JSON value 的 RedisTemplate。
     *
     * @param connectionFactory Redis 连接工厂
     * @return Redis 模板
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
