package com.chiji.module.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.FeedbackComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 反馈评论 Mapper。
 */
@Mapper
public interface FeedbackCommentMapper extends BaseMapper<FeedbackComment> {

    /**
     * 用户端未读数：当前用户名下所有反馈中，运营在「用户最近已读时刻」之后的新评论数。
     * <p>
     * {@code user_read_at IS NULL} 表示从未进入对话详情页，其后产生的运营评论全部计未读；
     * 存量数据迁移时已初始化为迁移时刻，不会误亮。
     *
     * @param userId 提交用户 ID
     * @return 未读运营评论数
     */
    @Select("SELECT COUNT(*) FROM feedback_comment c "
            + "JOIN feedback f ON f.id = c.feedback_id AND f.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.role = 'ADMIN' AND f.user_id = #{userId} "
            + "AND (f.user_read_at IS NULL OR c.created_at > f.user_read_at)")
    long countUserUnread(@Param("userId") Long userId);

    /**
     * 管理端未读数：所有反馈中用户侧的新消息数 = 新反馈首条（feedback 行本体）+ 用户新评论。
     * <p>
     * {@code admin_read_at IS NULL} 表示运营从未进入该反馈对话（含全部新反馈），其用户消息全部计未读；
     * 存量数据迁移时已初始化为迁移时刻，不会误亮。
     *
     * @return 未读用户消息数
     */
    @Select("(SELECT COUNT(*) FROM feedback WHERE deleted = 0 "
            + "AND (admin_read_at IS NULL OR created_at > admin_read_at)) "
            + "+ (SELECT COUNT(*) FROM feedback_comment c "
            + "JOIN feedback f ON f.id = c.feedback_id AND f.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.role = 'USER' "
            + "AND (f.admin_read_at IS NULL OR c.created_at > f.admin_read_at))")
    long countAdminUnread();

    /**
     * 批量判定「有未读运营回复」的反馈 ID（历史列表逐条角标用，一次查询避免 N+1）。
     *
     * @param userId 提交用户 ID
     * @param ids    待判定的反馈 ID 列表（当前页）
     * @return 其中存在未读运营评论的反馈 ID 子集
     */
    @Select("<script>"
            + "SELECT DISTINCT c.feedback_id FROM feedback_comment c "
            + "JOIN feedback f ON f.id = c.feedback_id AND f.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.role = 'ADMIN' AND f.user_id = #{userId} "
            + "AND (f.user_read_at IS NULL OR c.created_at > f.user_read_at) "
            + "AND c.feedback_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    Set<Long> selectUnreadFeedbackIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
