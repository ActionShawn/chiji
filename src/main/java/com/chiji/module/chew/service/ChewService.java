package com.chiji.module.chew.service;

import com.chiji.module.chew.dto.ChewStartRequest;
import com.chiji.module.chew.dto.ChewStateChangeRequest;
import com.chiji.module.chew.vo.ChewSessionVO;
import com.chiji.module.chew.vo.TodayChewVO;

/**
 * 咬胶会话状态机服务（#1–#5 + #12）。
 * <p>
 * 不变式：同用户至多一个未结束会话（{@code user_id + deleted=0 + ended_at IS NULL}）。
 * 所有「进行中/回读/幂等」查询必须带 {@code deleted = 0}（自定义 {@code @Select} 已显式声明，
 * Wrapper 依赖 {@code @TableLogic} 自动追加）。时间戳一律服务端生成。
 */
public interface ChewService {

    /**
     * 开始咬胶（#1）。
     * <p>
     * 幂等：先查同用户进行中会话，命中直接返回既有；否则以 {@code clientSessionId} 唯一键
     * {@code INSERT IGNORE} 落库，冲突后回读返回（不报错）。开始即快照当前 ACTIVE 阶段/副。
     *
     * @param userId 用户 ID
     * @param req    开始请求
     * @return 会话视图
     */
    ChewSessionVO start(Long userId, ChewStartRequest req);

    /**
     * 暂停（#2）：未结束且未暂停时置暂停起点；已暂停则原样返回（幂等）。
     *
     * @param userId 用户 ID
     * @param id     会话 ID
     * @param req    目标调整（可空）
     * @return 会话视图
     */
    ChewSessionVO pause(Long userId, Long id, ChewStateChangeRequest req);

    /**
     * 继续（#3）：暂停中时把 {@code now − pausedAt} 一次性结算进 {@code pauseSec} 并清暂停起点；
     * 非暂停则原样返回（幂等）。
     *
     * @param userId 用户 ID
     * @param id     会话 ID
     * @param req    目标调整（可空）
     * @return 会话视图
     */
    ChewSessionVO resume(Long userId, Long id, ChewStateChangeRequest req);

    /**
     * 重来（#4）：复位同一行（不新建），{@code startedAt = now}、{@code pauseSec = 0}、
     * {@code pausedAt = null}，保留原阶段/副快照。
     *
     * @param userId 用户 ID
     * @param id     会话 ID
     * @param req    目标调整（可空）
     * @return 会话视图
     */
    ChewSessionVO restart(Long userId, Long id, ChewStateChangeRequest req);

    /**
     * 结束（#5）：结算未结暂停段并置结束时刻；已结束则幂等返回既有（不覆盖 {@code endedAt}）。
     * 净时长 &lt; {@link com.chiji.module.chew.support.ChewConstants#MIN_VALID_NET_SEC} 视为误触，
     * 逻辑删除且返回 null。
     *
     * @param userId 用户 ID
     * @param id     会话 ID
     * @param req    目标调整（可空）
     * @return 会话视图；误触丢弃时 null
     */
    ChewSessionVO finish(Long userId, Long id, ChewStateChangeRequest req);

    /**
     * 丢弃未结束会话（#12，P0）：仅 RUNNING/PAUSED 可丢弃（软删 {@code deleted=1}）；
     * 已结束记录 → {@code CHEW_SESSION_FORBIDDEN}；不存在/非本人 → {@code CHEW_SESSION_NOT_FOUND}（不区分）。
     *
     * @param userId 用户 ID
     * @param id     会话 ID
     * @return 丢弃后的今日累计视图（回显）
     */
    TodayChewVO discard(Long userId, Long id);
}
