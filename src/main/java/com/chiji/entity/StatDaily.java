package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运营日汇总实体。
 * <p>
 * 每日 00:05 聚合前一日各采集表（user/login_log/usage_session/api_metric_hourly/
 * timeline_record/record_media），按 stat_date 先删后插幂等写入；永久保留。
 */
@Getter
@Setter
@ToString
@TableName("stat_daily")
public class StatDaily {

    /** 统计日期（主键，手动赋值） */
    @TableId(value = "stat_date", type = IdType.INPUT)
    private LocalDate statDate;

    /** 新增用户数（user.created_at 当日计数） */
    private Integer newUsers;

    /** 登录用户数（login_log distinct user_id） */
    private Integer loginUsers;

    /** 使用时长总和（秒） */
    private Long usageSec;

    /** 使用用户数（usage_session distinct user_id） */
    private Integer usageUsers;

    /** 接口调用总数（api_metric_hourly.cnt 求和） */
    private Long apiCalls;

    /** 新增记录数（timeline_record.created_at 当日计数） */
    private Integer newRecords;

    /** 新增图片数（record_media.created_at 当日计数） */
    private Integer newImages;

    /** 更新时间（数据库 ON UPDATE 维护） */
    private LocalDateTime updatedAt;
}
