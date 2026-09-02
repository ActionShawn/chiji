-- D:\Java Work Place\personal-develop\teeth-trace\server\src\main\resources\db\migration\20260822_add_treatment_type.sql
-- 齿迹 · 治疗方式字段迁移
-- 说明：
--   1) 给 user 表新增 treatment_type 字段，存储 TreatmentTypeEnum.name()
--   2) 现有用户 treatment_type 保持 NULL，下次启动时被引导到初始化页选择
--   3) MySQL 8.0 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS（MariaDB 特性），
--      如重复执行会报 Duplicate column，执行前请先确认列不存在：
--      SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--      WHERE TABLE_SCHEMA='teeth-trace' AND TABLE_NAME='user' AND COLUMN_NAME='treatment_type';

ALTER TABLE `user`
    ADD COLUMN `treatment_type` VARCHAR(32) DEFAULT NULL
    COMMENT '治疗方式：TreatmentTypeEnum.name()，CLEAR_ALIGNER(隐形)/FIXED_BRACKET(固定托槽)/LINGUAL(舌侧)'
    AFTER `phone`;
