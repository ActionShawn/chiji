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
 * 使用会话实体（App 级埋点）。
 * <p>
 * 小程序 App onShow 记进入时刻、onHide 上报停留时长，一段前后台停留即一条记录；
 * 上报失败前端 storage 缓存补报，{@code clientSessionId} 唯一键幂等防重
 * （Mapper 使用 INSERT IGNORE）。按保留期定期物理清理。
 */
@Getter
@Setter
@ToString
@TableName("usage_session")
public class UsageSession {

    /** 主键，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 前端生成幂等键（补报防重），UNIQUE 索引 */
    private String clientSessionId;

    /** 会话进入时刻 */
    private LocalDateTime enterAt;

    /** 停留秒数 */
    private Integer durationSec;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
