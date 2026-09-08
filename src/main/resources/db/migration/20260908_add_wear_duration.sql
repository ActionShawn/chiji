-- 齿迹 · 佩戴时长 + 通知中心（提醒类型 / 通知设置）支持
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行
--   2) 完整结构见 db/schema.sql，两者保持同步
--   3) 佩戴时长模型：wear_session 为物理佩戴段（一戴一摘 = 一行，跨午夜不预拆，自然日归属在查询/结算时切分）；
--      wear_daily_summary 为结算物化（00:30 定时结算昨天 + 客户端懒结算，天然日 = Asia/Shanghai）
--   4) 通知中心：message 扩展提醒类型/场景日期字段用于类型化去重；user_setting 扩展单值偏好；
--      user_notification_config 存每提醒类型开关明细
--   5) 存量 message 行 reminder_type/scene_date 为 null（MySQL 唯一索引允许多个 NULL，不冲突）

ALTER TABLE `message`
    ADD COLUMN `reminder_type` VARCHAR(32) DEFAULT NULL COMMENT '提醒类型：WearReminderTypeEnum.name()（WEAR_* 佩戴组 / ALIGNER_CHANGE 等进度组），存量消息为 null' AFTER `category`,
    ADD COLUMN `scene_date`    DATE        DEFAULT NULL COMMENT '业务场景日期（去重维度：同用户同类型同日期至多一条提醒）' AFTER `reminder_type`,
    ADD COLUMN `priority`      TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '优先级：0 低 / 1 普通 / 2 高（供未来排序）' AFTER `scene_date`,
    ADD COLUMN `push_status`   VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '订阅消息推送状态：NONE(未推送)/PUSHED(已推送)，当前无订阅通道恒为 NONE' AFTER `priority`,
    ADD COLUMN `pushed_at`     DATETIME    DEFAULT NULL COMMENT '推送时间（当前无订阅通道，恒为 null）' AFTER `push_status`,
    ADD UNIQUE KEY `uk_message_user_type_date` (`user_id`, `reminder_type`, `scene_date`)
        COMMENT '同用户同提醒类型同场景日期至多一条消息（去重；NULL 不参与唯一约束）';

ALTER TABLE `user_setting`
    ADD COLUMN `goal_sec`     INT         NOT NULL DEFAULT 72000 COMMENT '每日佩戴目标（秒，默认 20h=72000；可调 18.0–22.0h，步进 0.5h）' AFTER `reminder_time`,
    ADD COLUMN `notif_master` TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '通知总开关：0 关闭（不再生成/归档提醒消息）/ 1 开启' AFTER `goal_sec`,
    ADD COLUMN `notif_popup`  TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '弹窗提醒开关：0 关闭（提醒仅信箱可见）' AFTER `notif_master`,
    ADD COLUMN `notif_badge`  TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '信箱红点/角标开关：0 关闭' AFTER `notif_popup`,
    ADD COLUMN `preset_mode`  VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT '通知预设模式：NotificationPresetEnum.name()，STANDARD(标准)/IMPORTANT_ONLY(仅重要)/CUSTOM(手工调整)' AFTER `notif_badge`,
    ADD COLUMN `dnd_enabled`  TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '勿扰时段开关：仅抑制即时弹窗，信箱消息照常归档（默认关闭，预设 22:00–08:00）' AFTER `preset_mode`,
    ADD COLUMN `dnd_start`    TIME        NOT NULL DEFAULT '22:00:00' COMMENT '勿扰开始时间' AFTER `dnd_enabled`,
    ADD COLUMN `dnd_end`      TIME        NOT NULL DEFAULT '08:00:00' COMMENT '勿扰结束时间' AFTER `dnd_start`;

-- 佩戴会话（物理佩戴段）
CREATE TABLE IF NOT EXISTS `wear_session` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户 ID',
    `aligner_id` BIGINT      DEFAULT NULL COMMENT '打卡时当前佩戴的牙套副 ID（本副统计用；无 ACTIVE 副时为空）',
    `source`     VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：WearSourceEnum.name()，MANUAL(打卡/晨起补记)/MAKEUP(校准补录)',
    `makeup_for` DATE        DEFAULT NULL COMMENT '补录目标自然日（仅 source=MAKEUP 有值；补录段按该日全额归属）',
    `started_at` DATETIME(3) NOT NULL COMMENT '戴上时间（可回填到过去；跨午夜不预拆）',
    `ended_at`   DATETIME(3) DEFAULT NULL COMMENT '摘下时间（佩戴中为 null）',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_wear_session_user_time` (`user_id`, `started_at`) COMMENT '按用户取佩戴段（结算/今日切分）',
    KEY `idx_wear_session_user_aligner` (`user_id`, `aligner_id`) COMMENT '按用户取某副佩戴段（本副统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='佩戴会话表（物理佩戴段）';

-- 每日佩戴结算（物化，天然日 = Asia/Shanghai）
CREATE TABLE IF NOT EXISTS `wear_daily_summary` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`      BIGINT      NOT NULL COMMENT '所属用户 ID',
    `summary_date` DATE        NOT NULL COMMENT '结算自然日（Asia/Shanghai）',
    `wear_sec`     INT         NOT NULL DEFAULT 0 COMMENT '当日实际佩戴总秒（MANUAL + MAKEUP，达标基数）',
    `manual_sec`   INT         NOT NULL DEFAULT 0 COMMENT '其中手工打卡/晨起补记（MANUAL）秒数',
    `makeup_sec`   INT         NOT NULL DEFAULT 0 COMMENT '其中校准补录（MAKEUP）秒数（展示与正常分开统计）',
    `goal_sec`     INT         NOT NULL DEFAULT 72000 COMMENT '结算时当日目标快照（秒），保证历史达标口径稳定',
    `level`        VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '达标等级：WearLevelEnum.name()，FULL(≥目标)/PARTIAL(≥目标-4h)/NONE(<目标-4h)',
    `settled_at`   DATETIME    DEFAULT NULL COMMENT '结算/最近重算时间',
    `created_at`   DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`   DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wear_summary_user_date` (`user_id`, `summary_date`) COMMENT '每用户每自然日至多一条结算'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日佩戴结算表';

-- 用户通知类型开关
CREATE TABLE IF NOT EXISTS `user_notification_config` (
    `id`            BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`       BIGINT      NOT NULL COMMENT '所属用户 ID',
    `reminder_type` VARCHAR(32) NOT NULL COMMENT '提醒类型：WearReminderTypeEnum.name()',
    `enabled`       TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否开启',
    `created_at`    DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`    DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notif_cfg_user_type` (`user_id`, `reminder_type`) COMMENT '每用户每提醒类型至多一条开关',
    KEY `idx_notif_cfg_user` (`user_id`) COMMENT '按用户取通知配置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知类型开关表';
