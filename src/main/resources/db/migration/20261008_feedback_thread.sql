-- 齿迹 · 意见反馈对话化：连续评论式对话 + 三态状态机 + 未读标记（2026-10-08）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署对话化后端（含 feedback_comment 读写逻辑的版本）之前执行；
--      新增列均可空/带默认值，先执行脚本对旧后端零影响
--   3) 状态语义切换：PENDING(待处理)/PROCESSED(已处理)
--      → WAIT_ADMIN(等回复)/WAIT_USER(待你确认)/CLOSED(已闭环)（列宽 VARCHAR(16) 不变，仅换值）
--      迁移映射：PENDING → WAIT_ADMIN（运营尚未处理，真开放）；
--               PROCESSED → CLOSED（运营已按旧模型处理完，视为已闭环，closed_at 取最近处理时刻；
--               用户可在对话中「追问」复活）
--   4) 旧 reply 单条迁入 feedback_comment（幂等：该反馈尚无 ADMIN 评论时才插）。
--      迁入行 id 复用 feedback.id（两端同为雪花生成，旧 feedback id 的时间序必小于迁移后新评论，
--      不会冲突），created_at 取 replied_at 保证时间轴正确；
--      feedback.reply/replied_at 列保留作「最新运营回复镜像」供历史版本客户端展示，线程视图只读评论表
--   5) user_read_at / admin_read_at 初始化仅作用于本次未迁移行（WHERE 旧状态值，重放时无匹配自动跳过）：
--      存量对话不算未读（避免上线即全量红点），未读只对迁移后新产生的消息生效
--   6) 回滚脚本：20261008_feedback_thread_rollback.sql（须先回滚后端到不依赖新表/新列的版本，再执行）

-- 1) feedback 新增未读标记与闭环时刻列
ALTER TABLE `feedback`
    ADD COLUMN `user_read_at` DATETIME DEFAULT NULL
        COMMENT '用户最近进入对话详情页时刻（NULL=从未进入，按「存在未读的运营消息」计算）' AFTER `replied_at`,
    ADD COLUMN `admin_read_at` DATETIME DEFAULT NULL
        COMMENT '管理员最近进入对话详情页时刻（NULL=从未进入，全部用户消息计未读）' AFTER `user_read_at`,
    ADD COLUMN `closed_at` DATETIME DEFAULT NULL
        COMMENT '用户点「已解决」的闭环时刻（5 秒撤回窗口判定基准；仅 status=CLOSED 时有意义）' AFTER `admin_read_at`;

-- 2) 未读标记初始化（仅旧状态行，先于状态值迁移执行，重放时无匹配）
UPDATE `feedback` SET `user_read_at` = NOW(), `admin_read_at` = NOW()
WHERE `status` IN ('PENDING', 'PROCESSED');

-- 3) 状态值迁移（仅改旧值行，可重复执行无副作用）
UPDATE `feedback` SET `status` = 'WAIT_ADMIN' WHERE `status` = 'PENDING';
UPDATE `feedback` SET `status` = 'CLOSED', `closed_at` = COALESCE(`replied_at`, `updated_at`) WHERE `status` = 'PROCESSED';

-- 4) 评论表：反馈与运营的连续对话消息（旧 reply 单条由代码合成展示，不迁入本表）
CREATE TABLE IF NOT EXISTS `feedback_comment` (
    `id`          BIGINT        NOT NULL COMMENT '主键（雪花算法生成）',
    `feedback_id` BIGINT        NOT NULL COMMENT '所属反馈 ID',
    `user_id`     BIGINT        NOT NULL DEFAULT 0 COMMENT '发言人用户 ID：USER=提交用户；ADMIN=0（运营无用户账号）',
    `role`        VARCHAR(8)    NOT NULL COMMENT '发言人角色：USER(用户)/ADMIN(运营)',
    `content`     VARCHAR(1000) DEFAULT NULL COMMENT '评论正文（可空：允许仅图片，正文与图片至少一项非空）',
    `images`      VARCHAR(2048) DEFAULT NULL COMMENT '图片地址 JSON 数组，如 ["https://...","https://..."]（无图为空串/NULL）',
    `created_at`  DATETIME      NOT NULL COMMENT '创建时间',
    `updated_at`  DATETIME      NOT NULL COMMENT '更新时间',
    `deleted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_fbc_feedback` (`feedback_id`, `id`) COMMENT '按反馈取对话消息（游标分页按 id 升序）',
    KEY `idx_fbc_unread` (`role`, `created_at`) COMMENT '未读计数：按角色+时间扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈评论表（连续对话消息）';

-- 5) 旧单条 reply 迁入评论表（幂等：仅当该反馈尚无 ADMIN 评论时插入，重放自动跳过）
INSERT INTO `feedback_comment` (`id`, `feedback_id`, `user_id`, `role`, `content`, `images`, `created_at`, `updated_at`, `deleted`)
SELECT f.`id`, f.`id`, 0, 'ADMIN', f.`reply`, NULL,
       COALESCE(f.`replied_at`, f.`updated_at`), COALESCE(f.`replied_at`, f.`updated_at`), 0
FROM `feedback` f
WHERE f.`deleted` = 0
  AND f.`reply` IS NOT NULL AND f.`reply` <> ''
  AND NOT EXISTS (SELECT 1 FROM `feedback_comment` c
                  WHERE c.`feedback_id` = f.`id` AND c.`role` = 'ADMIN' AND c.`deleted` = 0);
