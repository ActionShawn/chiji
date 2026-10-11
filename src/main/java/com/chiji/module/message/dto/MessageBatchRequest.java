package com.chiji.module.message.dto;

import java.util.List;

/**
 * 信箱消息批量操作请求。
 *
 * @param ids 消息 ID 集合（必填，非空，上限 {@code 500}）
 */
public record MessageBatchRequest(List<Long> ids) {
}
