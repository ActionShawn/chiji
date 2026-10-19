-- 齿迹 · 注销原因匿名统计 回滚（账号注销功能，2026-10-19）
-- 说明：本期不涉及任何既有表结构变更（无 ALTER），回滚仅删新建表；
--       该表为纯匿名统计表、无外键引用，可直接删除，不影响其他表。
-- 对应迁移：20261019_add_user_cancel_reason.sql

DROP TABLE IF EXISTS `user_cancel_reason`;
