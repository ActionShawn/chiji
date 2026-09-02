-- D:\Java Work Place\personal-develop\teeth-trace\server\src\main\resources\db\seed.sql
-- 齿迹 · 开发期种子数据
-- 说明：
--   1) 数据来自 client/utils/mock.js，用于首页联调，与原型视觉一一对应；
--   2) 本文件不会被 Spring 自动执行（与 schema.sql 同置于 db/ 子目录），仅供人工执行或 MCP 注入；
--   3) 执行前请先执行 schema.sql 建表；
--   4) 可重复执行：先 DELETE 再 INSERT（仅清理种子范围，不动业务数据）。

-- 清理种子数据（按外键依赖逆序）
DELETE FROM `tag` WHERE `user_id` = 0 AND `name` IN ('换牙套', '复诊', '疼痛', '适应中', '小进步', '里程碑', '日常');
DELETE FROM `aligner` WHERE `stage_id` IN (SELECT `id` FROM `stage` WHERE `user_id` = 1);
DELETE FROM `stage` WHERE `user_id` = 1;
DELETE FROM `user` WHERE `id` = 1;

-- 用户：dev 用户（id=1，与后端 DevAuthServiceImpl 中固定 userId 对齐）
INSERT INTO `user` (`id`, `openid`, `nickname`, `avatar_url`, `phone`, `created_at`, `updated_at`, `deleted`)
VALUES (1, 'dev-user-001', '齿迹开发者', NULL, NULL, '2026-03-10 09:00:00', '2026-08-12 20:30:00', 0);

-- 阶段：对应 mock.stages（3 条）
-- 阶段状态为两态：ACTIVE（启用）/ ENDED（结束），同一时间至多一个 ACTIVE
-- 注意 sort_order：mock 数组顺序为 [第一阶段(active), 第二阶段(ended), 准备阶段(ended)]
-- 但展示排序按 sortOrder 升序，因此准备阶段 sort_order=1（最早），第一阶段=2，第二阶段=3
INSERT INTO `stage` (`id`, `user_id`, `name`, `count`, `days_per_aligner`, `start_date`, `status`, `sort_order`, `created_at`, `updated_at`, `deleted`) VALUES
-- 准备阶段 · 初适应（ended）
(1001, 1, '准备阶段 · 初适应', 8, 5, '2026-03-10', 'ENDED', 1, '2026-03-10 09:00:00', '2026-03-30 09:15:00', 0),
-- 第一阶段 · 主力矫正（active，首页默认展示此阶段的 alignerNodes）
(1002, 1, '第一阶段 · 主力矫正', 54, 7, '2026-07-15', 'ACTIVE', 2, '2026-07-15 09:00:00', '2026-08-12 20:30:00', 0),
-- 第二阶段 · 精细调整（ended）
(1003, 1, '第二阶段 · 精细调整', 19, 10, NULL, 'ENDED', 3, '2026-08-02 14:12:00', '2026-08-02 14:12:00', 0);

-- 牙套副：对应 mock.alignerNodes（10 条），全部挂到第一阶段（stage_id=1002）
-- mock 数组下标 0-9 对应 num 1-10
-- dates 解析：年份统一 2026，'03.10–03.16' → start_date='2026-03-10', end_date='2026-03-16'
-- active 节点（idx=7, num=8）：current_day=6, total_days=7
-- future 节点（idx=8,9, num=9,10）：日期为 null
INSERT INTO `aligner` (`id`, `stage_id`, `num`, `state`, `start_date`, `end_date`, `current_day`, `total_days`, `created_at`, `updated_at`, `deleted`) VALUES
-- num=1 done
(2001, 1002, 1, 'DONE', '2026-03-10', '2026-03-16', NULL, 5, '2026-03-10 09:00:00', '2026-03-16 22:00:00', 0),
-- num=2 done
(2002, 1002, 2, 'DONE', '2026-03-17', '2026-03-23', NULL, 5, '2026-03-17 09:00:00', '2026-03-23 22:00:00', 0),
-- num=3 done
(2003, 1002, 3, 'DONE', '2026-03-24', '2026-03-30', NULL, 5, '2026-03-24 09:00:00', '2026-03-30 22:00:00', 0),
-- num=4 done
(2004, 1002, 4, 'DONE', '2026-03-31', '2026-04-06', NULL, 5, '2026-03-31 09:00:00', '2026-04-06 22:00:00', 0),
-- num=5 done
(2005, 1002, 5, 'DONE', '2026-04-07', '2026-04-13', NULL, 5, '2026-04-07 09:00:00', '2026-04-13 22:00:00', 0),
-- num=6 done（mock dates '07.15–07.21'）
(2006, 1002, 6, 'DONE', '2026-07-15', '2026-07-21', NULL, 7, '2026-07-15 09:00:00', '2026-07-21 22:00:00', 0),
-- num=7 done（mock dates '07.22–07.28'）
(2007, 1002, 7, 'DONE', '2026-07-22', '2026-07-28', NULL, 7, '2026-07-22 09:00:00', '2026-07-28 22:00:00', 0),
-- num=8 active（mock dates '08.01–今天', day=6, total=7）
(2008, 1002, 8, 'ACTIVE', '2026-08-01', NULL, 6, 7, '2026-08-01 09:00:00', '2026-08-12 20:30:00', 0),
-- num=9 future
(2009, 1002, 9, 'FUTURE', NULL, NULL, NULL, 7, '2026-08-01 09:00:00', '2026-08-01 09:00:00', 0),
-- num=10 future
(2010, 1002, 10, 'FUTURE', NULL, NULL, NULL, 7, '2026-08-01 09:00:00', '2026-08-01 09:00:00', 0);

-- 标签：系统内置预设（user_id=0），对应 mock.recordTags
INSERT INTO `tag` (`id`, `user_id`, `name`, `color_hex`, `sort_order`, `created_at`, `updated_at`, `deleted`) VALUES
(3001, 0, '换牙套', NULL, 1, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0),
(3002, 0, '复诊',   NULL, 2, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0),
(3003, 0, '疼痛',   NULL, 3, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0),
(3004, 0, '适应中', NULL, 4, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0),
(3005, 0, '小进步', NULL, 5, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0),
(3006, 0, '里程碑', NULL, 6, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0),
(3007, 0, '日常',   NULL, 7, '2026-03-10 09:00:00', '2026-03-10 09:00:00', 0);
