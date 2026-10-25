package com.chiji.module.adjustment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 复诊调整新增/编辑请求体（两接口复用，REQ-FB-004）。
 * <p>
 * 校验规则（服务层落库前校验）：
 * <ul>
 *   <li>contents：预设标签多选（粘托槽/换弓丝/加力/调整皮筋钩/拆牙套/其他），至少一项；
 *       取值合法性、选「其他」时 contentExtra 必填均由服务层校验</li>
 *   <li>wireNo：正整数或空（医生未说明时友好留空）</li>
 *   <li>adjustDate：可空（缺省今天）；≤今日（补记不限时间窗，仅禁止未来日期，事项 13 已拍板）</li>
 *   <li>stageId：必填，归属当前用户且 mode=FIXED_BRACKET 的阶段壳</li>
 * </ul>
 *
 * @param stageId      归属阶段 ID（必填）
 * @param wireNo       弓丝序号（可选，正整数）
 * @param contents     加力内容标签列表（必填，至少一项）
 * @param contentExtra 选「其他」时的自定义文本（条件必填）
 * @param adjustDate   调整日期（可空，缺省今天；≤今日）
 * @param note         备注（可选）
 */
public record AdjustmentSaveRequest(

        /** 归属阶段 ID（必填，阶段壳归属） */
        Long stageId,

        /** 弓丝序号（可选，正整数；不记录传 null） */
        Integer wireNo,

        /** 加力内容标签列表（必填，至少一项：粘托槽/换弓丝/加力/调整皮筋钩/拆牙套/其他） */
        @NotEmpty(message = "请至少选择一项调整内容")
        @Size(max = 6, message = "调整内容最多6项")
        List<String> contents,

        /** 选「其他」时的自定义文本（条件必填，≤200 字） */
        @Size(max = 200, message = "自定义内容最多200字")
        String contentExtra,

        /** 调整日期（可空，缺省今天；不允许未来日期） */
        LocalDate adjustDate,

        /** 备注（可选，≤200 字） */
        @Size(max = 200, message = "备注最多200字")
        String note
) {

    /** 预设加力内容标签（记录分类用语，非医疗操作指导）。粘托槽/拆牙套各一次（矫正起止），换弓丝/加力可多次。「其他」需配套 contentExtra。 */
    public static final List<String> PRESET_CONTENTS = List.of("粘托槽", "换弓丝", "加力", "调整皮筋钩", "拆牙套", "其他");

    /** 「其他」标签值（选中时 contentExtra 必填） */
    public static final String CONTENT_EXTRA_KEY = "其他";
}
