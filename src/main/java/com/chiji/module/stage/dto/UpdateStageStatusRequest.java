package com.chiji.module.stage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 更新阶段状态请求体。
 * <p>
 * status 取值：ACTIVE（启用）/ ENDED（结束）。
 * 启用某阶段时，后端会自动把旧的 ACTIVE 阶段置为 ENDED，保持同一时间至多一个启用阶段。
 *
 * @param status 目标状态，取值 ACTIVE / ENDED
 */
public record UpdateStageStatusRequest(
        @NotNull(message = "状态不能为空")
        @Pattern(regexp = "ACTIVE|ENDED", message = "状态取值仅支持 ACTIVE / ENDED")
        String status
) {
}
