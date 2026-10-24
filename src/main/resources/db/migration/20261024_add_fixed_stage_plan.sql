-- 齿迹 · 固定托槽·阶段详情页（首页改版 v2.0 走查 P5）数据层变更（2026-10-24）
-- 依据：《固定托槽版首页改版 UI 效果图》（Figma Make）PhaseDetailPage —— 阶段详情页新增
--         「医生预估疗程」「治疗计划三步区」展示，数据随阶段壳落库（固定托槽专属）。
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署新后端（Stage 实体新增 3 字段的版本）之前执行；
--      三条 ALTER 均为「追加可空列」，旧后端（不读新列）零影响，破坏性风险最低档
--   3) 本批无 DROP COLUMN、无类型变更；新列仅固定托槽阶段（mode=FIXED_BRACKET）会写入，
--      隐形存量阶段三列恒 NULL，零数据改写
--   4) 回滚脚本：20261024_add_fixed_stage_plan_rollback.sql（须先将后端回滚到不读新列的版本，再执行回滚）

-- ───────────────────────── 1. 医生预估疗程（下限 / 上限，单位月） ─────────────────────────
-- 形如 Figma「约 18–24 个月」：min=max 时前端展示「约 X 个月」，不等时展示「约 X–Y 个月」。
-- 两个可空 INT：下限 / 上限；仅固定托槽阶段有值，隐形恒 NULL
ALTER TABLE `stage`
    ADD COLUMN `estimate_months_min` INT DEFAULT NULL COMMENT '医生预估疗程下限（月，固定托槽专属，可空）' AFTER `start_date`,
    ADD COLUMN `estimate_months_max` INT DEFAULT NULL COMMENT '医生预估疗程上限（月，固定托槽专属，可空）' AFTER `estimate_months_min`;

-- ───────────────────────── 2. 治疗计划三步区 ─────────────────────────
-- JSON 数组字符串：[{title,desc,status}]，status ∈ todo(待开始)/active(进行中)/done(已完成)。
-- 三步为标准固定托槽正畸阶段（排齐/调整牙弓/关闭间隙），title 前端预置、随表单一并落库；
-- 仅固定托槽阶段有值，隐形恒 NULL。序列化/反序列化在 StageServiceImpl 内（沿用 adjustment_record.contents 模式）
ALTER TABLE `stage`
    ADD COLUMN `plan_steps` VARCHAR(800) DEFAULT NULL COMMENT '治疗计划三步区 JSON [{title,desc,status}]（固定托槽专属，可空）' AFTER `estimate_months_max`;
