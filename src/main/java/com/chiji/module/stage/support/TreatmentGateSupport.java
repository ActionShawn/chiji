package com.chiji.module.stage.support;

import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.enums.TreatmentTypeEnum;

/**
 * 固定托槽门控支撑（后端侧门控唯一事实源）。
 * <p>
 * 固定托槽专属接口（复诊调整 / 橡皮筋打卡 / 疼痛记录）统一经由本类校验
 * {@code user.treatment_type}，非 FIXED_BRACKET 用户一律 403 拒绝。
 * 禁止在业务代码里散写 {@code "FIXED_BRACKET".equals(treatmentType)} 字符串比较。
 * <p>
 * 第 2 批若回补疼痛记录给隐形用户，仅需调整疼痛链路的调用点，不在本类扩大放行面。
 */
public final class TreatmentGateSupport {

    private TreatmentGateSupport() {
    }

    /**
     * 判断治疗方式是否为固定托槽。
     *
     * @param treatmentType 用户治疗方式编码（可空）
     * @return true = 固定托槽用户
     */
    public static boolean isFixedBracket(String treatmentType) {
        return TreatmentTypeEnum.FIXED_BRACKET.getCode().equals(treatmentType);
    }

    /**
     * 校验当前用户为固定托槽用户，否则抛 403（需求清单 REQ-FB-004/006/007 门控验收项）。
     *
     * @param treatmentType 用户治疗方式编码（可空）
     */
    public static void assertFixedBracket(String treatmentType) {
        if (!isFixedBracket(treatmentType)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前矫正方式暂不支持");
        }
    }
}
