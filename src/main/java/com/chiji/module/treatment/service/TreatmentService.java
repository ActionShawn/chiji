package com.chiji.module.treatment.service;

import com.chiji.module.treatment.dto.UpdateTreatmentTypeRequest;
import com.chiji.module.treatment.vo.TreatmentTypeVO;

import java.util.List;

/**
 * 治疗方式服务。
 * <p>
 * 提供治疗方式列表查询、用户当前治疗方式查询与更新。
 * 用户首次启动小程序时，前端通过本服务获取治疗方式列表渲染初始化页，
 * 用户选择后通过 {@link #updateUserTreatmentType} 持久化，app.js 据此分发到对应首页。
 */
public interface TreatmentService {

    /**
     * 查询所有治疗方式列表。
     * <p>
     * 返回所有枚举项（含未开放的），前端根据 available 字段决定卡片是否可选。
     *
     * @return 治疗方式 VO 列表
     */
    List<TreatmentTypeVO> listTreatmentTypes();

    /**
     * 查询当前用户的治疗方式。
     * <p>
     * app.js onLaunch 时调用，用于路由分发：
     * <ul>
     *   <li>返回 null → 未选择，跳转初始化页</li>
     *   <li>返回 CLEAR_ALIGNER → 进入隐形矫正首页</li>
     *   <li>返回 FIXED_BRACKET / LINGUAL → 进入敬请期待页</li>
     * </ul>
     *
     * @param userId 当前用户 ID
     * @return 治疗方式编码，未选择返回 null
     */
    String getUserTreatmentType(Long userId);

    /**
     * 更新当前用户的治疗方式。
     * <p>
     * 用于初始化页选择治疗方式，以及「我的」页修改治疗方式。
     *
     * @param userId  当前用户 ID
     * @param request 更新请求
     * @return 更新后的治疗方式 VO
     */
    TreatmentTypeVO updateUserTreatmentType(Long userId, UpdateTreatmentTypeRequest request);
}
