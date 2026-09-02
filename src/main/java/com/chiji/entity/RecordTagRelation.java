// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\RecordTagRelation.java
package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 记录-标签关联实体（多对多中间表，无业务字段）。
 * <p>
 * 对应「记录弹层」的标签选择（预设 + 自定义，最多 6 个）。
 * 联合主键 {@code (record_id, tag_id)}，见 schema.sql；表内不含 createdAt/updatedAt/deleted。
 * 因联合主键非单一 {@code id}，本实体不声明 {@code @TableId}，
 * 关联查询请手写 Mapper XML 或使用 MyBatis-Plus-Join（MPJ），不使用 autoResultMap。
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@TableName("record_tag_relation")
public class RecordTagRelation {

    /** 记录 ID（联合主键 (record_id, tag_id) 第一列） */
    private Long recordId;

    /** 标签 ID（联合主键第二列；表上另有单列索引 idx_rtr_tag_id 支持按标签查记录） */
    private Long tagId;
}
