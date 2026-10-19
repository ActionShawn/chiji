package com.chiji.module.auth.service;

import com.chiji.module.auth.dto.UserCancelRequest;
import com.chiji.module.auth.vo.CancelPreviewVO;

/**
 * 账号注销服务（自助注销，立即物理删除、不可逆）。
 * <p>
 * 入口在「我的 - 隐私与授权」底部；数据处置口径见方案 §3，事务顺序见 §5.3。
 * 后端不做管理员角色拦截（仅前端隐藏入口），避免「前端藏了、库里却删掉管理员账号」的不可逆事故。
 */
public interface UserCancelService {

    /**
     * 挽留屏数据盘点（只读）。
     *
     * @param userId 当前登录用户 ID
     * @return 盘点数据（recordDays / mediaCount / milestoneCount / daysWithUs）
     */
    CancelPreviewVO preview(Long userId);

    /**
     * 执行注销：单事务内写原因 → 收口延迟任务 → 匿名化 → 硬删业务表 → 物理删 user 行；
     * 提交后异步删文件并失效登录态。
     * <p>
     * 幂等：user 行已不存在时同样按成功返回（避免弱网重试拿到错误）。
     *
     * @param userId  当前登录用户 ID
     * @param request 注销请求（confirm 必须为「注销」）
     */
    void cancel(Long userId, UserCancelRequest request);
}
