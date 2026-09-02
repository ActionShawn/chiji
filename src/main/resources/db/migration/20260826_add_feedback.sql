-- 齿迹 · 意见反馈模块建表
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行（IF NOT EXISTS 可重复执行）
--   2) 完整结构见 db/schema.sql，两者保持同步
--   3) status 预留状态机：PENDING(待处理)/PROCESSED(已处理)；reply/replied_at 为管理员回复预留

CREATE TABLE IF NOT EXISTS `feedback` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`      BIGINT       NOT NULL COMMENT '提交用户 ID',
    `content`      TEXT         NOT NULL COMMENT '意见正文',
    `contact_type` VARCHAR(16)  DEFAULT NULL COMMENT '联系方式类型：FeedbackContactTypeEnum.name()，PHONE/EMAIL，未留联系方式为 null',
    `contact`      VARCHAR(64)  DEFAULT NULL COMMENT '联系方式值（手机号或邮箱，可空）',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：FeedbackStatusEnum.name()，PENDING(待处理)/PROCESSED(已处理)',
    `reply`        VARCHAR(512) DEFAULT NULL COMMENT '管理员回复（预留）',
    `replied_at`   DATETIME     DEFAULT NULL COMMENT '回复时间（预留）',
    `created_at`   DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_feedback_user` (`user_id`, `id`) COMMENT '按用户查反馈（游标分页按 id 倒序）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意见反馈表';

CREATE TABLE IF NOT EXISTS `feedback_image` (
    `id`          BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `feedback_id` BIGINT       NOT NULL COMMENT '所属反馈 ID',
    `url`         VARCHAR(512) NOT NULL COMMENT '图片地址（COS URL）',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '展示排序，越小越靠前',
    `created_at`  DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_fbimg_feedback` (`feedback_id`) COMMENT '按反馈取图片列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈图片表';
