package com.chiji.module.auth.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 安全上下文工具类。
 * <p>
 * 封装 Sa-Token 的常用取值操作，业务模块统一通过本类获取当前登录用户 ID，
 * 避免直接依赖 Sa-Token API，便于后续替换鉴权方案。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID
     * @throws cn.dev33.satoken.exception.NotLoginException 未登录时抛出（由全局异常处理器转为 401）
     */
    public static Long getCurrentUserId() {
        return StpUtil.getLoginIdAsLong();
    }
}
