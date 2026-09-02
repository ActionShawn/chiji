package com.chiji.module.feedback.service;

import com.chiji.common.core.page.CursorPage;
import com.chiji.module.feedback.dto.SubmitFeedbackRequest;
import com.chiji.module.feedback.vo.FeedbackVO;

/**
 * 意见反馈服务。
 */
public interface FeedbackService {

    /**
     * 提交意见（正文 + 图片 + 可选联系方式）。
     *
     * @param userId  当前登录用户 ID
     * @param request 提交请求
     * @return 保存后的反馈 VO
     */
    FeedbackVO submit(Long userId, SubmitFeedbackRequest request);

    /**
     * 分页查询当前用户的反馈历史（游标分页，按 id 倒序）。
     *
     * @param userId 当前登录用户 ID
     * @param limit  每页条数（1-50）
     * @param cursor 上一页最后一条 id（首页传 null）
     * @return 游标分页结果
     */
    CursorPage<FeedbackVO> listByUser(Long userId, int limit, Long cursor);
}
