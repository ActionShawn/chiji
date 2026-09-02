package com.chiji.module.stage.service;

import com.chiji.module.stage.vo.HomeSummaryVO;

/**
 * 首页聚合服务。
 * <p>
 * 提供首页所需全部数据的聚合查询：阶段列表、当前阶段的牙套节点、鼓励横幅。
 * 首页按记录模式隔离（模式即工作台）：单模 / 双模的阶段数据彼此独立。
 */
public interface HomeService {

    /**
     * 获取首页聚合数据（按记录模式过滤阶段）。
     *
     * @param userId 当前登录用户 ID
     * @param mode   记录模式编码（CLEAR_SINGLE / CLEAR_DUAL；空或缺省按 CLEAR_SINGLE，兼容存量数据）
     * @return 首页聚合 VO
     */
    HomeSummaryVO getHomeSummary(Long userId, String mode);
}
