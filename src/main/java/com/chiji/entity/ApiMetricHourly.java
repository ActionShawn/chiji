package com.chiji.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * 接口小时聚合实体。
 * <p>
 * 内存计数器每 5 分钟增量刷盘：按 (stat_date, hour, api) 唯一键累加 upsert
 * （cnt/err_cnt/slow_cnt 累加，max_ms 取 GREATEST）。永久保留，供看板与监控页查询。
 */
@Getter
@Setter
@ToString
@TableName("api_metric_hourly")
public class ApiMetricHourly {

    /** 主键，雪花算法生成（upsert 冲突时数据库忽略，沿用已有行） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 统计日期（Asia/Shanghai） */
    private LocalDate statDate;

    /** 小时 0-23 */
    private Integer hour;

    /** 归一化接口模板，如 /api/feedback/{id}/reply */
    private String api;

    /** 调用数 */
    private Long cnt;

    /** 失败数（含抛异常与 4xx/5xx） */
    private Long errCnt;

    /** 慢请求数（>阈值） */
    private Long slowCnt;

    /** 最大耗时 ms */
    private Integer maxMs;
}
