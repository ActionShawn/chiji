package com.chiji.module.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新个人资料请求体。
 * <p>
 * nickname / avatarUrl 均可选提交（只改想改的字段）；
 * 传空字符串表示清除对应字段，后端统一存 null。
 */
public record UpdateUserProfileRequest(

        /** 昵称（可空，空字符串表示清除；非空时 2-20 字） */
        @Size(max = 20, message = "昵称最多20个字")
        String nickname,

        /** 头像 URL（可空，空字符串表示清除；由上传接口返回） */
        @Size(max = 512, message = "头像地址过长")
        String avatarUrl
) {
}
