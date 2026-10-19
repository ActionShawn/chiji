package com.chiji.module.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号注销清理 Mapper（原生 SQL，不继承 BaseMapper）。
 * <p>
 * 关键约束：所有清理/匿名化 SQL 一律使用 {@code @Delete/@Update} 原生注解，<b>不走
 * {@code BaseMapper.delete()}</b>——业务表普遍带 {@code deleted} 列且全局配置
 * {@code logic-delete-field: deleted}，走 MP 通用删除会被改写成 {@code UPDATE ... SET deleted = 1}，
 * 与「物理硬删」语义相悖（会残留数据且不释放唯一键槽位）。
 * <p>
 * 仅 SQL 文本跨表（子表无 {@code user_id} 时用子查询定位），无编译期跨模块依赖，
 * ArchUnit 三条规则（common/framework 分层、mapper 仅被 service 访问）不受影响。
 */
@Mapper
public interface AccountCancelMapper {

    // ─────────────────────── 挽留屏数据盘点（只读） ───────────────────────

    /**
     * 已记录天数：去重记录日期数（口径见方案 §5.1）。
     *
     * @param userId 用户 ID
     * @return 去重记录日期数
     */
    @Select("SELECT COUNT(DISTINCT record_date) FROM timeline_record "
            + "WHERE user_id = #{userId} AND deleted = 0")
    long countRecordDays(@Param("userId") Long userId);

    /**
     * 媒体数：该用户记录下的图片/视频总数（口径见方案 §5.1）。
     *
     * @param userId 用户 ID
     * @return 媒体行数
     */
    @Select("SELECT COUNT(*) FROM record_media m JOIN timeline_record r ON m.record_id = r.id "
            + "WHERE r.user_id = #{userId} AND m.deleted = 0 AND r.deleted = 0")
    long countMedia(@Param("userId") Long userId);

    /**
     * 对比印记数（口径见方案 §5.1）。
     *
     * @param userId 用户 ID
     * @return 印记行数
     */
    @Select("SELECT COUNT(*) FROM compare_milestone WHERE user_id = #{userId} AND deleted = 0")
    long countMilestone(@Param("userId") Long userId);

    /**
     * 陪伴天数：注册日至今，当天注册当天注销也最小取 1（口径见方案 §5.1）。
     *
     * @param userId 用户 ID
     * @return 陪伴天数（不小于 1）；用户行不存在时为 null
     */
    @Select("SELECT GREATEST(DATEDIFF(CURDATE(), DATE(created_at)), 1) FROM user WHERE id = #{userId}")
    Integer selectDaysWithUs(@Param("userId") Long userId);

    // ─────────────────────── 注销原因匿名落库 ───────────────────────

    /**
     * 写一行注销原因匿名统计（无 user_id / 无 openid，纯汇总用）。
     *
     * @param id          主键（雪花）
     * @param reasonCodes 原因码数组 JSON 字符串；全不选时为 null
     * @param reasonRemark 补充说明（≤200 字，已截断）；为空时为 null
     * @param createdAt   注销时间
     * @return 影响行数
     */
    @Insert("INSERT INTO user_cancel_reason (id, reason_codes, reason_remark, created_at) "
            + "VALUES (#{id}, #{reasonCodes}, #{reasonRemark}, #{createdAt})")
    int insertCancelReason(@Param("id") Long id, @Param("reasonCodes") String reasonCodes,
                           @Param("reasonRemark") String reasonRemark,
                           @Param("createdAt") LocalDateTime createdAt);

    // ─────────────────────── 删除前取数（延迟任务 / 文件） ───────────────────────

    /**
     * 该用户全部佩戴会话 ID（用于在删 wear_session 之前收口 takeoff-timeout 延迟任务）。
     *
     * @param userId 用户 ID
     * @return 会话 ID 列表（含已逻辑删除行）
     */
    @Select("SELECT id FROM wear_session WHERE user_id = #{userId}")
    List<Long> selectWearSessionIds(@Param("userId") Long userId);

    /**
     * 该用户全部记录媒体 URL（提交后异步真删文件用；须在删 record_media 之前读出）。
     *
     * @param userId 用户 ID
     * @return 媒体 URL 列表（含已逻辑删除行）
     */
    @Select("SELECT m.url FROM record_media m JOIN timeline_record r ON m.record_id = r.id "
            + "WHERE r.user_id = #{userId}")
    List<String> selectRecordMediaUrls(@Param("userId") Long userId);

    // ─────────────────────── 匿名化（运营/统计/工单：保留行、user_id 置 0） ───────────────────────

    /**
     * 意见反馈工单匿名化（保留工单与图片，仅抹去用户归属）。
     *
     * @return 影响行数
     */
    @Update("UPDATE feedback SET user_id = 0 WHERE user_id = #{userId}")
    int anonymizeFeedback(@Param("userId") Long userId);

    /**
     * 反馈评论匿名化：仅抹 USER 角色的发言（ADMIN 行 user_id 本就为 0）。
     *
     * @return 影响行数
     */
    @Update("UPDATE feedback_comment SET user_id = 0 WHERE user_id = #{userId} AND role = 'USER'")
    int anonymizeFeedbackComment(@Param("userId") Long userId);

    /**
     * 登录日志匿名化。
     *
     * @return 影响行数
     */
    @Update("UPDATE login_log SET user_id = 0 WHERE user_id = #{userId}")
    int anonymizeLoginLog(@Param("userId") Long userId);

