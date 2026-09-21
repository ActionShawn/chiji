package com.chiji.module.toolset.dto;

import java.util.List;

/**
 * 常用工具配置更新请求（全量覆盖语义：以传入列表为准，顺序即展示顺序）。
 *
 * @param toolKeys 工具 key 有序列表（允许空数组=清空自定义、回退默认；null 视为参数缺失）
 */
public record ToolKeysUpdateRequest(List<String> toolKeys) {
}
