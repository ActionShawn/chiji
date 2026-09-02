// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\config\JacksonConfig.java
package com.chiji.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 序列化配置。
 * <p>
 * - {@code Long} / {@code long} 序列化为字符串，避免前端 JS 精度丢失；
 * - {@code LocalDateTime} 统一为「yyyy-MM-dd HH:mm:ss」并采用东八区；
 * - 忽略未知字段，提升前后端字段演进时的兼容性。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 定制全局 ObjectMapper。
     *
     * @return ObjectMapper 构建器定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER))
                .timeZone(TimeZone.getTimeZone("GMT+8"))
                .failOnUnknownProperties(false);
    }
}
