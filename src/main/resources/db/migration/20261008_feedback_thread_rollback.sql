-- 回滚：20261008_feedback_thread.sql（2026-10-08）
-- 【执行顺序】必须先将后端回滚到不依赖 feedback_comment / 新列的版本，再执行本脚本
-- 影响说明：
--   1) feedback_comment 整表删除：迁移后新产生的所有对话评论丢失（旧 reply 仍在 feedback.reply 列，
--      旧后端可正常展示历史回复；迁移后的新对话消息不可恢复，回滚窗口内应尽量短）
--   2) user_read_at / admin_read_at 丢弃：未读标记失效（旧后端本就不读这两列，无影响）
--   3) closed_at 丢弃：5 秒撤回窗口判定失效（旧后端无此逻辑，无影响）
--   4) 状态值回迁：WAIT_ADMIN→PENDING、WAIT_USER→PROCESSED、CLOSED→PROCESSED
--      （旧后端两态语义下的近似映射；用户已闭环的历史会话回到「已处理」，不会误亮待处理）

DROP TABLE IF EXISTS `feedback_comment`;

ALTER TABLE `feedback`
    DROP COLUMN `user_read_at`,
    DROP COLUMN `admin_read_at`,
    DROP COLUMN `closed_at`;

UPDATE `feedback` SET `status` = 'PENDING' WHERE `status` = 'WAIT_ADMIN';
UPDATE `feedback` SET `status` = 'PROCESSED' WHERE `status` IN ('WAIT_USER', 'CLOSED');
