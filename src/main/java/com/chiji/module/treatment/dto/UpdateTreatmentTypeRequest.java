package com.chiji.module.treatment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 更新用户治疗方式请求体。
 * <p>
 * status 取值：CLEAR_ALIGNER / FIXED_BRACKET / LINGUAL。
 * 初始化页选择治疗方式时提交，后续在「我的」页也可修改。
 *
 * @param treatmentType 目标治疗方式编码
 */
public record UpdateTreatmentTypeRequest(
        @NotNull(message = "治疗方式不能为空")
        @Pattern(regexp = "CLEAR_ALIGNER|FIXED_BRACKET|LINGUAL", message = "治疗方式取值仅支持 CLEAR_ALIGNER / FIXED_BRACKET / LINGUAL")
        String treatmentType
) {
}
