-- 复诊日程支持备注（非必填）
--   clinic_visit 新增 remark：用户建/编辑日程时自由填写，如注意事项；可空

ALTER TABLE `clinic_visit`
    ADD COLUMN `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注（可空，用户自由填写）' AFTER `doctor_name`;