    /**
     * 使用会话匿名化。
     *
     * @return 影响行数
     */
    @Update("UPDATE usage_session SET user_id = 0 WHERE user_id = #{userId}")
    int anonymizeUsageSession(@Param("userId") Long userId);

    /**
     * 慢请求明细匿名化（该表 user_id 可 NULL）。
     *
     * @return 影响行数
     */
    @Update("UPDATE slow_api_log SET user_id = 0 WHERE user_id = #{userId}")
    int anonymizeSlowApiLog(@Param("userId") Long userId);

    // ─────────────────────── 业务表物理硬删（严格按 §3.2 由父到子逆序） ───────────────────────

    /** 1. 记录-标签关联（无 user_id，经 record_id 子查询定位）。 */
    @Delete("DELETE FROM record_tag_relation "
            + "WHERE record_id IN (SELECT id FROM timeline_record WHERE user_id = #{userId})")
    int deleteRecordTagRelations(@Param("userId") Long userId);

    /** 2. 记录媒体（无 user_id，经 record_id 子查询定位）。 */
    @Delete("DELETE FROM record_media "
            + "WHERE record_id IN (SELECT id FROM timeline_record WHERE user_id = #{userId})")
    int deleteRecordMedia(@Param("userId") Long userId);

    /** 3. 对比印记（引用 record_media.id，须先于 record_media/timeline_record 删）。 */
    @Delete("DELETE FROM compare_milestone WHERE user_id = #{userId}")
    int deleteCompareMilestones(@Param("userId") Long userId);

    /** 4. 时光轴记录。 */
    @Delete("DELETE FROM timeline_record WHERE user_id = #{userId}")
    int deleteTimelineRecords(@Param("userId") Long userId);

    /** 5. 牙套副（无 user_id，经 stage_id 子查询定位）。 */
    @Delete("DELETE FROM aligner WHERE stage_id IN (SELECT id FROM stage WHERE user_id = #{userId})")
    int deleteAligners(@Param("userId") Long userId);

    /** 6. 矫正阶段。 */
    @Delete("DELETE FROM stage WHERE user_id = #{userId}")
    int deleteStages(@Param("userId") Long userId);

    /** 7. 咬胶会话。 */
    @Delete("DELETE FROM chew_session WHERE user_id = #{userId}")
    int deleteChewSessions(@Param("userId") Long userId);

    /** 7. 佩戴会话编辑留痕。 */
    @Delete("DELETE FROM wear_session_edit_log WHERE user_id = #{userId}")
    int deleteWearSessionEditLogs(@Param("userId") Long userId);

    /** 7. 每日佩戴结算。 */
    @Delete("DELETE FROM wear_daily_summary WHERE user_id = #{userId}")
    int deleteWearDailySummaries(@Param("userId") Long userId);

    /** 7. 佩戴会话（须在延迟任务收口之后删）。 */
    @Delete("DELETE FROM wear_session WHERE user_id = #{userId}")
    int deleteWearSessions(@Param("userId") Long userId);

    /** 8. 矫正花费流水。 */
    @Delete("DELETE FROM clinic_expense WHERE user_id = #{userId}")
    int deleteClinicExpenses(@Param("userId") Long userId);

    /** 8. 复诊档案（日程 / 就诊记录）。 */
    @Delete("DELETE FROM clinic_visit WHERE user_id = #{userId}")
    int deleteClinicVisits(@Param("userId") Long userId);

    /** 8. 花费分类：仅删用户自定义（user_id ≠ 0），保留系统内置五类。 */
    @Delete("DELETE FROM clinic_expense_category WHERE user_id = #{userId} AND user_id <> 0")
    int deleteClinicExpenseCategories(@Param("userId") Long userId);

    /** 9. 标签：仅删用户自定义（user_id ≠ 0），保留系统内置预设。 */
    @Delete("DELETE FROM tag WHERE user_id = #{userId} AND user_id <> 0")
    int deleteTags(@Param("userId") Long userId);

    /** 9. 消息。 */
    @Delete("DELETE FROM message WHERE user_id = #{userId}")
    int deleteMessages(@Param("userId") Long userId);

    /** 9. 用户设置。 */
    @Delete("DELETE FROM user_setting WHERE user_id = #{userId}")
    int deleteUserSettings(@Param("userId") Long userId);

    /** 9. 首页常用工具配置。 */
    @Delete("DELETE FROM user_tool_config WHERE user_id = #{userId}")
    int deleteUserToolConfigs(@Param("userId") Long userId);

    /** 9. 用户通知类型开关。 */
    @Delete("DELETE FROM user_notification_config WHERE user_id = #{userId}")
    int deleteUserNotificationConfigs(@Param("userId") Long userId);

    /** 9. 一次性订阅额度。 */
    @Delete("DELETE FROM user_subscribe_quota WHERE user_id = #{userId}")
    int deleteUserSubscribeQuotas(@Param("userId") Long userId);

    /** 10. AI 问答消息（无 user_id 语义列可依赖，经 conversation_id 子查询定位）。 */
    @Delete("DELETE FROM ai_message WHERE conversation_id IN "
            + "(SELECT id FROM ai_conversation WHERE user_id = #{userId})")
    int deleteAiMessages(@Param("userId") Long userId);

    /** 11. AI 问答会话。 */
    @Delete("DELETE FROM ai_conversation WHERE user_id = #{userId}")
    int deleteAiConversations(@Param("userId") Long userId);
}
