// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\config\WebConfig.java
package com.chiji.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web 配置。
 * <p>
 * 跨域策略：dev 环境（默认 {@code *}）放行全部来源；
 * prod 环境通过 {@code chiji.cors.allowed-origins} 配置白名单（多个域名用英文逗号分隔）。
 * 静态资源：{@code /uploads/**} 映射到本地存储目录，供上传文件对外访问（Sa-Token 只拦截 {@code /api/**}，本路径天然放行）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许的跨域来源，* 表示全部（来自 application*.yml 的 chiji.cors.allowed-origins）。 */
    @Value("${chiji.cors.allowed-origins:*}")
    private String allowedOrigins;

    /** 本地文件存储根目录（来自 chiji.storage.local.base-path）。 */
    @Value("${chiji.storage.local.base-path}")
    private String localStorageBasePath;

    /**
     * 注册跨域规则。
     *
     * @param registry 跨域注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(resolveOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册静态资源映射。
     * <p>
     * 将 {@code /uploads/**} 映射到本地存储目录（{@code chiji.storage.local.base-path}），
     * 使上传后的文件可通过 {@code {base-url}/{relative}} 直接访问。
     * Sa-Token 仅拦截 {@code /api/**}，本路径无需鉴权即可放行。
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // file: 协议需以 / 结尾；basePath 统一补 / 避免拼接丢失
        String location = localStorageBasePath.endsWith("/")
                ? "file:" + localStorageBasePath
                : "file:" + localStorageBasePath + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }

    /**
     * 解析允许的来源模式列表。
     * <p>
     * {@code *} 代表放行全部来源；否则按逗号拆分并去除空白。
     *
     * @return 来源模式数组
     */
    private String[] resolveOrigins() {
        if (allowedOrigins == null || allowedOrigins.isBlank() || "*".equals(allowedOrigins.trim())) {
            return new String[]{"*"};
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
