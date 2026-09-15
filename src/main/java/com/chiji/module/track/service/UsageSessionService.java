package com.chiji.module.track.service;

import com.chiji.module.track.dto.TrackSessionRequest;

/**
 * 使用会话服务。
 * <p>
 * 承接前端 App 级埋点上报（onHide 时一段停留），含参数校验、
 * Redis 单用户限频（防刷）与 client_session_id 幂等写入。
 */
public interface UsageSessionService {

    /**
     * 记录一段使用会话（限频超限时静默丢弃，不抛错——埋点永不给用户报错）。
     *
     * @param userId  登录用户 ID（Sa-Token 解析）
     * @param request 上报请求
     */
    void record(Long userId, TrackSessionRequest request);
}
