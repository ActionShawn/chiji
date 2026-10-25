-- 齿迹 · 固定托槽·橡皮筋医嘱（首页改版 v2.0 BandPage）数据层变更（2026-10-24）
-- 依据：Figma Make BandPage —— 橡皮筋独立页新增「当前医嘱」卡（牵引类型/佩戴侧/佩戴位置/每日目标次数）。
-- 说明：
--   1) 本文件为增量迁移，供云托管 MySQL 与本地库人工执行；完整结构见 db/schema.sql，两者保持同步
--   2) 【执行顺序】必须在部署新后端（RubberBandPrescription 实体版本）之前执行；全新表对旧后端零影响
--   3) 单用户单行（唯一键 uk_rbp_user），保存走 upsert；全字段可空（未填医嘱即无卡内容）
--   4) 回滚脚本：20261024_add_rubber_band_prescription_rollback.sql

CREATE TABLE IF NOT EXISTS `rubber_band_prescription` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花算法生成）',
    `user_id`       BIGINT       NOT NULL COMMENT '所属用户 ID',
    `traction_type` VARCHAR(20)  DEFAULT NULL COMMENT '牵引类型（如「Ⅱ类牵引」）',
    `side`          VARCHAR(20)  DEFAULT NULL COMMENT '佩戴侧（左右两侧/仅左侧/仅右侧）',
    `position`      VARCHAR(50)  DEFAULT NULL COMMENT '佩戴位置（如「上颌尖牙→下颌第一磨牙」）',
    `daily_target`  INT          DEFAULT NULL COMMENT '每日目标次数（正整数，1-20）',
    `created_at`    DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rbp_user` (`user_id`) COMMENT '单用户单行，upsert 兜底'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='橡皮筋医嘱表';
