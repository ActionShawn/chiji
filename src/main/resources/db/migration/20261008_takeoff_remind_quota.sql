-- 齿迹 · 摘下超时提醒（P0-2）额度预扣模型地基 + 提醒方式配置列（2026-10-08）
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署新后端（含 TAKEOFF_TIMEOUT 额度逻辑的版本）之前执行；
--      两个新列均为「可空 / 带默认值」，先执行脚本再发版对旧后端零影响
--   3) user_subscribe_quota.quota_date：额度记账日期（Asia/Shanghai），惰性每日清零标记。
--      仅 TAKEOFF_TIMEOUT（摘下超时提醒）场景使用，其余场景恒为 NULL；
--      存量行 NULL 由代码视为「需重置」（当日额度按 0 计），不报错、无需数据迁移
--   4) user_setting.takeoff_remind_mode：摘下超时提醒方式（HALF_HOUR/ONE_HOUR/SMART，默认 HALF_HOUR），
--      本迁移仅加列，读写服务由后续任务接入
--   5) user_setting.goal_updated_at：每日佩戴目标最近设置/修改时刻（PRD 2026-10-08 边界 2 拍板：
--      复诊小结未达标评判基准日按目标设置日精确计算，不再用整行 updated_at 近似）。
--      存量行 NULL 由代码宽松回退 updated_at 日期（偏晚 → 少评判，宽松方向可接受），无需数据迁移
--   6) 回滚脚本：20261008_takeoff_remind_quota_rollback.sql（须先将后端回滚到不依赖新列的版本，再执行回滚）

ALTER TABLE `user_subscribe_quota`
    ADD COLUMN `quota_date` DATE DEFAULT NULL
        COMMENT '额度记账日期（Asia/Shanghai，惰性每日清零标记；NULL=需重置。仅 TAKEOFF_TIMEOUT 场景使用，其余场景恒为 NULL）' AFTER `last_accept_at`;

ALTER TABLE `user_setting`
    ADD COLUMN `takeoff_remind_mode` VARCHAR(16) NOT NULL DEFAULT 'HALF_HOUR'
        COMMENT '摘下超时提醒方式：HALF_HOUR(固定半小时)/ONE_HOUR(固定一小时)/SMART(智能提醒)，默认 HALF_HOUR' AFTER `clinic_book_offset`,
    ADD COLUMN `goal_updated_at` DATETIME DEFAULT NULL
        COMMENT '每日佩戴目标最近设置/修改时刻（NULL=未记录或迁移前旧数据；复诊小结未达标基准日优先取此列日期，NULL 回退 updated_at 近似）' AFTER `goal_sec`;
