package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 登录日志实体。
 * <p>
 * 管理后台 P2 采集层：每次登录成功异步落 1 条（controller 层埋点，虚拟线程异步写入），
 * 供运营看板「登录用户数（distinct）」指标聚合。按保留期定期物理清理。
 */
@Getter
@Setter
@ToString
@TableName("login_log")
public class LoginLog {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 登录时刻 */
    private LocalDateTime loginAt;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
