package com.chiji.module.clinic.service;

import com.chiji.module.clinic.dto.VisitCompleteRequest;
import com.chiji.module.clinic.dto.VisitSaveRequest;
import com.chiji.module.clinic.vo.ClinicDayVO;
import com.chiji.module.clinic.vo.ClinicMonthVO;
import com.chiji.module.clinic.vo.ClinicVisitVO;

import java.time.LocalDate;

/**
 * 复诊档案服务（已确认日程 + 就诊记录）。
 * <p>
 * 完成动作（PLANNED → DONE）联动时光轴：创建 {@code FOLLOW_UP} 徽标投影记录，
 * 照片挂 record_media；单向主从，档案删除联动删投影，时光轴侧删除不回写。
 */
public interface ClinicVisitService {

    /**
     * 创建已确认复诊日程（PLANNED）。
     *
     * @param userId 用户 ID
     * @param req    保存请求
     * @return 新建日程
     */
    ClinicVisitVO create(Long userId, VisitSaveRequest req);

    /**
     * 编辑日程（仅 PLANNED 可编辑；DONE 走补录链路）。
     *
     * @param userId 用户 ID
     * @param id     记录 ID
     * @param req    保存请求
     * @return 更新后日程
     */
    ClinicVisitVO update(Long userId, Long id, VisitSaveRequest req);

    /**
     * 删除日程/记录（DONE 记录联动逻辑删其投影时光轴记录）。
     *
     * @param userId 用户 ID
     * @param id     记录 ID
     */
    void delete(Long userId, Long id);

    /**
     * 标记就诊完成（PLANNED → DONE），补录内容/照片，可一键带出复诊费、
     * 生成下一条 PLANNED（nextVisitDate 非空时）。
     *
     * @param userId 用户 ID
     * @param id     记录 ID
     * @param req    完成请求
     * @return 完成后记录
     */
    ClinicVisitVO complete(Long userId, Long id, VisitCompleteRequest req);

    /**
     * 月视图（自绘月历数据源：本月日程/记录 + 预约窗口锚点）。
     *
     * @param userId 用户 ID
     * @param month  月份 yyyy-MM
     * @return 月视图
     */
    ClinicMonthVO monthView(Long userId, String month);

    /**
     * 日视图（当日日程/记录 + 当日花费）。
     *
     * @param userId 用户 ID
     * @param date   日期
     * @return 日视图
     */
    ClinicDayVO dayView(Long userId, LocalDate date);

    /**
     * 最近一次已完成就诊（预填诊所/医生 + 「距上次复诊 X 天」）。
     *
     * @param userId 用户 ID
     * @return 最近记录；无则 null
     */
    ClinicVisitVO lastVisit(Long userId);
}
