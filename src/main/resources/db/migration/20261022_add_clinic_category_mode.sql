-- 齿迹 · 固定托槽体验优化第 2 轮 · OPT-FB-03 记账默认分类按模式分档（2026-10-22）
-- 依据：《固定托槽体验优化第2轮技术评审与任务拆解.md》SQL-2（加 mode 列 + 固定五类 seed，服务层按用户模式过滤）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署分类过滤版后端之前执行；加列 + 新增行对旧后端零影响，窗口宽松
--   3) 隐形回归红线：存量内置行（id 3101~3105）mode 保持 NULL（NULL = 通用/隐形），零改写；
--      隐形用户分类列表逐字节不变（服务层按 mode IS NULL OR mode = 用户模式 过滤）
--   4) staging 先行验证清单：隐形用户分类列表逐字节对比（存量行全保留）；固定用户新分类可见；
--      create() 内置同名拦截验证（如「托槽」「复诊挂号」不可自建）；回滚演练；耗时留档
--   5) 本批无 DROP COLUMN、无类型变更、无存量数据改写语义，破坏性最低档（评审结论）

-- 1) 加模式维度列：NULL = 通用（隐形存量行零改写）；FIXED_BRACKET = 固定专属
ALTER TABLE `clinic_expense_category`
    ADD COLUMN `mode` VARCHAR(32) DEFAULT NULL
    COMMENT '分类适用模式：NULL 通用（隐形存量行）/ FIXED_BRACKET 固定托槽专属（OPT-FB-03）；服务层按用户 treatment_type 过滤' AFTER `name`;

-- 2) 固定五类 seed（user_id=0 内置行，id 3106~3110 固定段避开雪花段，INSERT IGNORE 防重复执行；
--    固定排最前沿用内置排序机制：sort_order 1~5 与隐形内置同权重，由服务层模式过滤后各自模式内有序）
INSERT IGNORE INTO `clinic_expense_category` (`id`, `user_id`, `name`, `mode`, `sort_order`, `created_at`, `updated_at`, `deleted`) VALUES
(3106, 0, '托槽',     'FIXED_BRACKET', 1, NOW(), NOW(), 0),
(3107, 0, '弓丝更换', 'FIXED_BRACKET', 2, NOW(), NOW(), 0),
(3108, 0, '橡皮筋',   'FIXED_BRACKET', 3, NOW(), NOW(), 0),
(3109, 0, '复诊挂号', 'FIXED_BRACKET', 4, NOW(), NOW(), 0),
(3110, 0, '保持器',   'FIXED_BRACKET', 5, NOW(), NOW(), 0);
