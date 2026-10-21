-- 齿迹 · 用户交流群动态二维码配置表（2026-10-21）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 单行配置表：config_key 唯一键锚定单行（当前仅 GROUP_QRCODE），读取方按空配置兜底、首次写入懒创建，
--      并发首写由唯一键 + 服务层 DuplicateKeyException 幂等回读兜底
--   3) 全新表先建表再发版对旧后端零影响；无 DROP / 无 ALTER 存量表，破坏性风险最低档
--   4) 回滚脚本：20261021_add_group_qrcode_config_rollback.sql（须先将后端回滚到不依赖本表的版本，再执行回滚）

CREATE TABLE IF NOT EXISTS `group_qrcode_config` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `config_key`    VARCHAR(32)  NOT NULL COMMENT '配置键（单行锚点，当前仅 GROUP_QRCODE=用户交流群二维码）',
    `image_file_id` VARCHAR(128) DEFAULT NULL COMMENT '云存储 fileID（预留列：当前二维码走 COS https 直链，此列暂不写入）',
    `image_url`     VARCHAR(512) NOT NULL COMMENT '二维码图片地址（https，COS 直链；用户端实时展示该地址）',
    `updated_by`    BIGINT       NOT NULL COMMENT '最后更新管理员用户 ID',
    `created_at`    DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL COMMENT '更新时间（即用户端展示的「更新于」时间）',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除（单行配置业务恒 0）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_qrcode_key` (`config_key`) COMMENT '单行配置：每个配置键至多一行'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户交流群二维码配置表（单行）';
