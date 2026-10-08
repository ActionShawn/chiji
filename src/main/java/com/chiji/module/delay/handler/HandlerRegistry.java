package com.chiji.module.delay.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler 注册表：启动时收集全部 {@link DelayTaskHandler} Bean，按 type 建立路由。
 * <p>
 * type 重复 / type 或 owner 空白即启动失败（fail-fast），保证「投递必有实现」——
 * submit 前置校验依赖 {@link #hasHandler(String)}，无注册 handler 的任务类型直接拒绝投递。
 */
@Slf4j
@Component
public class HandlerRegistry {

    /** type → handler（LinkedHashMap 保持注册顺序，日志可读） */
    private final Map<String, DelayTaskHandler> handlers = new LinkedHashMap<>();

    public HandlerRegistry(List<DelayTaskHandler> beans) {
        for (DelayTaskHandler handler : beans) {
            String type = handler.type();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("DelayTaskHandler#type() 不能为空: " + handler.getClass().getName());
            }
            if (handler.owner() == null || handler.owner().isBlank()) {
                throw new IllegalStateException("DelayTaskHandler#owner() 不能为空: " + handler.getClass().getName());
            }
            DelayTaskHandler prev = handlers.put(type, handler);
            if (prev != null) {
                throw new IllegalStateException("DelayTaskHandler type 重复注册: " + type
                        + " (" + prev.getClass().getName() + " / " + handler.getClass().getName() + ")");
            }
        }
        log.info("延迟任务 handler 注册完成: {}", handlers.keySet());
    }

    /** 是否存在该类型的注册 handler（submit 前置校验用） */
    public boolean hasHandler(String type) {
        return type != null && handlers.containsKey(type);
    }

    /** 取 handler（不存在返回 null，调用方先用 {@link #hasHandler(String)} 校验） */
    public DelayTaskHandler get(String type) {
        return handlers.get(type);
    }
}
