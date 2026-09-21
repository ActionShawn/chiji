package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 首页常用工具配置实体（一对一扩展 User）。
 * <p>
 * 存储用户自选的常用工具 key 有序列表（逗号分隔，如 {@code timeline,compare,report,clinic}）。
 * 工具字典（名称/图标/页面地址/默认顺序）由客户端承载，本表仅记录用户改过的选择与排序；
 * {@code toolKeys} 为空串表示未自定义，客户端回退默认顺序。
 * 约束：{@code userId} 唯一（UNIQUE INDEX：uk_user_tool_user）。该行可能不存在，读取方按空配置兜底、首次写时懒创建。
 */
@Getter
@Setter
@ToString
@TableName("user_tool_config")
public class UserToolConfig {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID（一对一，全局唯一 UNIQUE INDEX：uk_user_tool_user） */
    private Long userId;

    /** 常用工具 key 有序列表（逗号分隔；空串=客户端回退默认顺序） */
    private String toolKeys;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 正常 / 1 已删除（默认 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
