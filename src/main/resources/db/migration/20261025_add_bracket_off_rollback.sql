-- 齿迹 · 托槽脱落记录（20261025_add_bracket_off.sql）回滚脚本
-- 执行前提：须先将后端回滚到「不读 bracket_issue/bracket_tooth」的版本，再执行本脚本
-- 注意：BRACKET_OFF 徽标记录本身保留（badge_type 为字符串列，旧版本渲染为普通记录）
ALTER TABLE `timeline_record`
    DROP COLUMN `bracket_issue`,
    DROP COLUMN `bracket_tooth`;
