-- D:\Java Work Place\personal-develop\teeth-trace\server\src\main\resources\db\schema.sql
-- 齿迹 · 隐形牙套矫正记录核心表结构
-- MySQL 8.0 / InnoDB / utf8mb4_unicode_ci
-- 说明：
--   1) 时间字段 created_at / updated_at 由后端 MyMetaObjectHandler 自动填充，此处仅定义类型；
--   2) 逻辑删除统一 deleted TINYINT(1) NOT NULL DEFAULT 0；
--   3) 关联表 record_tag_relation 无审计/逻辑删除列；
--   4) 本文件不会被 Spring 自动执行（置于 db/ 子目录且 init mode 为 embedded），仅供人工执行。

-- 用户
CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `openid`     VARCHAR(64)  NOT NULL COMMENT '微信 openid，全局唯一',
    `nickname`   VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    `phone`           VARCHAR(20)  DEFAULT NULL COMMENT '手机号（可选，绑定后填入）',
    `treatment_type`  VARCHAR(32)  DEFAULT NULL COMMENT '治疗方式：TreatmentTypeEnum.name()，CLEAR_ALIGNER(隐形)/FIXED_BRACKET(固定托槽)/LINGUAL(舌侧)，未选择为 null',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_openid` (`openid`) COMMENT 'openid 全局唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 矫正阶段
CREATE TABLE IF NOT EXISTS `stage` (
    `id`               BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`          BIGINT      NOT NULL COMMENT '所属用户 ID',
    `name`             VARCHAR(64) NOT NULL COMMENT '阶段名称（形如「第一阶段 · 主力矫正」）',
    `count`            INT         NOT NULL COMMENT '总副数（双模为组数，每组 = 软膜 + 硬膜两副）',
    `days_per_aligner` INT         DEFAULT NULL COMMENT '每副佩戴天数（单模有值；双模为 null，改由软/硬膜天数决定）',
    `mode`             VARCHAR(32) NOT NULL DEFAULT 'CLEAR_SINGLE' COMMENT '记录模式：RecordModeEnum.name()，CLEAR_SINGLE(单模)/CLEAR_DUAL(双模)，存量数据归入单模',
    `soft_days`        INT         DEFAULT NULL COMMENT '双模：软膜佩戴天数（每组前段，牙齿初步移动）',
    `hard_days`        INT         DEFAULT NULL COMMENT '双模：硬膜佩戴天数（每组后段，同序号力量更强）',
    `start_date`       DATE        DEFAULT NULL COMMENT '开始日期（可空）',
    `status`           VARCHAR(16) NOT NULL DEFAULT 'ENDED' COMMENT '阶段状态：StageStatusEnum.name()，ACTIVE(启用)/ENDED(结束)，同一时间至多一个 ACTIVE',
    `sort_order`       INT         NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    `created_at`       DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`       DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`          TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_stage_user` (`user_id`, `sort_order`) COMMENT '按用户取阶段列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='矫正阶段表';

-- 牙套副（阶段内迷你时间轴节点）
CREATE TABLE IF NOT EXISTS `aligner` (
    `id`          BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `stage_id`    BIGINT      NOT NULL COMMENT '所属阶段 ID',
    `num`         INT         NOT NULL COMMENT '副序号（第X副，阶段内从 1 递增；双模下全局连续：软奇硬偶）',
    `film_type`   VARCHAR(16) DEFAULT NULL COMMENT '膜片类型：AlignerFilmEnum.name()，SOFT(软膜)/HARD(硬膜)，仅双模阶段有值',
    `state`       VARCHAR(16) NOT NULL DEFAULT 'FUTURE' COMMENT '佩戴状态：AlignerStateEnum.name()，DONE/ACTIVE/FUTURE',
    `start_date`  DATE        DEFAULT NULL COMMENT '佩戴开始日期（未开始为 null）',
    `end_date`    DATE        DEFAULT NULL COMMENT '佩戴结束日期（进行中/未开始为 null）',
    `current_day` INT         DEFAULT NULL COMMENT '当前佩戴至第几天（仅 ACTIVE 有值）',
    `total_days`  INT         DEFAULT NULL COMMENT '应佩戴总天数（默认取所属阶段每副天数）',
    `created_at`  DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_aligner_stage` (`stage_id`, `num`) COMMENT '按阶段取牙套副并按序号排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='牙套副表';

