package com.chiji.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * <p>
 * 提供 MySQL 方言的分页拦截器；{@code createdAt} / {@code updatedAt} / {@code deleted}
 * 的自动填充统一由 {@code com.chiji.config.MyMetaObjectHandler} 承担，此处不再重复注册。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器（MySQL 方言）。
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
