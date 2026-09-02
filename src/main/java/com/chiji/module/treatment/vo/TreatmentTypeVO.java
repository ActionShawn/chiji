package com.chiji.module.treatment.vo;

import com.chiji.enums.TreatmentTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 治疗方式展示 VO。
 * <p>
 * 供初始化页渲染治疗方式卡片：
 * <ul>
 *   <li>{@code code}：治疗方式编码，选择后回传给后端</li>
 *   <li>{@code name}：卡片标题（如「隐形矫正」）</li>
 *   <li>{@code subTypes}：子类型描述（如「隐适美 / 时代天使 / 正雅」）</li>
 *   <li>{@code available}：是否已开放，false 时卡片置灰并显示「敬请期待」角标</li>
 * </ul>
 */
@Schema(description = "治疗方式展示对象")
@Builder
public record TreatmentTypeVO(
        @Schema(description = "治疗方式编码：CLEAR_ALIGNER / FIXED_BRACKET / LINGUAL") String code,
        @Schema(description = "中文名称") String name,
        @Schema(description = "子类型描述") String subTypes,
        @Schema(description = "是否已开放") boolean available
) {

    /**
     * 由枚举派生 VO。
     *
     * @param item 枚举项
     * @return 展示 VO
     */
    public static TreatmentTypeVO of(TreatmentTypeEnum item) {
        return TreatmentTypeVO.builder()
                .code(item.getCode())
                .name(item.getName())
                .subTypes(item.getSubTypes())
                .available(item.isAvailable())
                .build();
    }
}
