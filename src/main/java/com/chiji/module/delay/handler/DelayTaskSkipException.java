package com.chiji.module.delay.handler;

/**
 * 业务性跳过异常：handler 判定任务无需执行（如超时前已戴回、用户已关闭开关、
 * 订阅额度不足）时抛出。任务视为正常完成（置 DONE），原因记入 last_error，
 * 不消耗重试次数——与「执行失败」（抛其他异常、进入重试）严格区分。
 */
public class DelayTaskSkipException extends RuntimeException {

    public DelayTaskSkipException(String reason) {
        super(reason);
    }
}
