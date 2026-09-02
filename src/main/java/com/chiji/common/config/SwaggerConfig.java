// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\config\SwaggerConfig.java
package com.chiji.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 接口文档配置。
 * <p>
 * 注册全局鉴权头 {@code Authorization}：Sa-Token 的 uuid 令牌直接放置在该请求头中，
 * 因此声明为 APIKEY 类型（而非 Bearer）。同时提供 {@link GroupedOpenApi} 分组示例。
 */
@Configuration
public class SwaggerConfig {

    /**
     * 全局 OpenAPI 元信息与安全声明。
     *
     * @return OpenAPI 描述
     */
    @Bean
    public OpenAPI chijiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("齿迹 API")
                        .version("v1.0.0")
                        .description("隐形牙套矫正记录小程序后端接口文档"))
                .components(new Components()
                        .addSecuritySchemes("Authorization", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("登录后获取的 Sa-Token uuid 令牌")))
                .addSecurityItem(new SecurityRequirement().addList("Authorization"));
    }

    /**
     * 分组示例：按路径将接口拆分为独立文档分组，业务落地后可按模块细化。
     *
     * @return 分组 OpenAPI
     */
    @Bean
    public GroupedOpenApi chijiGroupedApi() {
        return GroupedOpenApi.builder()
                .group("齿迹接口")
                .pathsToMatch("/api/**")
                .build();
    }
}
