package com.chiji.module.auth.service.impl;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.module.auth.dto.UpdateUserProfileRequest;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.auth.service.UserService;
import com.chiji.module.auth.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户资料服务实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserProfileVO getProfile(Long userId) {
        return toVO(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateProfile(Long userId, UpdateUserProfileRequest request) {
        User user = requireUser(userId);

        String nickname = request.nickname();
        if (nickname != null && !nickname.isBlank()) {
            nickname = nickname.trim();
            if (nickname.length() < 2 || nickname.length() > 20) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "昵称2-20个字");
            }
            user.setNickname(nickname);
        } else {
            // 空字符串/空白视为清除昵称，前端回退「微信用户」
            user.setNickname(null);
        }

        String avatarUrl = request.avatarUrl();
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            user.setAvatarUrl(avatarUrl.trim());
        } else {
            user.setAvatarUrl(null);
        }

        userMapper.updateById(user);
        return toVO(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserProfileVO toVO(User user) {
        return new UserProfileVO(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getPhone(),
                user.getTreatmentType(),
                user.getCreatedAt());
    }
}
