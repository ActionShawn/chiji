// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\Message.java
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
 * 消息实体（信箱页的提醒/关怀卡片）。
 * <p>
 * 对应 {@code messages}：记录提醒、阶段更新、同路人、系统关怀四类通知。
 * {@code read} 对应前端 {@code status}（unread/read）；「时间」展示取 {@code createdAt}。
 * 支持标记已读、全部已读与删除（逻辑删除）。
 */
@Getter
@Setter
@ToString
@TableName("message")
public class Message {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收用户 ID（联合索引 idx_message_user 前缀） */
    private Long userId;

    /** 消息分类：MessageCategoryEnum.name()，RECORD_REMINDER/STAGE_UPDATE/FELLOW_TRAVELER/SYSTEM_CARE */
    private String category;

    /** 标题（形如「该记录今天的佩戴啦～」） */
    private String title;

    /** 正文（形如「距离上次记录已经过去22小时…」） */
    private String body;

    /** 是否已读（默认 false；对应前端 status unread/read） */
    private Boolean read;

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
