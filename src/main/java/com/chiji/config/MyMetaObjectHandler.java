// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\config\MyMetaObjectHandler.java
package com.chiji.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充处理器。
 * <p>
 * 插入时填充 {@code createdAt}/{@code updatedAt}/{@code deleted=0}，更新时填充 {@code updatedAt}。
 * 统一使用 Strict 系列方法：仅当目标字段当前为 null 时填充，不覆盖业务已赋的值。
 * 本类取代了 MybatisPlusConfig 中原有的内联 Bean，避免重复注册。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入填充：createdAt / updatedAt = 当前时间，deleted = 0。
     *
     * @param metaObject 目标元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * 更新填充：updatedAt = 当前时间。
     *
     * @param metaObject 目标元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
