-- 首页常用工具个人配置：每用户一行，工具 key 有序列表（客户端字典承载默认顺序与图标/地址）
-- 空 tool_keys 表示未自定义，客户端回退默认顺序
CREATE TABLE IF NOT EXISTS `user_tool_config` (
    `id`         BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT       NOT NULL COMMENT '用户 ID（一对一，全局唯一）',
    `tool_keys`  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '常用工具 key 有序列表（逗号分隔，如 timeline,compare,report,clinic；空串=客户端回退默认）',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tool_user` (`user_id`) COMMENT '每用户至多一条配置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页常用工具配置表';
