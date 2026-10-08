package com.chiji.module.delay.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler 注册表：启动时收集全部 {@link DelayTaskHandler} Bean，按 type 建立路由。
 * <p>
 * type 重复 / type 或 owner 空白即启动失败（fail-fast），保证「投递必有实现」——
 * submit 前置校验依赖 {@link #hasHandler(String)}，无注册 handler 的任务类型直接拒绝投递。
 * <p>
 * 注册时机：不在构造器直接注入 {@code List<DelayTaskHandler>}。handler 实现依赖业务 service
 * （如 {@code com.chiji.module.wear.service.WearService}），业务 service 又依赖
 * {@code com.chiji.module.delay.service.DelayTaskService}，后者依赖本注册表；构造器即注入 List
 * 会强制实例化全部 handler，形成无法解析的构造器循环依赖，导致启动抛
 * BeanCurrentlyInCreationException（8080 不监听、云托管探针 connection refused）。
 * 故改为持有 {@link ObjectProvider} 惰性句柄，在 {@link #afterSingletonsInstantiated()}
 * （全部单例就绪、可安全解析任意 Bean）时再拉取并校验——既彻底断环，又把 fail-fast 保留在
 * 启动完成前（该回调早于 WebServer 启动与探针接入）。
 */
@Slf4j
@Component
public class HandlerRegistry implements SmartInitializingSingleton {

    /** type → handler（LinkedHashMap 保持注册顺序，日志可读） */
    private final Map<String, DelayTaskHandler> handlers = new LinkedHashMap<>();

    /** 惰性句柄：构造期不触发任何 handler 实例化，避免构造器循环依赖 */
    private final ObjectProvider<DelayTaskHandler> handlerProvider;

    public HandlerRegistry(ObjectProvider<DelayTaskHandler> handlerProvider) {
        this.handlerProvider = handlerProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (DelayTaskHandler handler : handlerProvider) {
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
