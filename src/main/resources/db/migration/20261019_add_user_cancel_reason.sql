-- 齿迹 · 注销原因匿名统计（账号注销功能，2026-10-19）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 全匿名：无 user_id、无 openid、无任何可回溯标识，仅用于整体汇总（原因分布 Top3 / 不填比例）
--   3) reason_codes 为注销原因码数组（JSON），非必填——全不选时落 NULL；reason_remark 选填，超长由后端截断
--   4) 回滚脚本：20261019_add_user_cancel_reason_rollback.sql

CREATE TABLE IF NOT EXISTS `user_cancel_reason` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `reason_codes`  JSON         DEFAULT NULL COMMENT '注销原因码数组，可为空（非必填）',
    `reason_remark` VARCHAR(200) DEFAULT NULL COMMENT '补充说明，可为空',
    `created_at`    DATETIME     NOT NULL COMMENT '注销时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='注销原因匿名统计';
