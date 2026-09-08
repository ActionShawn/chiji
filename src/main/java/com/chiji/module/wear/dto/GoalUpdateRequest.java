package com.chiji.module.wear.dto;

/**
 * 每日佩戴目标更新请求。
 *
 * @param goalHours 目标（小时，18.0–22.0，步进 0.5；后端按秒存储 64800–79200）
 */
public record GoalUpdateRequest(Double goalHours) {
}
