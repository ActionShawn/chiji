-- 管理后台 P2：采集层 5 张表
-- login_log / usage_session / api_metric_hourly / slow_api_log / stat_daily
-- 主键均为后端雪花 ID（IdType.ASSIGN_ID），不使用数据库自增

-- 登录日志：每次登录成功 1 条
CREATE TABLE IF NOT EXISTS `login_log` (
    `id`       BIGINT      NOT NULL COMMENT '主键，雪花 ID',
    `user_id`  BIGINT      NOT NULL COMMENT '用户 ID',
    `login_at` DATETIME(3) NOT NULL COMMENT '登录时刻',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_login_at` (`login_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 使用会话：App onShow→onHide 一段停留（前端补报，client_session_id 幂等防重）
CREATE TABLE IF NOT EXISTS `usage_session` (
    `id`                BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `user_id`           BIGINT       NOT NULL COMMENT '用户 ID',
    `client_session_id` VARCHAR(64)  NOT NULL COMMENT '前端生成幂等键（补报防重）',
    `enter_at`          DATETIME(3)  NOT NULL COMMENT '会话进入时刻',
    `duration_sec`      INT          NOT NULL COMMENT '停留秒数',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_session` (`client_session_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_enter_at` (`enter_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='使用会话（App 级埋点）';

-- 接口小时聚合：内存计数器每 5 分钟增量刷盘累加（永久保留）
CREATE TABLE IF NOT EXISTS `api_metric_hourly` (
    `id`        BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `stat_date` DATE         NOT NULL COMMENT '统计日期（Asia/Shanghai）',
    `hour`      TINYINT      NOT NULL COMMENT '小时 0-23',
    `api`       VARCHAR(190) NOT NULL COMMENT '归一化接口模板，如 /api/feedback/{id}/reply',
    `cnt`       BIGINT       NOT NULL DEFAULT 0 COMMENT '调用数',
    `err_cnt`   BIGINT       NOT NULL DEFAULT 0 COMMENT '失败数（含抛异常与 4xx/5xx）',
    `slow_cnt`  BIGINT       NOT NULL DEFAULT 0 COMMENT '慢请求数（>阈值）',
    `max_ms`    INT          NOT NULL DEFAULT 0 COMMENT '最大耗时 ms',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date_hour_api` (`stat_date`, `hour`, `api`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口小时聚合';

-- 慢请求/错误明细：阈值采集，保留 90 天
CREATE TABLE IF NOT EXISTS `slow_api_log` (
    `id`         BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `api`        VARCHAR(190) NOT NULL COMMENT '归一化接口模板',
    `user_id`    BIGINT       NULL COMMENT '登录用户 ID（未登录请求为 NULL）',
    `cost_ms`    INT          NOT NULL COMMENT '耗时 ms',
    `success`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    `error_msg`  VARCHAR(500) NULL COMMENT '异常摘要（截断 500）',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_api` (`api`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='慢请求/错误明细';

-- 运营日汇总：00:05 聚合前一日，先删后插幂等（永久保留）
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营日汇总';
