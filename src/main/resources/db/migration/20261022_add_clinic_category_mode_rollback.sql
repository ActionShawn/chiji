-- 齿迹 · 固定托槽体验优化第 2 轮 · OPT-FB-03 分类分档迁移回滚脚本（2026-10-22）
-- 配套迁移：20261022_add_clinic_category_mode.sql
-- 【执行前提】后端必须已回滚到不感知 mode 列的旧版本，再执行本脚本；
--   若后端仍在过滤版上执行本回滚，存量隐形行为不变（mode 全 NULL = 全可见），固定五类将消失。
--
-- 数据丢失声明：
--   仅删除本迁移新增的固定五类内置行（id 3106~3110）。若生产已有花费流水引用这些分类 id，
--   回滚后其分类名显示退化为「已删分类」（与现有悬空分类兜底口径一致）；
--   staging 验证须先确认无生产流水引用 3106~3110 方可执行（评审 SQL-2 回滚方案前提）。

-- 1) 删除固定五类内置行（限 id 精确段 + user_id=0 双条件，防误删）
DELETE FROM `clinic_expense_category` WHERE `id` BETWEEN 3106 AND 3110 AND `user_id` = 0;

-- 2) 删除 mode 列，结构回到迁移前
ALTER TABLE `clinic_expense_category` DROP COLUMN `mode`;
