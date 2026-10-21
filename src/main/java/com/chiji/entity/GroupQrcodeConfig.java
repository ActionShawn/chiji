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
 * 用户交流群二维码配置实体（单行运营配置）。
 * <p>
 * 存储用户交流群二维码的图片地址（{@code https} COS 直链），由管理员在管理后台上传/更换，
 * 用户端「我的 → 交流群」页实时展示最新图，解决微信群二维码 7 天过期的换图痛点。
 * 约束：{@code configKey} 唯一（UNIQUE INDEX：uk_group_qrcode_key），每个配置键至多一行；
 * 该行可能不存在，读取方按空配置兜底、首次写入懒创建。
 * {@code imageFileId} 为云存储 fileID 预留列（当前 COS 直链链路不写入）。
 */
@Getter
@Setter
@ToString
@TableName("group_qrcode_config")
public class GroupQrcodeConfig {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配置键（单行锚点，当前仅 GROUP_QRCODE=用户交流群二维码） */
    private String configKey;

    /** 云存储 fileID（预留列：当前二维码走 COS https 直链，此列暂不写入） */
    private String imageFileId;

    /** 二维码图片地址（https，COS 直链；用户端实时展示该地址） */
    private String imageUrl;

    /** 最后更新管理员用户 ID */
    private Long updatedBy;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入/更新时自动填充（即用户端展示的「更新于」时间） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 正常 / 1 已删除（单行配置业务恒 0，插入时自动填充） */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