-- 时光轴记录
CREATE TABLE IF NOT EXISTS `timeline_record` (
    `id`          BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`     BIGINT      NOT NULL COMMENT '所属用户 ID',
    `stage_id`    BIGINT      NOT NULL COMMENT '所属阶段 ID',
    `aligner_id`  BIGINT      DEFAULT NULL COMMENT '所属牙套副 ID（阶段切换类记录可为 null）',
    `media_kind`  VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '内容类型：RecordKindEnum.name()，TEXT/IMAGE/VIDEO/MIXED',
    `text`        TEXT        NOT NULL COMMENT '记录正文（纯文本）',
    `record_date` DATE        NOT NULL COMMENT '记录日期',
    `wear_hours`  INT         DEFAULT NULL COMMENT '佩戴时长（小时，可空；有值则徽标显示「22h」）',
    `badge_type`  VARCHAR(16) DEFAULT NULL COMMENT '特殊徽标：RecordBadgeTypeEnum.name()，FOLLOW_UP/MILESTONE/STAGE_SWITCH（与 wear_hours 互斥）',
    `created_at`  DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_record_user_date` (`user_id`, `record_date`) COMMENT '按用户取时间轴并按日期排序',
    KEY `idx_record_stage` (`stage_id`) COMMENT '按阶段查记录',
    KEY `idx_record_aligner` (`aligner_id`) COMMENT '按牙套副查记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时光轴记录表';

-- 记录媒体
CREATE TABLE IF NOT EXISTS `record_media` (
    `id`         BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `record_id`  BIGINT       NOT NULL COMMENT '所属记录 ID',
    `type`       VARCHAR(16)  NOT NULL COMMENT '媒体类型：MediaKindEnum.name()，IMAGE/VIDEO',
    `url`        VARCHAR(512) NOT NULL COMMENT '媒体文件地址（OSS key 或 CDN URL）',
    `duration`   INT          DEFAULT NULL COMMENT '视频时长（秒），图片为 null',
    `caption`    VARCHAR(255) DEFAULT NULL COMMENT '说明文字（可空）',
    `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '展示排序，越小越靠前',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_media_record` (`record_id`) COMMENT '按记录取媒体列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录媒体表（对比素材库的数据源）';

-- 标签（user_id=0 为系统内置预设：换牙套/复诊/疼痛/适应中/小进步/里程碑/日常）
CREATE TABLE IF NOT EXISTS `tag` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户 ID；0 = 系统内置预设',
    `name`       VARCHAR(32) NOT NULL COMMENT '标签名（同一用户下唯一）',
    `color_hex`  VARCHAR(7)  DEFAULT NULL COMMENT '标签颜色，形如 #FF6B6B',
    `sort_order` INT         NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_user_name` (`user_id`, `name`) COMMENT '同一用户（含系统预设）下标签名唯一',
    KEY `idx_tag_user` (`user_id`, `sort_order`) COMMENT '按用户取标签列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- 记录-标签关联
CREATE TABLE IF NOT EXISTS `record_tag_relation` (
    `record_id` BIGINT NOT NULL COMMENT '记录 ID',
    `tag_id`    BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (`record_id`, `tag_id`) COMMENT '联合主键',
    KEY `idx_rtr_tag_id` (`tag_id`) COMMENT '按标签反向查询记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录-标签关联表（多对多，无业务字段）';

-- 对比印记
CREATE TABLE IF NOT EXISTS `compare_milestone` (
    `id`               BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`          BIGINT      NOT NULL COMMENT '所属用户 ID',
    `name`             VARCHAR(64) NOT NULL COMMENT '印记名称（最多 30 字）',
    `before_media_id`  BIGINT      NOT NULL COMMENT '对比前素材（record_media.id）',
    `after_media_id`   BIGINT      NOT NULL COMMENT '对比后素材（record_media.id）',
    `created_at`       DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`       DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`          TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_milestone_user` (`user_id`) COMMENT '按用户取印记列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对比印记表';

-- 消息
CREATE TABLE IF NOT EXISTS `message` (
    `id`         BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT       NOT NULL COMMENT '接收用户 ID',
    `category`   VARCHAR(24)  NOT NULL COMMENT '分类：MessageCategoryEnum.name()，RECORD_REMINDER/STAGE_UPDATE/FELLOW_TRAVELER/SYSTEM_CARE',
    `title`      VARCHAR(128) NOT NULL COMMENT '标题',
    `body`       TEXT         NOT NULL COMMENT '正文',
    `read`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读（对应前端 status unread/read）',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_message_user` (`user_id`, `created_at`) COMMENT '按用户取消息并按时间倒序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- 用户设置
CREATE TABLE IF NOT EXISTS `user_setting` (
    `id`                BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`           BIGINT      NOT NULL COMMENT '用户 ID（一对一，全局唯一）',
    `theme_mode`        VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT '主题模式：ThemeModeEnum.name()，LIGHT/DARK/AUTO',
    `font_size`         VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '字体大小：FontSizeEnum.name()，SMALL/MEDIUM/LARGE',
    `enable_cloud_sync` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否启用云同步',
    `reminder_time`     TIME        DEFAULT NULL COMMENT '每日提醒时间（可选，与「记录提醒」消息呼应）',
    `created_at`        DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`        DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`           TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_setting_user` (`user_id`) COMMENT '每用户至多一条设置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置表';

-- 意见反馈
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

-- 反馈图片
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
