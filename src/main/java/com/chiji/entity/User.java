// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\entity\User.java
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
 * 用户实体。
 * <p>
 * 微信静默登录时自动创建，{@code openid} 全局唯一。
 * 关系：1 用户 → N 篇日记、N 个自定义标签、1 条用户设置。
 */
@Getter
@Setter
@ToString
@TableName("user")
public class User {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 微信 openid，全局唯一（UNIQUE INDEX：uk_user_openid） */
    private String openid;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatarUrl;

    /** 手机号（可选，绑定后填入；未绑定为 null） */
    private String phone;

    /** 治疗方式：TreatmentTypeEnum.name()，CLEAR_ALIGNER(隐形)/FIXED_BRACKET(固定托槽)/LINGUAL(舌侧)，未选择为 null */
    private String treatmentType;

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
