package com.chiji.module.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.User;
import com.chiji.enums.UserCancelReasonEnum;
import com.chiji.framework.storage.FileStorageStrategy;
import com.chiji.module.auth.dto.UserCancelRequest;
import com.chiji.module.auth.mapper.AccountCancelMapper;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.auth.service.UserCancelService;
import com.chiji.module.auth.vo.CancelPreviewVO;
import com.chiji.module.delay.mapper.DelayTaskMapper;
import com.chiji.module.wear.support.TakeoffTimeoutSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号注销服务实现。
 * <p>
 * 严格遵守方案约束：清理/匿名化/删除全部走原生 SQL（见 {@link AccountCancelMapper}），
 * 事务顺序按 §5.3，删文件与登出放在事务提交之后（COS/磁盘删除不可回滚，放事务内有
 * 「库回滚了文件却已删」的不一致风险）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCancelServiceImpl implements UserCancelService {

    /** 危险确认词（服务端防线，前端校验只是体验） */
    private static final String CONFIRM_WORD = "注销";

    /** 补充说明最大字符数（超长截断而非报错） */
    private static final int REMARK_MAX_LEN = 200;

    private final UserMapper userMapper;
    private final AccountCancelMapper accountCancelMapper;
    private final DelayTaskMapper delayTaskMapper;
    private final FileStorageStrategy fileStorageStrategy;
    private final ObjectMapper objectMapper;

    @Override
    public CancelPreviewVO preview(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        long recordDays = accountCancelMapper.countRecordDays(userId);
        long mediaCount = accountCancelMapper.countMedia(userId);
        long milestoneCount = accountCancelMapper.countMilestone(userId);
        Integer days = accountCancelMapper.selectDaysWithUs(userId);
        long daysWithUs = (days == null || days < 1) ? 1L : days;
        return new CancelPreviewVO(recordDays, mediaCount, milestoneCount, daysWithUs);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, UserCancelRequest request) {
        // ① confirm 校验：不正确立即 BAD_REQUEST，DB 无任何变化
        if (request == null || !CONFIRM_WORD.equals(request.confirm())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入「注销」以确认");
        }
        // 幂等：user 行已不存在（重复提交 / 弱网重试）同样按成功返回
        if (userMapper.selectById(userId) == null) {
            log.info("账号注销幂等跳过（user 行已不存在）, userId={}", userId);
            return;
        }

        // ② 写注销原因（匿名统计，先落地；与本事务同生共死）
        accountCancelMapper.insertCancelReason(
                IdWorker.getId(),
                buildReasonCodesJson(request.reasonCodes()),
                truncate(request.reasonRemark(), REMARK_MAX_LEN),
                LocalDateTime.now());

        // ③ 收口该用户待触发的摘下超时提醒（必须在删 wear_session 之前：biz_key 由 sessionId 拼出）
        for (Long sessionId : accountCancelMapper.selectWearSessionIds(userId)) {
            delayTaskMapper.cancelByBizKey(TakeoffTimeoutSpec.bizKey(sessionId));
        }

        // ④ 匿名化：运营/统计/工单表保留行、user_id 置 0（工单图片保留，不删文件）
        accountCancelMapper.anonymizeFeedback(userId);
        accountCancelMapper.anonymizeFeedbackComment(userId);
        accountCancelMapper.anonymizeLoginLog(userId);
        accountCancelMapper.anonymizeUsageSession(userId);
        accountCancelMapper.anonymizeSlowApiLog(userId);

        // ⑤ 读出待删文件 URL（须在删 record_media 之前，否则读不到）
        List<String> mediaUrls = accountCancelMapper.selectRecordMediaUrls(userId);

        // ⑥ 硬删业务表：严格按 §3.2 由父到子逆序，子表用子查询定位
        accountCancelMapper.deleteRecordTagRelations(userId);
        accountCancelMapper.deleteRecordMedia(userId);
        accountCancelMapper.deleteCompareMilestones(userId);
        accountCancelMapper.deleteTimelineRecords(userId);
        accountCancelMapper.deleteAligners(userId);
        accountCancelMapper.deleteStages(userId);
        accountCancelMapper.deleteChewSessions(userId);
        accountCancelMapper.deleteWearSessionEditLogs(userId);
        accountCancelMapper.deleteWearDailySummaries(userId);
        accountCancelMapper.deleteWearSessions(userId);
        accountCancelMapper.deleteClinicExpenses(userId);
        accountCancelMapper.deleteClinicVisits(userId);
        accountCancelMapper.deleteClinicExpenseCategories(userId);
        accountCancelMapper.deleteTags(userId);
        accountCancelMapper.deleteMessages(userId);
        accountCancelMapper.deleteUserSettings(userId);
        accountCancelMapper.deleteUserToolConfigs(userId);
        accountCancelMapper.deleteUserNotificationConfigs(userId);
        accountCancelMapper.deleteUserSubscribeQuotas(userId);
        accountCancelMapper.deleteAiMessages(userId);
        accountCancelMapper.deleteAiConversations(userId);

        // ⑦ 物理删 user 行（原生 SQL，绕开 @TableLogic，释放 uk_user_openid 槽位）
        userMapper.deletePhysicallyById(userId);

        // ⑧ 注册「提交后」动作：异步删文件 + 失效登录态
        registerAfterCommitActions(userId, mediaUrls);

        log.info("账号注销事务内清理就绪, userId={}, 待删文件={}", userId, mediaUrls.size());
    }

    /**
     * 注册事务提交后动作（提交才执行，回滚则自动作废）。
     *
     * @param userId    用户 ID
     * @param mediaUrls 待删文件 URL
     */
    private void registerAfterCommitActions(Long userId, List<String> mediaUrls) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 兜底：理论上 cancel 全程在事务内；无同步活动时直接执行，保证不留文件与旧 token
            afterCommit(userId, mediaUrls);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                UserCancelServiceImpl.this.afterCommit(userId, mediaUrls);
            }
        });
    }

    /**
     * 事务提交后动作：登出 + 异步删文件（失败只记日志，绝不影响已完成的注销）。
     *
     * @param userId    用户 ID
     * @param mediaUrls 待删文件 URL
     */
    private void afterCommit(Long userId, List<String> mediaUrls) {
        // 登出：彻底断掉旧 token（本仓库此前无 logout 调用先例，用 Sa-Token 显式 loginId 登出）
        try {
            StpUtil.logout(userId);
            log.info("注销后已登出, userId={}", userId);
        } catch (Exception e) {
            log.warn("注销后登出失败（不影响注销结果）, userId={}", userId, e);
        }

        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return;
        }
        // 独立虚拟线程异步删文件，不占用请求线程
        Thread.ofVirtual().name("account-cancel-file-" + userId)
                .start(() -> deleteFiles(userId, mediaUrls));
    }

    /**
     * 逐个删除文件；单个失败只记 warn 并继续（注销已提交，不可因删文件失败而回退）。
     *
     * @param userId 用户 ID（日志定位）
     * @param urls   待删 URL 列表
     */
    private void deleteFiles(Long userId, List<String> urls) {
        int ok = 0;
        int fail = 0;
        for (String url : urls) {
            try {
                fileStorageStrategy.delete(url);
                ok++;
            } catch (Exception e) {
                fail++;
                log.warn("注销删文件失败, userId={}, url={}", userId, url, e);
            }
        }
        log.info("注销文件清理完成, userId={}, total={}, ok={}, fail={}", userId, urls.size(), ok, fail);
    }

    /**
     * 原因码过滤 + 序列化为 JSON：白名单校验、未知码丢弃、去重、上限 8；全不选返回 null。
     *
     * @param codes 原始原因码（可空）
     * @return JSON 数组字符串；无合法码时为 null
     */
    private String buildReasonCodesJson(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> filtered = codes.stream()
                .filter(UserCancelReasonEnum::isValidCode)
                .distinct()
                .limit(8)
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(filtered);
        } catch (JsonProcessingException e) {
            log.warn("注销原因码序列化失败，按未填写处理", e);
            return null;
        }
    }

    /**
     * 截断补充说明（去首尾空白；空串按未填处理；按码点截断避免截断代理对）。
     *
     * @param text 原始文本
     * @param max  最大字符数
     * @return 处理后的文本；未填时为 null
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) <= max) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, max));
    }
}
