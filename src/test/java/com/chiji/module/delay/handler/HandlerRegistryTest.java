package com.chiji.module.delay.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HandlerRegistry 注册语义测试：type/owner 空白与 type 重复均启动失败（fail-fast），
 * 正常注册后 hasHandler/get 路由正确。不依赖 Spring 上下文。
 */
class HandlerRegistryTest {

    private static DelayTaskHandler handler(String type, String owner) {
        return new DelayTaskHandler() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public String owner() {
                return owner;
            }

            @Override
            public void execute(com.chiji.entity.DelayTask task) {
                // 测试桩，不执行业务
            }
        };
    }

    @Test
    void register_and_route() {
        DelayTaskHandler a = handler("TAKEOFF_TIMEOUT", "wear");
        HandlerRegistry registry = new HandlerRegistry(List.of(a));
        assertTrue(registry.hasHandler("TAKEOFF_TIMEOUT"));
        assertFalse(registry.hasHandler("OTHER"));
        assertEquals(a, registry.get("TAKEOFF_TIMEOUT"));
        assertNull(registry.get("OTHER"));
        assertNotNull(registry);
    }

    @Test
    void duplicate_type_fails_fast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new HandlerRegistry(List.of(handler("X", "wear"), handler("X", "clinic"))));
        assertTrue(e.getMessage().contains("X"));
    }

    @Test
    void blank_type_fails_fast() {
        assertThrows(IllegalStateException.class, () -> new HandlerRegistry(List.of(handler(" ", "wear"))));
        assertThrows(IllegalStateException.class, () -> new HandlerRegistry(List.of(handler(null, "wear"))));
    }

    @Test
    void blank_owner_fails_fast() {
        assertThrows(IllegalStateException.class, () -> new HandlerRegistry(List.of(handler("X", ""))));
    }

    @Test
    void empty_registry_is_legal() {
        // 本期未接入业务 handler，空注册表必须可正常启动
        HandlerRegistry registry = new HandlerRegistry(List.of());
        assertFalse(registry.hasHandler("ANY"));
    }
}
