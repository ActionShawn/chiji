-- 统一延迟任务队列（chiji-delay 组件）：业务事务内投递，调度泵 SKIP LOCKED 取出到期任务
-- 主键为后端雪花 ID（IdType.ASSIGN_ID），不使用数据库自增
-- 设计文档：docs/待开发/延迟队列组件设计方案.md

CREATE TABLE IF NOT EXISTS `delay_task` (
    `id`           BIGINT       NOT NULL COMMENT '主键，雪花 ID',
    `task_type`    VARCHAR(32)  NOT NULL COMMENT '任务类型，对应 DelayTaskHandler#type()',
    `owner_module` VARCHAR(16)  NOT NULL COMMENT '归属模块（wear/clinic/...），取自 DelayTaskHandler#owner()，运维页按模块筛选',
    `biz_key`      VARCHAR(64)  NOT NULL COMMENT '业务幂等键，如 takeoff-timeout:{sessionId}，一行一任务、状态机循环复用',
    `summary`      VARCHAR(128) DEFAULT NULL COMMENT '人类可读摘要，运维列表直读（投递方传入）',
    `payload`      JSON         DEFAULT NULL COMMENT '任务参数 JSON，handler 自行解析',
    `execute_at`   DATETIME(3)  NOT NULL COMMENT '计划触发时间（应用时钟计算，与 DB NOW(3) 偏差同 region 秒级）',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/DONE/CANCELLED/FAILED',
    `lease_until`  DATETIME(3)  DEFAULT NULL COMMENT 'RUNNING 租约到期时间，超时由泵回收重投',
    `retry_count`  INT          NOT NULL DEFAULT 0 COMMENT '已重试次数（租约回收与失败重试 +1，手动重试清零）',
    `executed_at`  DATETIME(3)  DEFAULT NULL COMMENT '最近一次实际开始执行时间（取出置 RUNNING 时由泵写入）',
    `finished_at`  DATETIME(3)  DEFAULT NULL COMMENT '终态时间（DONE/FAILED/CANCELLED 均记录）',
    `last_error`   VARCHAR(512) DEFAULT NULL COMMENT '最近一次失败原因（含业务跳过原因备注，如「无额度」）',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（投递时刻）',
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_delay_task_biz_key` (`biz_key`) COMMENT '业务幂等：同 biz_key 至多一行',
    KEY `idx_delay_task_due` (`status`, `execute_at`) COMMENT '泵取出期任务（FOR UPDATE SKIP LOCKED）',
    KEY `idx_delay_task_lease` (`status`, `lease_until`) COMMENT '租约到期回收扫描',
    KEY `idx_delay_task_finished` (`finished_at`) COMMENT '终态行按期清理',
    KEY `idx_delay_task_owner_status` (`owner_module`, `status`) COMMENT '运维页按模块筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一延迟任务队列';
