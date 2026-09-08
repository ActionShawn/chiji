package com.chiji.module.wear.vo;

/**
 * 佩戴目标视图。
 *
 * @param goalHours 目标小时（0.1h 精度，如 20.0）
 * @param goalSec   目标秒
 * @param minHours  允许下限 18.0
 * @param maxHours  允许上限 22.0
 * @param step      步进 0.5
 */
public record GoalVO(
        Double goalHours,
        Integer goalSec,
        Double minHours,
        Double maxHours,
        Double step
) {
}
