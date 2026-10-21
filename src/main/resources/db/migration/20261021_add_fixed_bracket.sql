-- 齿迹 · 固定托槽矫正第 1 批（fixed bracket）数据层变更（2026-10-21）
-- 依据：《固定托槽矫正技术评审与任务拆解.md》第四节 SQL 变更清单（BE-FB-08）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署新后端（含 FIXED_BRACKET 枚举与固定分支的版本）之前执行；
--      两张全新表先建表再发版对旧后端零影响；两条 ALTER 仅放开 NOT NULL / 追加可空列，旧后端兼容
--   3) 本批无 DROP COLUMN、无类型变更，破坏性风险最低档（评审结论）；
--      staging 先行验证清单见评审文档 4.4（建表索引生效 / stage 全量读校验 / timeline_record 全量读校验 /
--      回滚演练 / 耗时留档），全部逐项留档后方可上生产
--   4) 回滚脚本：20261021_add_fixed_bracket_rollback.sql（须先将后端回滚到不依赖新表/新列的版本，再执行回滚）

-- ───────────────────────── 1. 新表：复诊调整记录（REQ-FB-004） ─────────────────────────
-- timeline_record_id 为双写联动关键列：同事务 INSERT timeline_record(badge_type=ADJUSTMENT) + 本表，
-- 单向关联支撑两个删除链路（时光轴侧删→反查联动；列表侧删→先删时光轴记录再删自身）
CREATE TABLE IF NOT EXISTS `adjustment_record` (
    `id`                 BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`            BIGINT       NOT NULL COMMENT '所属用户 ID',
    `stage_id`           BIGINT       NOT NULL COMMENT '归属阶段壳（不触碰 aligner/reconcile）',
    `timeline_record_id` BIGINT       NOT NULL COMMENT '联动时光轴记录 ID（同事务双写；双向删除联动走此列索引）',
    `wire_no`            INT          DEFAULT NULL COMMENT '弓丝序号（正整数，可空）',
    `contents`           VARCHAR(200) NOT NULL COMMENT '加力内容标签 JSON 数组（换弓丝/加力/调整皮筋钩/其他）',
    `content_extra`      VARCHAR(200) DEFAULT NULL COMMENT '选「其他」时的自定义文本',
    `adjust_date`        DATE         NOT NULL COMMENT '调整日期（≤今日，补记不限）',
    `note`               VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `created_at`         DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`         DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_adj_user_date` (`user_id`, `adjust_date`) COMMENT '按用户取调整列表倒序',
    KEY `idx_adj_tr` (`timeline_record_id`) COMMENT '时光轴删除联动反查'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复诊调整记录表';

-- ───────────────────────── 2. 新表：橡皮筋每日打卡（REQ-FB-006） ─────────────────────────
-- 唯一键不含 deleted：业务层 done 翻转、禁逻辑删除行（否则同日再打卡唯一键冲突，评审红线 R7）；
-- toggle 用 INSERT ... ON DUPLICATE KEY UPDATE done=VALUES(done)，以落库状态返回
CREATE TABLE IF NOT EXISTS `rubber_band_record` (
    `id`          BIGINT     NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`     BIGINT     NOT NULL COMMENT '所属用户 ID',
    `record_date` DATE       NOT NULL COMMENT '打卡日（设备本地时区）',
    `done`        TINYINT    NOT NULL DEFAULT 1 COMMENT '1 已打卡 / 0 已取消（翻转更新，禁逻辑删除行）',
    `created_at`  DATETIME   NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME   NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记仅规范兼容，业务恒 0',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rb_user_date` (`user_id`, `record_date`) COMMENT '幂等兜底：同日仅一行'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='橡皮筋每日打卡表';

-- ───────────────────────── 3. stage：放开 count NOT NULL（REQ-FB-003） ─────────────────────────
-- 仅 count 一列需放开（days_per_aligner/soft_days/hard_days 本就是 DEFAULT NULL，评审 4.2 修正）；
-- 隐形存量数据零影响：放开的只是约束，无任何数据改写；隐形路径服务层已迁移等价强校验（BE-FB-03）
ALTER TABLE `stage` MODIFY COLUMN `count` INT DEFAULT NULL COMMENT '总副数（双模为组数，每组 = 软膜 + 硬膜两副；固定托槽阶段为 null）';

-- stage.mode 列定义零变更（VARCHAR(32) 容纳 FIXED_BRACKET 14 字符），存量零回填；
-- 存量库若需显式对齐注释可执行（可选，幂等）：
-- ALTER TABLE `stage` MODIFY COLUMN `mode` VARCHAR(32) NOT NULL DEFAULT 'CLEAR_SINGLE' COMMENT '记录模式：RecordModeEnum.name()，CLEAR_SINGLE(单模)/CLEAR_DUAL(双模)/FIXED_BRACKET(固定托槽)，存量数据归入单模';

-- ───────────────────────── 4. timeline_record：疼痛可空列（REQ-FB-007） ─────────────────────────
-- 与 wear_hours 同构（记录类型专属可空载荷挂核心表，评审 2.2 裁决）；无默认值回填需求，索引不新增；
-- pain_part 修正为 VARCHAR(128)（7 个预设标签全选 JSON 序列化约 60+ 字符，清单原 64 不足）
ALTER TABLE `timeline_record`
    ADD COLUMN `pain_score` TINYINT DEFAULT NULL COMMENT '疼痛程度 0-5（仅 PAIN 记录有值）' AFTER `badge_type`,
    ADD COLUMN `pain_part`  VARCHAR(128) DEFAULT NULL COMMENT '疼痛部位 JSON 数组（预设标签+自定义≤20字，仅 PAIN 记录有值）' AFTER `pain_score`;

-- badge_type 列定义零变更（VARCHAR(16) 容纳 PAIN(4)/ADJUSTMENT(11)）；
-- 存量库若需显式对齐注释可执行（可选，幂等）：
-- ALTER TABLE `timeline_record` MODIFY COLUMN `badge_type` VARCHAR(16) DEFAULT NULL COMMENT '特殊徽标：RecordBadgeTypeEnum.name()，FOLLOW_UP/MILESTONE/STAGE_SWITCH/PAIN/ADJUSTMENT（与 wear_hours 互斥）';
