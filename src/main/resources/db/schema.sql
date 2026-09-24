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
    `role`            VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：UserRoleEnum.name()，USER(普通用户)/ADMIN(管理员)',
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
    `view_type`  VARCHAR(16)  DEFAULT NULL COMMENT '拍摄视角类别：FRONT_CLOSED/FRONT_SMILE/SIDE/ARCH_UPPER/ARCH_LOWER/NORMAL，历史数据与视频为 NULL',
    `tags`       VARCHAR(128) DEFAULT NULL COMMENT '自定义标签，逗号分隔（最多3个、单个≤8字），历史数据与视频为 NULL',
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
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`       BIGINT       NOT NULL COMMENT '接收用户 ID',
    `category`      VARCHAR(24)  NOT NULL COMMENT '分类：MessageCategoryEnum.name()，提醒类落 WEAR(佩戴提醒)/PROGRESS(佩戴进度)/CLINIC(复诊提醒)/SYSTEM_CARE(问候)，存量兼容 RECORD_REMINDER/STAGE_UPDATE/FELLOW_TRAVELER',
    `reminder_type` VARCHAR(32)  DEFAULT NULL COMMENT '提醒类型：WearReminderTypeEnum.name()（WEAR_* 佩戴组 / ALIGNER_CHANGE 等进度组），存量消息为 null',
    `scene_date`    DATE         DEFAULT NULL COMMENT '业务场景日期（去重维度：同用户同类型同日期至多一条提醒）',
    `priority`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '优先级：0 低 / 1 普通 / 2 高（供未来排序）',
    `push_status`   VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT '订阅消息推送状态：NONE(未推送)/PUSHED(已推送)，仅换副提醒等已开通订阅通道的类型会回写 PUSHED',
    `pushed_at`     DATETIME     DEFAULT NULL COMMENT '推送时间（订阅消息下发成功时回写）',
    `title`         VARCHAR(128) NOT NULL COMMENT '标题',
    `body`          TEXT         NOT NULL COMMENT '正文',
    `read`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读（对应前端 status unread/read）',
    `created_at`    DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_message_user` (`user_id`, `created_at`) COMMENT '按用户取消息并按时间倒序',
    UNIQUE KEY `uk_message_user_type_date` (`user_id`, `reminder_type`, `scene_date`) COMMENT '同用户同提醒类型同场景日期至多一条消息（去重；NULL 不参与唯一约束）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- 用户设置
