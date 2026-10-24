package com.chiji.module.stage.vo;

/**
 * 治疗计划单步（固定托槽·阶段详情页三步区，首页改版 v2.0 走查 P5）。
 * <p>
 * 与前端 {@code mode-config.PLAN_STEP_TITLES} 同源：三步为固定托槽标准正畸阶段
 * （排齐 / 调整牙弓 / 关闭间隙），title 前端预置、随建阶段表单一并落库，本 VO 回显原文。
 * 建/编辑阶段请求（CreateStageRequest / UpdateStageRequest）与本 VO 复用同一结构。
 *
 * @param title  步骤标题（如「调整牙弓形态」）
 * @param desc   步骤说明（可空，如「使用第 2 根弓丝 · 按医嘱佩戴橡皮筋」）
 * @param status 步骤状态小写：todo(待开始) / active(进行中) / done(已完成)
 */
public record PlanStepVO(String title, String desc, String status) {
}
