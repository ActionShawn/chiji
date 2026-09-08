package com.chiji.module.auth.service;

import com.chiji.module.auth.dto.UpdateUserProfileRequest;
import com.chiji.module.auth.vo.UserProfileVO;

import java.util.List;

/**
 * 用户资料服务。
 */
public interface UserService {

    /**
     * 查询个人资料。
     *
     * @param userId 当前登录用户 ID
     * @return 用户资料 VO
     */
    UserProfileVO getProfile(Long userId);

    /**
     * 更新个人资料（昵称 / 头像）。
     *
     * @param userId  当前登录用户 ID
     * @param request 更新请求（字段可单独提交，空字符串表示清除）
     * @return 更新后的用户资料 VO
     */
    UserProfileVO updateProfile(Long userId, UpdateUserProfileRequest request);

    /**
     * 全量用户 ID 列表（按 id 升序）。
     * <p>
     * 供佩戴/通知侧定时任务（睡前提醒等）在非请求线程里枚举目标用户；
     * 无用户返回空列表。
     *
     * @return 全部用户 ID
     */
    List<Long> listAllUserIds();
}
