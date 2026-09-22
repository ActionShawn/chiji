-- 预约提醒独立提醒时间 + 提前量区间收窄（0~2）
--   1) user_setting 新增 clinic_book_time（默认 07:00，独立于就诊提醒时刻 clinic_remind_time）
--   2) clinic_book_offset 默认值 3 → 2；存量「前 3 天」归一为 2（读取端区间校验亦回退 2）

ALTER TABLE `user_setting`
    ADD COLUMN `clinic_book_time` TIME NOT NULL DEFAULT '07:00:00' COMMENT '预约提醒时刻（Asia/Shanghai，仅整点，默认早 7 点，独立于就诊提醒时刻）' AFTER `clinic_remind_time`,
    MODIFY COLUMN `clinic_book_offset` TINYINT NOT NULL DEFAULT 2 COMMENT '预约提醒提前天数：0~2 天（默认前 2 天，仅隐形最终副生效）';

UPDATE `user_setting` SET `clinic_book_offset` = 2 WHERE `clinic_book_offset` = 3;
