-- 齿迹 · AI 问答助手（问小齿）会话与消息落库
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) ai_conversation / ai_message 支撑「千问网页版」式的可回溯问答历史：
--      会话按用户隔离、游标分页；消息按会话升序回放，供前端加载与向 LLM 组装多轮上下文
--   3) 角色仅 user / assistant 两值，暂不支持图片等多模态消息

-- AI 问答会话
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户 ID',
    `title`      VARCHAR(64) DEFAULT NULL COMMENT '会话标题（首条提问截断生成）',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_conv_user` (`user_id`, `id`) COMMENT '按用户取会话（游标分页按 id 倒序）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 问答会话表';

-- AI 问答消息
CREATE TABLE IF NOT EXISTS `ai_message` (
    `id`              BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `conversation_id` BIGINT      NOT NULL COMMENT '所属会话 ID',
    `user_id`         BIGINT      NOT NULL COMMENT '所属用户 ID（冗余，权限校验与清理用）',
    `role`            VARCHAR(16) NOT NULL COMMENT '角色：user(提问) / assistant(回复)',
    `content`         TEXT        NOT NULL COMMENT '消息内容',
    `created_at`      DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`      DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_ai_msg_conv` (`conversation_id`, `id`) COMMENT '按会话取消息（升序回放）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 问答消息表';
