-- 牙套副前后对比功能细化：六类视角 + 媒体自定义标签
--   1) view_type 枚举细化：FRONT 拆分为 FRONT_CLOSED 正面闭嘴 / FRONT_SMILE 正面露齿笑
--      存量 FRONT 统一迁移为 FRONT_SMILE（当时拍摄引导即露齿咬合）
--   2) record_media 新增 tags：自定义标签，逗号分隔（最多3个、单个≤8字），前端打标，历史数据与视频为 NULL

UPDATE `record_media` SET `view_type` = 'FRONT_SMILE' WHERE `view_type` = 'FRONT';

ALTER TABLE `record_media`
    MODIFY COLUMN `view_type` VARCHAR(16) DEFAULT NULL COMMENT '拍摄视角类别：FRONT_CLOSED/FRONT_SMILE/SIDE/ARCH_UPPER/ARCH_LOWER/NORMAL，历史数据与视频为 NULL',
    ADD COLUMN `tags` VARCHAR(128) DEFAULT NULL COMMENT '自定义标签，逗号分隔（最多3个、单个≤8字），历史数据与视频为 NULL' AFTER `view_type`;
