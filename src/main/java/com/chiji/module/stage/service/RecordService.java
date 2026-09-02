package com.chiji.module.stage.service;

import com.chiji.module.stage.dto.CreateRecordRequest;
import com.chiji.module.stage.vo.RecordVO;

import java.util.List;

/**
 * 时光轴记录服务。
 * <p>
 * 负责记录的创建与查询：
 * <ul>
 *   <li>{@link #createRecord}：保存一条记录（文字 + 标签），自动关联阶段与牙套副</li>
 *   <li>{@link #listByAligner}：查询某牙套副下的记录列表（供牙套详情页展示）</li>
 *   <li>{@link #listByUser}：查询用户全部记录（供时间轴页展示，按日期倒序）</li>
 * </ul>
 */
public interface RecordService {

    /**
     * 创建一条记录。
     * <p>
     * 校验阶段归属，保存正文，自动创建自定义标签并关联，返回展示 VO。
     *
     * @param userId  当前用户 ID
     * @param request 创建请求
     * @return 保存后的记录 VO
     */
    RecordVO createRecord(Long userId, CreateRecordRequest request);

    /**
     * 查询某牙套副下的记录列表。
     *
     * @param userId    当前用户 ID
     * @param alignerId 牙套副 ID
     * @return 记录列表（按记录日期倒序）
     */
    List<RecordVO> listByAligner(Long userId, Long alignerId);

    /**
     * 查询用户全部记录。
     *
     * @param userId 当前用户 ID
     * @return 记录列表（按记录日期倒序）
     */
    List<RecordVO> listByUser(Long userId);

    /**
     * 查询某阶段下的记录列表。
     * <p>
     * 校验阶段归属，仅返回该用户对应阶段的记录（时间轴页「查看全部」按当前阶段过滤）。
     *
     * @param userId  当前用户 ID
     * @param stageId 阶段 ID
     * @return 记录列表（按记录日期倒序）
     */
    List<RecordVO> listByStage(Long userId, Long stageId);

    /**
     * 查询单条记录详情。
     * <p>
     * 校验记录归属，仅允许查看自己的记录（首页「最近记录」缩略图点击后弹出预览弹窗）。
     *
     * @param userId   当前用户 ID
     * @param recordId 记录 ID
     * @return 记录 VO
     */
    RecordVO getRecord(Long userId, Long recordId);

    /**
     * 删除一条记录（逻辑删除）。
     * <p>
     * 校验记录归属，仅允许删除自己的记录；使用 {@code deleted=1} 逻辑删除，数据可恢复。
     *
     * @param userId   当前用户 ID
     * @param recordId 记录 ID
     */
    void deleteRecord(Long userId, Long recordId);
}
