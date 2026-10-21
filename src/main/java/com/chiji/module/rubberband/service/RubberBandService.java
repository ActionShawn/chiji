package com.chiji.module.rubberband.service;

import com.chiji.module.rubberband.vo.RubberBandRecentVO;
import com.chiji.module.rubberband.vo.RubberBandToggleVO;

/**
 * 橡皮筋每日打卡服务（固定托槽第 1 批，REQ-FB-006）。
 * <p>
 * 第 1 批形态：纯手动、每日一次勾选、仅当日、零提醒零订阅消息；
 * 门控由 {@link com.chiji.module.stage.support.TreatmentGateSupport} 唯一事实源承担。
 */
public interface RubberBandService {

    /**
     * 查询今日状态 + 近 7 天点阵（一条范围 SQL，零新增请求红线的数据源）。
     *
     * @param userId 当前用户 ID
     * @return 今日状态 + 近 7 天逐日状态
     */
    RubberBandRecentVO recent(Long userId);

    /**
     * 打卡/取消翻转（仅当日；upsert 幂等，以落库状态返回）。
     *
     * @param userId 当前用户 ID
     * @return 落库后的最终状态
     */
    RubberBandToggleVO toggle(Long userId);
}
