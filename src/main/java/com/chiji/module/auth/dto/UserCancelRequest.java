package com.chiji.module.auth.dto;

import java.util.List;

/**
 * 账号注销请求体。
 * <p>
 * 校验口径见方案 §5.1：{@code confirm} 必填且必须等于「注销」（在 Service 内手工校验，
 * 以便返回固定文案「请输入「注销」以确认」）；{@code reasonCodes} 可为空数组，按白名单过滤、
 * 未知码丢弃；{@code reasonRemark} 选填、超长截断而非报错。
 */
public record UserCancelRequest(

        /** 确认词：必须等于字符串「注销」，否则 BAD_REQUEST */
        String confirm,

        /** 注销原因码数组（可为空数组/null；白名单过滤，未知码丢弃，长度上限 8） */
        List<String> reasonCodes,

        /** 补充说明（选填，≤200 字，超长截断） */
        String reasonRemark
) {
}
