package com.chiji.module.treatment.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.enums.TreatmentTypeEnum;
import com.chiji.module.auth.util.SecurityUtil;
import com.chiji.module.treatment.dto.UpdateTreatmentTypeRequest;
import com.chiji.module.treatment.service.TreatmentService;
import com.chiji.module.treatment.vo.TreatmentTypeVO;
import com.chiji.module.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 治疗方式服务实现。
 * <p>
 * 治疗方式存于 {@code user.treatment_type} 字段，每用户至多一个。
 * 枚举列表由 {@link TreatmentTypeEnum} 静态派生，无需数据库表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreatmentServiceImpl implements TreatmentService {

    private final UserMapper userMapper;

    @Override
    public List<TreatmentTypeVO> listTreatmentTypes() {
        return Arrays.stream(TreatmentTypeEnum.values())
                .map(TreatmentTypeVO::of)
                .toList();
    }

    @Override
    public String getUserTreatmentType(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user.getTreatmentType();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TreatmentTypeVO updateUserTreatmentType(Long userId, UpdateTreatmentTypeRequest request) {
        // 校验治疗方式编码合法
        TreatmentTypeEnum targetEnum = TreatmentTypeEnum.getByCode(request.treatmentType());
        if (targetEnum == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "治疗方式编码不合法");
        }

        // 校验用户存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 更新治疗方式
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getTreatmentType, targetEnum.getCode()));

        log.info("更新用户治疗方式, userId={}, treatmentType={}", userId, targetEnum.getCode());
        return TreatmentTypeVO.of(targetEnum);
    }
}
