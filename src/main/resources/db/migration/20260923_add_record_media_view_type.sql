-- 记录媒体新增拍摄视角类别（牙套副前后对比照片分类）
--   record_media 新增 view_type：FRONT 正面露齿 / SIDE 侧面 / ARCH_UPPER 上牙套 / ARCH_LOWER 下牙套 / NORMAL 普通（无辅助线）
--   历史存量数据与视频为 NULL，前端按无类别渲染

ALTER TABLE `record_media`
    ADD COLUMN `view_type` VARCHAR(16) DEFAULT NULL COMMENT '拍摄视角类别：FRONT/SIDE/ARCH_UPPER/ARCH_LOWER/NORMAL，历史数据与视频为 NULL' AFTER `sort_order`;