CREATE TABLE IF NOT EXISTS `user_setting` (
    `id`                BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`           BIGINT      NOT NULL COMMENT '用户 ID（一对一，全局唯一）',
    `theme_mode`        VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT '主题模式：ThemeModeEnum.name()，LIGHT/DARK/AUTO',
    `font_size`         VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '字体大小：FontSizeEnum.name()，SMALL/MEDIUM/LARGE',
    `enable_cloud_sync` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否启用云同步',
    `reminder_time`     TIME        DEFAULT NULL COMMENT '每日提醒时间（可选，与「记录提醒」消息呼应）',
    `goal_sec`          INT         NOT NULL DEFAULT 72000 COMMENT '每日佩戴目标（秒，默认 20h=72000；可调 18.0–22.0h，步进 0.5h）',
    `notif_master`      TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '通知总开关：0 关闭（不再生成/归档提醒消息）/ 1 开启',
    `notif_popup`       TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '弹窗提醒开关：0 关闭（提醒仅信箱可见）',
    `notif_badge`       TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '信箱红点/角标开关：0 关闭',
    `preset_mode`       VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT '通知预设模式：NotificationPresetEnum.name()，STANDARD(标准)/IMPORTANT_ONLY(仅重要)/CUSTOM(手工调整)',
    `dnd_enabled`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '勿扰时段开关：仅抑制即时弹窗，信箱消息照常归档（默认关闭，预设 22:00–08:00）',
    `dnd_start`         TIME        NOT NULL DEFAULT '22:00:00' COMMENT '勿扰开始时间',
    `dnd_end`           TIME        NOT NULL DEFAULT '08:00:00' COMMENT '勿扰结束时间',
    `aligner_remind_time`   TIME    NOT NULL DEFAULT '07:00:00' COMMENT '换副提醒时间（Asia/Shanghai，仅整点，默认早 7 点，可改）',
    `aligner_remind_offset` TINYINT NOT NULL DEFAULT 0 COMMENT '换副提醒时机偏移：-1 到期前一天 / 0 到期当天 / 1 到期后一天',
    `clinic_remind_time`    TIME    NOT NULL DEFAULT '07:00:00' COMMENT '就诊提醒时刻（Asia/Shanghai，仅整点，默认早 7 点）',
    `clinic_book_time`      TIME    NOT NULL DEFAULT '07:00:00' COMMENT '预约提醒时刻（Asia/Shanghai，仅整点，默认早 7 点，独立于就诊提醒时刻）',
    `clinic_visit_offset`   TINYINT NOT NULL DEFAULT 0 COMMENT '就诊提醒提前天数：0 当天 / 1~2 提前 N 天（默认当天）',
    `clinic_book_offset`    TINYINT NOT NULL DEFAULT 2 COMMENT '预约提醒提前天数：0~2 天（默认前 2 天，仅隐形最终副生效）',
    `created_at`        DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at`        DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`           TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_setting_user` (`user_id`) COMMENT '每用户至多一条设置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置表';

-- 复诊档案：已确认日程与就诊记录（同表，status 区分）
CREATE TABLE IF NOT EXISTS `clinic_visit` (
    `id`                 BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`            BIGINT       NOT NULL COMMENT '所属用户 ID',
    `visit_date`         DATE         NOT NULL COMMENT '复诊日期',
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'PLANNED' COMMENT '状态：ClinicVisitStatusEnum.name()，PLANNED(待复诊)/DONE(已完成)',
    `clinic_name`        VARCHAR(64)  DEFAULT NULL COMMENT '诊所/医院名称（可空，预填上次值）',
    `doctor_name`        VARCHAR(64)  DEFAULT NULL COMMENT '医生姓名（可空）',
    `remark`             VARCHAR(255) DEFAULT NULL COMMENT '备注（可空，用户自由填写）',
    `content`            VARCHAR(512) DEFAULT NULL COMMENT '就诊内容摘要（完成后补录）',
    `next_visit_date`    DATE         DEFAULT NULL COMMENT '医生约定的下次复诊日期（填写即自动生成下一条 PLANNED）',
    `stage_id`           BIGINT       DEFAULT NULL COMMENT '创建时关联阶段 ID（可空，仅展示用）',
    `aligner_id`         BIGINT       DEFAULT NULL COMMENT '创建时关联牙套副 ID（可空，仅隐形有值，仅展示用）',
    `timeline_record_id` BIGINT       DEFAULT NULL COMMENT '完成时同步生成的时光轴记录 ID（可空）',
    `remind_offset_days` INT          DEFAULT NULL COMMENT '就诊提醒提前天数（0~3，null=用用户默认配置）',
    `created_at`         DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`         DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_clinic_visit_user_date` (`user_id`, `visit_date`) COMMENT '按用户取复诊日程/记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复诊档案表';

-- 矫正花费流水
CREATE TABLE IF NOT EXISTS `clinic_expense` (
    `id`           BIGINT        NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`      BIGINT        NOT NULL COMMENT '所属用户 ID',
    `category_id`  BIGINT        NOT NULL COMMENT '分类 ID（clinic_expense_category）',
    `visit_id`     BIGINT        DEFAULT NULL COMMENT '关联复诊记录 ID（可空；复诊费一键生成时回填）',
    `expense_date` DATE          NOT NULL COMMENT '花费日期',
    `amount`       DECIMAL(10,2) NOT NULL COMMENT '金额（元，两位小数）',
    `note`         VARCHAR(255)  DEFAULT NULL COMMENT '备注（可空）',
    `created_at`   DATETIME      NOT NULL COMMENT '创建时间',
    `updated_at`   DATETIME      NOT NULL COMMENT '更新时间',
    `deleted`      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_clinic_expense_user_date` (`user_id`, `expense_date`) COMMENT '按用户取花费流水'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='矫正花费流水表';

-- 花费分类（user_id=0 为系统内置：牙套费/复诊费/保持器/洁牙/其他，照 tag 表模式）
CREATE TABLE IF NOT EXISTS `clinic_expense_category` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户 ID；0 = 系统内置预设',
    `name`       VARCHAR(32) NOT NULL COMMENT '分类名（同一用户下唯一）',
    `sort_order` INT         NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_clinic_cat_user_name` (`user_id`, `name`) COMMENT '同一用户（含系统预设）下分类名唯一',
    KEY `idx_clinic_cat_user` (`user_id`, `sort_order`) COMMENT '按用户取分类列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='矫正花费分类表';

-- 首页常用工具配置（每用户一行；工具 key 有序列表，默认顺序由客户端字典承载）
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

-- 佩戴会话（物理佩戴段：一戴一摘 = 一行，跨午夜不预拆，自然日归属在查询/结算时切分）
CREATE TABLE IF NOT EXISTS `wear_session` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户 ID',
    `aligner_id` BIGINT      DEFAULT NULL COMMENT '打卡时当前佩戴的牙套副 ID（本副统计用；无 ACTIVE 副时为空）',
    `source`     VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：WearSourceEnum.name()，MANUAL(打卡/晨起补记)/MAKEUP(校准补录)',
    `makeup_for` DATE        DEFAULT NULL COMMENT '补录目标自然日（仅 source=MAKEUP 有值；补录段按该日全额归属）',
    `started_at` DATETIME(3) NOT NULL COMMENT '戴上时间（可回填到过去；跨午夜不预拆）',
    `ended_at`   DATETIME(3) DEFAULT NULL COMMENT '摘下时间（佩戴中为 null）',
    `edited`     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否被手动修正过（MANUAL 编辑后置 1，展示「修正」标）',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL COMMENT '更新时间',
    `deleted`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_wear_session_user_time` (`user_id`, `started_at`) COMMENT '按用户取佩戴段（结算/今日切分）',
    KEY `idx_wear_session_user_aligner` (`user_id`, `aligner_id`) COMMENT '按用户取某副佩戴段（本副统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='佩戴会话表（物理佩戴段）';

-- 佩戴会话编辑留痕（每次编辑/删除写一行，改前/改后 + 积分预留，人工审核可追溯）
CREATE TABLE IF NOT EXISTS `wear_session_edit_log` (
    `id`           BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `session_id`   BIGINT      NOT NULL COMMENT '关联会话 id',
    `user_id`      BIGINT      NOT NULL COMMENT '操作用户 ID',
    `edit_type`    VARCHAR(16) NOT NULL COMMENT '操作类型：TIME_START / TIME_END / CREATE / DELETE',
    `field`        VARCHAR(32) NOT NULL COMMENT '改动字段：started_at / ended_at',
    `before_value` DATETIME(3) DEFAULT NULL COMMENT '修改前值',
    `after_value`  DATETIME(3) DEFAULT NULL COMMENT '修改后值',
    `points_cost`  INT         NOT NULL DEFAULT 0 COMMENT '本次消耗积分（当前 0，后续积分系统启用后填写）',
    `created_at`   DATETIME    NOT NULL COMMENT '创建时间',
    `deleted`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_edit_log_session` (`session_id`) COMMENT '按会话查留痕',
    KEY `idx_edit_log_user` (`user_id`) COMMENT '按用户查留痕'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='佩戴会话编辑留痕表';

-- 存量库升级（已有 wear_session 表时执行一次）：ALTER TABLE `wear_session` ADD COLUMN `edited` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被手动修正过';

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

-- 登录日志（管理后台 P2 采集层：登录成功异步落 1 条）
CREATE TABLE IF NOT EXISTS `login_log` (
    `id`         BIGINT      NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`    BIGINT      NOT NULL COMMENT '用户 ID',
    `login_at`   DATETIME(3) NOT NULL COMMENT '登录时刻',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间（代码插入时自动填充）',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_login_at` (`login_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

-- 使用会话（App 级埋点：onShow→onHide 一段停留，client_session_id 幂等防重）
CREATE TABLE IF NOT EXISTS `usage_session` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`           BIGINT       NOT NULL COMMENT '用户 ID',
    `client_session_id` VARCHAR(64)  NOT NULL COMMENT '前端生成幂等键（补报防重）',
    `enter_at`          DATETIME(3)  NOT NULL COMMENT '会话进入时刻',
    `duration_sec`      INT          NOT NULL COMMENT '停留秒数',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_session` (`client_session_id`) COMMENT '补报防重',
    KEY `idx_user` (`user_id`),
    KEY `idx_enter_at` (`enter_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='使用会话（App 级埋点）';

-- 接口小时聚合（内存计数器每 5 分钟增量刷盘累加，永久保留）
CREATE TABLE IF NOT EXISTS `api_metric_hourly` (
    `id`        BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `stat_date` DATE         NOT NULL COMMENT '统计日期（Asia/Shanghai）',
    `hour`      TINYINT      NOT NULL COMMENT '小时 0-23',
    `api`       VARCHAR(190) NOT NULL COMMENT '归一化接口模板，如 /api/feedback/{id}/reply',
    `cnt`       BIGINT       NOT NULL DEFAULT 0 COMMENT '调用数',
    `err_cnt`   BIGINT       NOT NULL DEFAULT 0 COMMENT '失败数（含抛异常与 4xx/5xx）',
    `slow_cnt`  BIGINT       NOT NULL DEFAULT 0 COMMENT '慢请求数（>阈值）',
    `max_ms`    INT          NOT NULL DEFAULT 0 COMMENT '最大耗时 ms',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_hour_api` (`stat_date`, `hour`, `api`) COMMENT '累加 upsert 锚点'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口小时聚合';

-- 慢请求/错误明细（阈值采集，保留 90 天）
CREATE TABLE IF NOT EXISTS `slow_api_log` (
    `id`         BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `api`        VARCHAR(190) NOT NULL COMMENT '归一化接口模板',
    `user_id`    BIGINT       NULL COMMENT '登录用户 ID（未登录请求为 NULL）',
    `cost_ms`    INT          NOT NULL COMMENT '耗时 ms',
    `success`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    `error_msg`  VARCHAR(500) NULL COMMENT '异常摘要（截断 500）',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_created_at` (`created_at`) COMMENT '保留期清理',
    KEY `idx_api` (`api`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='慢请求/错误明细';

-- 运营日汇总（00:05 聚合前一日，先删后插幂等，永久保留）
CREATE TABLE IF NOT EXISTS `stat_daily` (
    `stat_date`   DATE        NOT NULL COMMENT '统计日期',
    `new_users`   INT         NOT NULL DEFAULT 0 COMMENT '新增用户数（user.created_at）',
    `login_users` INT         NOT NULL DEFAULT 0 COMMENT '登录用户数（distinct）',
    `usage_sec`   BIGINT      NOT NULL DEFAULT 0 COMMENT '使用时长总和（秒）',
    `usage_users` INT         NOT NULL DEFAULT 0 COMMENT '使用用户数（distinct）',
    `api_calls`   BIGINT      NOT NULL DEFAULT 0 COMMENT '接口调用总数',
    `new_records` INT         NOT NULL DEFAULT 0 COMMENT '新增记录数',
    `new_images`  INT         NOT NULL DEFAULT 0 COMMENT '新增图片数',
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运营日汇总';
