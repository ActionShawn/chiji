-- 齿迹 · 固定托槽·阶段详情页（20261024_add_fixed_stage_plan.sql）回滚脚本
-- 执行前提：须先将后端回滚到「不读 estimate_months_min/max、plan_steps 三列」的版本，再执行本脚本
ALTER TABLE `stage`
    DROP COLUMN `estimate_months_min`,
    DROP COLUMN `estimate_months_max`,
    DROP COLUMN `plan_steps`;
