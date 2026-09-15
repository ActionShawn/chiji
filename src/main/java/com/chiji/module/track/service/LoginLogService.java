package com.chiji.module.track.service;

import com.chiji.entity.LoginLog;
import com.chiji.framework.metrics.TrackAsyncExecutor;
import com.chiji.module.track.mapper.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录日志服务。
 * <p>
 * 登录成功后由 AuthController 埋点调用 {@link #recordAsync(Long)}，经
 * {@link TrackAsyncExecutor} 单虚拟线程异步落 1 条，不占用登录请求线程；
 * 失败仅 warn，绝不影响登录主流程。
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;
    private final TrackAsyncExecutor asyncExecutor;

    /**
     * 异步记录一次登录（登录时刻取提交时刻）。
     *
     * @param userId 用户 ID
     */
    public void recordAsync(Long userId) {
        if (userId == null) {
            return;
        }
        asyncExecutor.submit("login-log", () -> {
            LoginLog logRow = new LoginLog();
            logRow.setUserId(userId);
            logRow.setLoginAt(LocalDateTime.now());
            loginLogMapper.insert(logRow);
        });
    }
}
