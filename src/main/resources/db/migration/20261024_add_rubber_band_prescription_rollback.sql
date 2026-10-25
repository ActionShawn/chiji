-- 齿迹 · 橡皮筋医嘱（20261024_add_rubber_band_prescription.sql）回滚脚本
-- 执行前提：须先将后端回滚到「不读 rubber_band_prescription 表」的版本，再执行本脚本
DROP TABLE IF EXISTS `rubber_band_prescription`;
