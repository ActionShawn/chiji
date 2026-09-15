package com.chiji.module.track.service;

import com.chiji.entity.SlowApiLog;
import com.chiji.framework.metrics.TrackAsyncExecutor;
import com.chiji.module.track.mapper.SlowApiLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 慢请求/错误明细服务。
 * <p>
 * 由接口监控拦截器调用：仅超过慢阈值（默认 500ms）或失败的请求经
 * {@link TrackAsyncExecutor} 异步单条写入，正常请求零落库；
 * 执行器内置队列上限，故障场景丢弃不反压业务。
 */
@Service
@RequiredArgsConstructor
public class SlowApiLogService {

    /** error_msg 截断长度（表列宽） */
    private static final int ERROR_MSG_MAX = 500;

    private final SlowApiLogMapper slowApiLogMapper;
    private final TrackAsyncExecutor asyncExecutor;

    /**
     * 异步记录一条慢请求/错误明细。
     *
     * @param api     归一化接口模板
     * @param userId  登录用户 ID（可空）
     * @param costMs  耗时 ms
     * @param success 是否成功
     * @param ex      处理异常（成功慢请求为 null）
     */
    public void recordAsync(String api, Long userId, long costMs, boolean success, Exception ex) {
        asyncExecutor.submit("slow-api", () -> {
            SlowApiLog row = new SlowApiLog();
            row.setApi(api);
            row.setUserId(userId);
            row.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
            row.setSuccess(success ? 1 : 0);
            row.setErrorMsg(truncate(ex));
            slowApiLogMapper.insert(row);
        });
    }

    /** 异常摘要截断到列宽。 */
    private String truncate(Exception ex) {
        if (ex == null) {
            return null;
        }
        String msg = ex.toString();
        return msg.length() > ERROR_MSG_MAX ? msg.substring(0, ERROR_MSG_MAX) : msg;
    }
}
