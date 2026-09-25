-- 记录媒体：删除逐图自定义标签列
-- 背景：逐图打标功能下线（2026-09-25），拍摄视角类别 view_type 即分类体系，
--       对比页筛选职责由 view_type（类别 Tab / 牙套上下排二级切换）承担
-- 影响列：record_media.tags（VARCHAR(128)，逗号分隔自由文本；功能下线后新数据恒为 NULL）
ALTER TABLE `record_media` DROP COLUMN `tags`;
