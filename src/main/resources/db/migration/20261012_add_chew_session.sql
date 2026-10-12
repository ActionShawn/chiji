-- 齿迹 · 咬胶计时（chew）会话表 + 首页工具位回填（2026-10-12）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署新后端（含 chew 模块的版本）之前执行；
--      chew_session 为全新表，先建表再发版对旧后端零影响
--   3) chew_session：咬胶会话（独立于 wear_session）。语义 = 一次咬胶行为，一行一段；
--      归属三元组 user_id + stage_id + aligner_id（stage/aligner 为「开始时刻」快照，允许 NULL=未归属档）；
--      净时长 = (ended_at - started_at) - pause_sec（服务端派生，不落库）；
--      跨日整段归 started_at 所在自然日，不拆分
--   4) 首页工具位回填：仅对「已自定义过工具」的用户（tool_keys <> ''）把 chew 追加到末尾（幂等）；
--      空配置用户走客户端默认回退，无需回填。FIND_IN_SET 保证重复执行不会产生重复 key
--   5) 回滚脚本：20261012_add_chew_session_rollback.sql（须先将后端回滚到不依赖 chew_session 的版本，再执行回滚）

CREATE TABLE IF NOT EXISTS `chew_session` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`           BIGINT       NOT NULL COMMENT '所属用户 ID',
    `stage_id`          BIGINT       DEFAULT NULL COMMENT '开始时刻阶段快照（当前 ACTIVE 阶段；无则 NULL=未归属档）',
    `aligner_id`        BIGINT       DEFAULT NULL COMMENT '开始时刻牙套副快照（当前 ACTIVE 副；无则 NULL=未归属档）',
    `started_at`        DATETIME(3)  NOT NULL COMMENT '开始时刻（服务端生成；「重来」重置为最新起点）',
    `ended_at`          DATETIME(3)  DEFAULT NULL COMMENT '结束时刻（进行中/暂停中未结束为 NULL）',
    `pause_sec`         INT          NOT NULL DEFAULT 0 COMMENT '已结算累计暂停秒（不含 paused_at 尚未结算的当前暂停段）',
    `paused_at`         DATETIME(3)  DEFAULT NULL COMMENT '当前暂停起点（NULL=未暂停/运行中；恢复或结束时结算进 pause_sec）',
    `target_sec`        INT          NOT NULL DEFAULT 0 COMMENT '目标时长快照（秒）：咬胶默认 600；保证达成口径稳定',
    `source`            VARCHAR(16)  NOT NULL DEFAULT 'REALTIME' COMMENT '来源：ChewSourceEnum.name()，REALTIME(实时计时)/MANUAL(手动补录，预留)',
    `edited`            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否被手动修正过',
    `client_session_id` VARCHAR(64)  DEFAULT NULL COMMENT '前端幂等键（重复开始/补报防重）',
    `created_at`        DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除（含误触近零会话）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chew_client_session` (`client_session_id`) COMMENT '前端幂等键防重（NULL 不参与唯一约束）',
    KEY `idx_chew_user_time` (`user_id`, `started_at`) COMMENT '按用户取咬胶段（今日/结算/日历）',
    KEY `idx_chew_user_aligner` (`user_id`, `aligner_id`) COMMENT '按用户取某副咬胶段',
    KEY `idx_chew_user_stage` (`user_id`, `stage_id`) COMMENT '按用户取某阶段咬胶段'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='咬胶会话表';

-- 首页工具位回填：对已自定义过工具的用户，把 chew 追加到末尾（幂等；空配置用户走客户端默认回退，无需回填）
UPDATE `user_tool_config`
SET `tool_keys` = CONCAT(`tool_keys`, ',chew')
WHERE `deleted` = 0
  AND `tool_keys` <> ''
  AND FIND_IN_SET('chew', `tool_keys`) = 0;
