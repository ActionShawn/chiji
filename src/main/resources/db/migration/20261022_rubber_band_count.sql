-- 齿迹 · 固定托槽体验优化第 2 轮 · OPT-FB-04 橡皮筋打卡改次数计数（2026-10-22）
-- 依据：《固定托槽体验优化第2轮技术评审与任务拆解.md》第二节 SQL 裁决（方案 A：加列回填，done 冻结保留不删）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署 increment 版后端之前执行；加列为最低破坏性档位，旧后端兼容（upsertDone 不触碰 count），窗口宽松
--   3) 存量迁移：已打卡日（done=1）→ count=1（验收「今日 1 次」）；已取消行（done=0）→ count=0 且保留行
--      （不删行，维持评审红线 R7：唯一键槽位语义，禁逻辑删除行）
--   4) staging 先行验证清单：count 回填正确性（done=1→1 / done=0→0）抽样核对；唯一键不受影响；
--      回滚演练（见 20261022_rubber_band_count_rollback.sql）；耗时留档。全部逐项留档后方可上生产
--   5) 本批无 DROP COLUMN、无类型变更、无存量数据改写语义（UPDATE 仅给新增列赋值，done 原值不动）

ALTER TABLE `rubber_band_record`
    ADD COLUMN `count` INT NOT NULL DEFAULT 0
    COMMENT '当日累计更换次数（OPT-FB-04，done 语义升级；done 列冻结废弃，仅回滚兜底）' AFTER `record_date`;

-- 存量回填：直接按 done 原值搬运（done=1 → count=1；done=0 → count=0）
UPDATE `rubber_band_record` SET `count` = `done`;
