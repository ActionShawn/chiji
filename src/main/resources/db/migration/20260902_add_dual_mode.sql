-- 齿迹 · 记录模式（单模/双模）支持
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行
--   2) 完整结构见 db/schema.sql，两者保持同步
--   3) 存量 stage.mode 由 DEFAULT 统一回填 CLEAR_SINGLE（与后端 normalizeMode 缺省行为一致，
--      老数据自动归入单模工作台）
--   4) days_per_aligner 由 NOT NULL 改为可空：双模阶段每副无统一佩戴天数（软/硬膜各由
--      soft_days/hard_days 决定），生成节点时逐副写入 total_days

ALTER TABLE `stage`
    MODIFY COLUMN `days_per_aligner` INT DEFAULT NULL COMMENT '每副佩戴天数（单模有值；双模为 null，改由软/硬膜天数决定）',
    ADD COLUMN `mode`      VARCHAR(32) NOT NULL DEFAULT 'CLEAR_SINGLE' COMMENT '记录模式：RecordModeEnum.name()，CLEAR_SINGLE(单模)/CLEAR_DUAL(双模)，存量数据归入单模' AFTER `days_per_aligner`,
    ADD COLUMN `soft_days` INT DEFAULT NULL COMMENT '双模：软膜佩戴天数（每组前段，牙齿初步移动）' AFTER `mode`,
    ADD COLUMN `hard_days` INT DEFAULT NULL COMMENT '双模：硬膜佩戴天数（每组后段，同序号力量更强）' AFTER `soft_days`;

ALTER TABLE `aligner`
    ADD COLUMN `film_type` VARCHAR(16) DEFAULT NULL COMMENT '膜片类型：AlignerFilmEnum.name()，SOFT(软膜)/HARD(硬膜)，仅双模阶段有值' AFTER `num`;
