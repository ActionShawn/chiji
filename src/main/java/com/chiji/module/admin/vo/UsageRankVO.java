package com.chiji.module.admin.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端使用时长排行 VO（运营看板）。
 * <p>
 * 隐私约定：昵称脱敏展示（首字符 + **），不出现 openid/头像等可识别信息。
 */
@Data
@Builder
public class UsageRankVO {

    /** 名次（1 起） */
    private int rank;

    /** 脱敏昵称（无昵称回退「用户+ID 后 4 位」） */
    private String nickname;

    /** 区间内使用时长（秒） */
    private long totalSec;
}
