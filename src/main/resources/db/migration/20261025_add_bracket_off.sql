-- 齿迹 · 固定托槽·托槽脱落记录（首页改版 v2.0 RecordPage「托槽脱落」）数据层变更（2026-10-25）
-- 依据：Figma Make RecordPage type=托槽脱落（发生了什么 / 发生位置 / 现场照片 / 临时处理）。
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署新后端（含 BRACKET_OFF 枚举与新列的版本）之前执行
--   3) 两条 ALTER 均为「追加可空列」，旧后端（不读新列）零影响；新增 badge_type 取值 BRACKET_OFF 无需改列定义
--      （badge_type 为 VARCHAR(16)，容纳 BRACKET_OFF(11)）
--   4) 回滚脚本：20261025_add_bracket_off_rollback.sql

-- ───────────────────────── timeline_record：托槽脱落类型专属可空载荷 ─────────────────────────
-- 与 pain_score/pain_part 同构（记录类型专属载荷挂核心表，评审 2.2 裁决）
ALTER TABLE `timeline_record`
    ADD COLUMN `bracket_issue` VARCHAR(32) DEFAULT NULL COMMENT '托槽脱落情形（托槽完全脱落/托槽松动/弓丝移位或扎嘴/结扎丝脱落，仅 BRACKET_OFF 有值）' AFTER `pain_part`,
    ADD COLUMN `bracket_tooth` VARCHAR(16) DEFAULT NULL COMMENT '脱落牙位（如「右上4」，仅 BRACKET_OFF 有值）' AFTER `bracket_issue`;
