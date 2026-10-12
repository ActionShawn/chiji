-- 回滚：20261012_add_chew_session.sql（2026-10-12）
-- 【执行顺序】必须先将后端回滚到不依赖 chew_session 的版本，再执行本脚本
-- 影响说明：
--   1) chew_session 整表删除：迁移后产生的咬胶记录全部丢失，回滚前建议先导出留档（可接受：本轮新增功能数据）
--   2) tool_keys 移除 chew：老前端本就忽略未知 key，无影响；
--      【产品约束】回滚后 tool_keys 可能为空串（如原值仅 chew）或残留，前端必须把「空串 / 全部无效 key」
--      回退为 DEFAULT_TOOL_KEYS 默认集合，不得出现首页工具位为空；回滚须前后端同批；
--      不改变用户其它已保存的自定义工具（chew,chew 等异常重复值脚本不保证完全清理，可忽略）

UPDATE `user_tool_config`
SET `tool_keys` = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', `tool_keys`, ','), ',chew,', ','))
WHERE `deleted` = 0 AND FIND_IN_SET('chew', `tool_keys`) = 1;

DROP TABLE IF EXISTS `chew_session`;
