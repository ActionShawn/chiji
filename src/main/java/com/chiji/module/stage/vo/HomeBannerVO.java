package com.chiji.module.stage.vo;

import lombok.Builder;

/**
 * 首页鼓励横幅 VO。
 * <p>
 * 由后端基于当前 active 牙套副的进度派生，对应前端 {@code mock.homeBanner}。
 *
 * @param emoji 表情符号（固定 🌿）
 * @param title 标题（如「第8副第6天，已经走过一大半啦～」）
 * @param sub   副标题（如「还有1天就可以换下一副了」）
 */
@Builder
public record HomeBannerVO(String emoji, String title, String sub) {
}
