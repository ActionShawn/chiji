-- 齿迹 · 小齿档案（复诊日程 / 就诊记录 / 花费统计）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) clinic_visit 同表承载「已确认日程(PLANNED)」与「就诊记录(DONE)」，status 区分
--   3) clinic_expense_category：user_id=0 为系统内置五类（牙套费/复诊费/保持器/洁牙/其他），照 tag 表预设模式
--   4) user_setting 增复诊提醒单值偏好（提醒时刻 + 就诊提醒提前天数 + 预约提醒提前天数）

-- 复诊档案：已确认日程与就诊记录（同表，status 区分）
CREATE TABLE IF NOT EXISTS `clinic_visit` (
    `id`                 BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`            BIGINT       NOT NULL COMMENT '所属用户 ID',
    `visit_date`         DATE         NOT NULL COMMENT '复诊日期',
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'PLANNED' COMMENT '状态：ClinicVisitStatusEnum.name()，PLANNED(待复诊)/DONE(已完成)',
    `clinic_name`        VARCHAR(64)  DEFAULT NULL COMMENT '诊所/医院名称（可空，预填上次值）',
    `doctor_name`        VARCHAR(64)  DEFAULT NULL COMMENT '医生姓名（可空）',
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

-- 系统内置五类（user_id=0；INSERT IGNORE 防重复执行；id 取 3101~3105 固定小整数段，避开雪花段）
INSERT IGNORE INTO `clinic_expense_category` (`id`, `user_id`, `name`, `sort_order`, `created_at`, `updated_at`, `deleted`) VALUES
(3101, 0, '牙套费', 1, NOW(), NOW(), 0),
(3102, 0, '复诊费', 2, NOW(), NOW(), 0),
(3103, 0, '保持器', 3, NOW(), NOW(), 0),
(3104, 0, '洁牙',   4, NOW(), NOW(), 0),
(3105, 0, '其他',   5, NOW(), NOW(), 0);

-- user_setting 增复诊提醒单值偏好（与换副提醒 aligner_remind_* 语义并列）
ALTER TABLE `user_setting`
    ADD COLUMN `clinic_remind_time`  TIME NOT NULL DEFAULT '07:00:00'
        COMMENT '复诊提醒时刻（Asia/Shanghai，仅整点，默认早 7 点；就诊提醒与预约提醒共用）' AFTER `aligner_remind_offset`,
    ADD COLUMN `clinic_visit_offset` TINYINT NOT NULL DEFAULT 0
        COMMENT '就诊提醒提前天数：0 当天 / 1~3 提前 N 天（默认当天）' AFTER `clinic_remind_time`,
    ADD COLUMN `clinic_book_offset`  TINYINT NOT NULL DEFAULT 3
        COMMENT '预约提醒提前天数：0~3 天（默认前 3 天，仅隐形最终副生效）' AFTER `clinic_visit_offset`;
