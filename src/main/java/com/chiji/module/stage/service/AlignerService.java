package com.chiji.module.stage.service;

import com.chiji.entity.Stage;
import com.chiji.module.stage.dto.AlignerTimeUpdateRequest;
import com.chiji.module.stage.dto.RevertAlignerRequest;
import com.chiji.module.stage.vo.AlignerNodeVO;

import java.util.List;

/**
 * 牙套副服务。
 * <p>
 * 负责阶段内牙套副的批量生成、换副与时间调整：
 * <ul>
 *   <li>{@link #batchCreateAligners}：创建阶段时按 count / daysPerAligner / startDate 批量预排期</li>
 *   <li>{@link #finishAligner}：手动结束当前副（按上/下午半天规则确定结束日），自动开始下一副并联动重排；
 *       结束最后一副时同时把阶段置 ENDED（结束阶段）</li>
 *   <li>{@link #revertAligner}：反向换副（撤回误操作），按上一副佩戴进度分情况恢复——
 *       超期换的副由用户决定「再戴几天」，提前换的副按原计划终点还原</li>
 *   <li>{@link #updateAlignerTime}：调整单副开始/结束时间，后续副按各自天数自动顺延</li>
 * </ul>
 */
public interface AlignerService {

    /**
     * 批量生成阶段下的牙套副。
     * <p>
     * 创建阶段后调用，按 {@code count} 生成 N 副，第 1 副为 ACTIVE，其余为 FUTURE。
     * 按 {@code stage.startDate} + {@code daysPerAligner} 批量预排期 startDate / endDate：
     * <ul>
     *   <li>第 1 副：startDate = stage.startDate，endDate = startDate + days - 1</li>
     *   <li>第 N 副：startDate = 第N-1副 endDate + 1，endDate = startDate + days - 1</li>
     * </ul>
     * stage.startDate 为 null 时仅第 1 副 ACTIVE、currentDay=1，日期留空。
     *
     * @param stage 已创建的阶段
     */
    void batchCreateAligners(Stage stage);

    /**
     * 手动结束当前副并开始下一副。
     * <p>
     * 流程：
     * <ol>
     *   <li>校验 alignerId 归属当前用户，且 state=ACTIVE</li>
     *   <li>当前副 state → DONE，endDate = 昨天（前一天）</li>
     *   <li>下一副（num+1）state → ACTIVE，startDate = 今天（当前天），currentDay = 1</li>
     *   <li>联动重排后续 FUTURE 副：从今天 + daysPerAligner 起依次重排 startDate/endDate</li>
     * </ol>
     *
     * @param userId    当前用户 ID
     * @param alignerId 要结束的牙套副 ID
     * @return 更新后的节点列表（供前端刷新展示）
     */
    List<AlignerNodeVO> finishAligner(Long userId, Long alignerId);

    /**
     * 反向换副：把当前 ACTIVE 副回退为 FUTURE，上一副 DONE 恢复为 ACTIVE。
     * <p>
     * 用于用户误操作「换下一副」后撤回，并支持给上一副「多戴几天」。
     * 按上一副佩戴进度分两种情况（已戴天数 = today - startDate + 1，周期 = 该副 totalDays 回退阶段默认）：
     * <ol>
     *   <li>校验 alignerId 归属当前用户，且 state=ACTIVE；上一副（num-1）存在且 state=DONE</li>
     *   <li>上一副已<b>戴满周期</b>（超期后才换的副）：endDate = max(用户指定延长终点, 今天)。
     *       用户在弹窗选择「再戴几天」（含今天起算），前端折算为 {@code request.endDate} 传入；
     *       未传时默认只归还今天（纯撤销）</li>
     *   <li>上一副<b>未戴满周期</b>（提前换的副）：按原计划终点（startDate + totalDays - 1）恢复，等于还原原排期</li>
     *   <li>当前副 state → FUTURE，startDate = 上一副新 endDate + 1，endDate 按该副自身 totalDays，清 currentDay</li>
     *   <li>联动重排后续 FUTURE 副：从当前副起按各自 totalDays 无缝顺延</li>
     * </ol>
     *
     * @param userId    当前用户 ID
     * @param alignerId 当前 ACTIVE 副的 ID（即将被回退为 FUTURE）
     * @param request   换回请求（endDate 可选，仅超期场景用于指定延长终点）
     * @return 更新后的节点列表（供前端刷新展示）
     */
    List<AlignerNodeVO> revertAligner(Long userId, Long alignerId, RevertAlignerRequest request);

    /**
     * 调整单副开始/结束时间（锚点式联动，批量顺延后续副）。
     * <p>
     * 规则：
     * <ol>
     *   <li>校验 alignerId 归属当前用户</li>
     *   <li>仅 startDate：该副天数不变，endDate = startDate + totalDays - 1</li>
     *   <li>仅 endDate：该副天数变化，totalDays = endDate - startDate + 1</li>
     *   <li>两者都传：按给定起止调整该副天数</li>
     *   <li>校验天数 ≥ 1，且新开始时间不早于上一副结束时间（不得重叠/倒序）</li>
     *   <li>该副之后的全部副按各自 totalDays 从上一副 endDate+1 起依次顺延</li>
     * </ol>
     * ACTIVE 副的 currentDay 按新 startDate 动态推算；DONE 副的实际佩戴天数由起止日期派生。
     *
     * @param userId    当前用户 ID
     * @param alignerId 要调整的牙套副 ID
     * @param req       时间调整请求（startDate/endDate 至少一个）
     * @return 更新后的节点列表（供前端刷新展示）
     */
    List<AlignerNodeVO> updateAlignerTime(Long userId, Long alignerId, AlignerTimeUpdateRequest req);
}
