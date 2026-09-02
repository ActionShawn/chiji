// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\RecordMedia.java
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
 * 记录媒体实体（时光轴记录下的图片/视频）。
 * <p>
 * 对应「记录弹层」的 tiles 与时间轴卡片的 media 数组。
 * 对比素材库（compareLibrary）即本表的投影（连同所属记录的阶段/副信息）。
 * 关系：N 媒体 → 1 记录（{@link TimelineRecord}）。
 */
@Getter
@Setter
@ToString
@TableName("record_media")
public class RecordMedia {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属记录 ID（索引 idx_media_record） */
    private Long recordId;

    /** 媒体类型：MediaKindEnum.name()，IMAGE/VIDEO */
    private String type;

    /** 媒体文件地址（OSS key 或 CDN URL） */
    private String url;

    /** 视频时长（秒），图片为 null（对应 duration） */
    private Integer duration;

    /** 说明文字（可空，对应 caption） */
    private String caption;

    /** 展示排序，越小越靠前 */
    private Integer sortOrder;

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
