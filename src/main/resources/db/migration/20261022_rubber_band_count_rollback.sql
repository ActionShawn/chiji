-- 齿迹 · 固定托槽体验优化第 2 轮 · OPT-FB-04 橡皮筋计数迁移回滚脚本（2026-10-22）
-- 配套迁移：20261022_rubber_band_count.sql
-- 【执行前提】后端必须已回滚到依赖 done 列的旧版本（toggle 端点），再执行本脚本；
--   若后端仍在 increment 版上执行本回滚，写入将因 count 列缺失直接报错。
--
-- 数据丢失声明（评审风险 R8，前置写明）：
--   升级期间新增的次数（count > 1 的部分）不回填 done——done 仅承载勾选语义，
--   count>1 的日回滚后点阵回退为「已打卡」勾选态（done=1），次数明细丢失属预期内取舍。
--   count=0（含原已取消行与升级后未计数日）回填 done=0，维持取消态语义。

-- 1) 按 count 回填 done（count>0 → 1；count=0 → 0），done 原值自迁移后即冻结，此处为唯一恢复路径
UPDATE `rubber_band_record` SET `done` = CASE WHEN `count` > 0 THEN 1 ELSE 0 END;

-- 2) 删除 count 列，结构回到迁移前
ALTER TABLE `rubber_band_record` DROP COLUMN `count`;
