// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\common\core\BaseEntity.java
package com.chiji.common.core;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类。
 * <p>
 * 业务实体统一继承本类，获得雪花算法主键与创建 / 更新时间自动填充能力。
 * 逻辑删除字段 {@code deleted} 由 MyBatis-Plus 全局配置接管，实体落地时按需声明。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键：雪花算法（ASSIGN_ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间：插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间：插入与更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
