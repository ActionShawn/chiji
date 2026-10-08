-- 回滚：20261008_takeoff_remind_quota.sql（2026-10-08）
-- 【执行顺序】必须先将后端回滚到不依赖新列的版本，再执行本脚本（新后端代码会读写 quota_date）
-- 影响说明：
--   1) user_subscribe_quota.quota_date 丢弃后，TAKEOFF_TIMEOUT 场景「当日剩余额度 / 当日累计授权数」
--      口径失效（回到生命周期累计旧口径，无日期维度）；ALIGNER_CHANGE / CLINIC_* 等常驻场景记账不受影响
--   2) user_setting.takeoff_remind_mode 丢弃后，摘下超时提醒方式回到代码默认 HALF_HOUR；
--      用户已保存的自选方式丢失（可接受：仅偏好项，非记录数据）
--   3) user_setting.goal_updated_at 丢弃后，复诊小结未达标基准日回退 updated_at 近似
--      （BE-8 旧口径：整行最近更新日，可能因其他设置项保存而偏晚 → 少评判，宽松方向）；
--      已记录的目标设置时刻丢失（可接受：统计口径退回近似，不影响记录数据）
--   4) 三列均为本迭代新增，DROP 不涉及存量业务数据迁移，无需数据搬迁脚本

ALTER TABLE `user_subscribe_quota` DROP COLUMN `quota_date`;

ALTER TABLE `user_setting` DROP COLUMN `takeoff_remind_mode`;

ALTER TABLE `user_setting` DROP COLUMN `goal_updated_at`;
