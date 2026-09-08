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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消息实体（信箱页的提醒/关怀卡片）。
 * <p>
 * 对应 {@code messages}：存量四类（记录提醒/阶段更新/同路人/系统关怀）与佩戴时长功能新增的
 * 佩戴提醒（WEAR）均落此表。{@code read} 对应前端 {@code status}（unread/read）；「时间」展示
 * 取 {@code createdAt}。支持标记已读、全部已读与删除（逻辑删除）。
 * <p>
 * 佩戴功能新增列：{@code reminderType}/{@code sceneDate} 用于类型化去重
 * （{@code uk_message_user_type_date}，同用户同类型同日期至多一条，存量行两列为 null 不受约束）；
 * {@code priority} 供未来排序；{@code pushStatus}/{@code pushedAt} 为订阅消息预留（当前无通道恒 NONE/null）。
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

    /** 消息分类：MessageCategoryEnum.name()，RECORD_REMINDER/STAGE_UPDATE/FELLOW_TRAVELER/SYSTEM_CARE/WEAR */
    private String category;

    /** 提醒类型：WearReminderTypeEnum.name()（佩戴功能生成的消息有值，存量消息为 null） */
    private String reminderType;

    /** 业务场景日期（去重维度之一，同用户同类型同日期至多一条；存量消息为 null） */
    private LocalDate sceneDate;

    /** 优先级：0 低 / 1 普通 / 2 高（达标庆祝置高，供未来排序） */
    private Integer priority;

    /** 订阅消息推送状态：NONE(未推送)/PUSHED(已推送)，当前无订阅通道恒为 NONE */
    private String pushStatus;

    /** 推送时间（当前无订阅通道，恒为 null） */
    private LocalDateTime pushedAt;

    /** 标题（形如「该记录今天的佩戴啦～」） */
    private String title;

    /** 正文（形如「距离上次记录已经过去22小时…」） */
    private String body;

    /**
     * 是否已读（默认 false；对应前端 status unread/read）。
     * {@code read} 是 MySQL 保留字，需反引号转义，否则 MP 生成的无引号 SQL 会语法报错。
     */
    @TableField("`read`")
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
