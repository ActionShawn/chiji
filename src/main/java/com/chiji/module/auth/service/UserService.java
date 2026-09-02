package com.chiji.module.auth.service;

import com.chiji.module.auth.dto.UpdateUserProfileRequest;
import com.chiji.module.auth.vo.UserProfileVO;

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
}
