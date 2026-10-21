-- 齿迹 · 固定托槽矫正第 1 批回滚脚本（2026-10-21）
-- 对应迁移：20261021_add_fixed_bracket.sql
-- 【执行前提】必须先将后端回滚到不依赖 adjustment_record / rubber_band_record /
--            pain_score / pain_part 的版本，再执行本脚本（与迁移脚本头注对齐）
-- 【数据丢失声明】
--   1) 第 2 段 DROP TABLE：删除两张新表及其全部数据（紧急回退用）；
--   2) 第 4 段 DROP COLUMN：疼痛数据不保留（评审 4.2 声明，仅紧急回退用）；
--   3) 第 3 段 stage.count 回滚会把固定阶段伪造成「0 副阶段」——
--      回滚前提：FIXED_BRACKET 功能已下线、固定阶段数据已确认处置（评审 4.2 要点）。

-- ───────────────────────── 1.（可选）联动下线复诊调整双写的时光轴记录 ─────────────────────────
-- 场景：固定托槽功能整体下线且不复保留时光轴上的「调整」节点时执行（默认注释，按需放开）；
-- DROP TABLE adjustment_record 后这些双写记录将失去联动来源，旧版本前端遇未知徽标 ADJUSTMENT
-- 按无徽标兜底渲染，不报错，故本段非必须。
-- UPDATE `timeline_record` tr
-- JOIN `adjustment_record` ar ON ar.`timeline_record_id` = tr.`id`
-- SET tr.`deleted` = 1
-- WHERE tr.`deleted` = 0 AND ar.`deleted` = 0;

-- ───────────────────────── 2. 删除两张新表 ─────────────────────────
DROP TABLE IF EXISTS `rubber_band_record`;
DROP TABLE IF EXISTS `adjustment_record`;

-- ───────────────────────── 3. stage：恢复 count NOT NULL ─────────────────────────
-- 先处置 NULL 行（否则 MODIFY 失败）；置 0 语义：双模/单模阶段不受影响（存量 count 均非 NULL，
-- 本 UPDATE 仅命中固定托槽阶段与异常脏数据）
UPDATE `stage` SET `count` = 0 WHERE `count` IS NULL;
ALTER TABLE `stage` MODIFY COLUMN `count` INT NOT NULL COMMENT '总副数（双模为组数，每组 = 软膜 + 硬膜两副）';

-- ───────────────────────── 4. timeline_record：删除疼痛可空列 ─────────────────────────
-- 数据丢失声明：疼痛数据不保留，仅紧急回退用
ALTER TABLE `timeline_record` DROP COLUMN `pain_part`, DROP COLUMN `pain_score`;
