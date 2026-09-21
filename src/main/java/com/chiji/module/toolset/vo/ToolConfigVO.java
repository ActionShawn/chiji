package com.chiji.module.toolset.vo;

import java.util.List;

/**
 * 常用工具配置视图。
 *
 * @param toolKeys 工具 key 有序列表；无自定义配置时为空列表（客户端回退默认顺序）
 */
public record ToolConfigVO(List<String> toolKeys) {
}
