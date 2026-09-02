package com.chiji.module.stage.vo;

import lombok.Builder;

/**
 * 阶段展示 VO（首页阶段选择器）。
 * <p>
 * 对应前端 {@code mock.stages} 与 {@code stage-selector} 组件的数据形状。
 * 字段全部由后端派生，前端直接消费。
 *
 * @param id        阶段 ID（雪花算法，序列化为字符串）
 * @param name      阶段名称（形如「第一阶段 · 主力矫正」）
 * @param meta      阶段元信息（形如「54副 · 每副7天 · 约378天」）
 * @param status    阶段状态小写：current / done / future
 * @param startDate 阶段开始日期文案（形如「2026.08.01」，取 stage.startDate，缺失回退创建日期）
 * @param edited    上次编辑时间文案（形如「上次编辑 2026.08.23 21:30」，取该阶段下所有关联数据的最新时间）
 */
@Builder
public record StageVO(Long id, String name, String meta, String status, String startDate, String edited) {
}
