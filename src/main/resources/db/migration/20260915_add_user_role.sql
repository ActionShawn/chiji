-- 管理后台 P1：用户角色字段
-- USER=普通用户（存量默认）/ ADMIN=管理员（管理后台功能权限标识，SQL 手动提权）
ALTER TABLE `user`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER'
        COMMENT '角色：UserRoleEnum.name()，USER(普通用户)/ADMIN(管理员)'
        AFTER `treatment_type`;
