-- 齿迹 · 牙套换副提醒微信一次性订阅消息支持
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) user_setting 增换副提醒「时机偏移 + 提醒时间」单值偏好，与既有 reminder_time（每日记录提醒）语义不同
--   3) user_subscribe_quota 为微信一次性订阅消息的本地额度记账（微信不提供剩余额度查询接口，
--      授权 accept 一次 +1，发送成功一次 -1）

ALTER TABLE `user_setting`
    ADD COLUMN `aligner_remind_time`   TIME    NOT NULL DEFAULT '07:00:00'
        COMMENT '换副提醒时间（Asia/Shanghai，仅整点，默认早 7 点，可改）' AFTER `dnd_end`,
    ADD COLUMN `aligner_remind_offset` TINYINT NOT NULL DEFAULT 0
        COMMENT '换副提醒时机偏移：-1 到期前一天 / 0 到期当天 / 1 到期后一天' AFTER `aligner_remind_time`;

-- 一次性订阅消息额度（本地记账）
CREATE TABLE IF NOT EXISTS `user_subscribe_quota` (
    `id`             BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`        BIGINT      NOT NULL COMMENT '所属用户 ID',
    `scene`          VARCHAR(32) NOT NULL COMMENT '订阅场景：ALIGNER_CHANGE（换副提醒）',
    `remain`         INT         NOT NULL DEFAULT 0 COMMENT '本地记账剩余可下发次数（accept +1，发送成功 -1）',
    `accepted_total` INT         NOT NULL DEFAULT 0 COMMENT '累计授权次数（accept 次数，仅统计用）',
    `last_accept_at` DATETIME    DEFAULT NULL COMMENT '最近一次授权时间',
    `created_at`     DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`     DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_quota_user_scene` (`user_id`, `scene`) COMMENT '每用户每场景至多一条额度记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一次性订阅消息额度表';
