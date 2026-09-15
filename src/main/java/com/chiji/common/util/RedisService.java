package com.chiji.common.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 通用操作服务。
 * <p>
 * 封装常用的缓存读写操作，业务模块直接注入本服务即可，无需直接接触 {@link RedisTemplate}。
 */
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 构造方法注入。
     *
     * @param redisTemplate Redis 模板
     */
    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入缓存（无过期时间）。
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入缓存（带过期时间）。
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     */
    public void set(String key, Object value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    /**
     * 读取缓存，不存在时返回 null。
     *
     * @param key 键
     * @param <T> 值类型
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存。
     *
     * @param key 键
     * @return 是否删除成功
     */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 判断缓存是否存在。
     *
     * @param key 键
     * @return 是否存在
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置过期时间。
     *
     * @param key     键
     * @param timeout 过期时长
     * @return 是否设置成功
     */
    public Boolean expire(String key, Duration timeout) {
        return redisTemplate.expire(key, timeout);
    }

    /**
     * 自增计数并设置过期时间（限频用）。
     * <p>
     * 首次自增（返回 1）时设置过期时间；Redis 异常时返回 0，
     * 调用方按「未超限」处理（限频是保护措施，故障时放行埋点）。
     *
     * @param key     键
     * @param timeout 首次自增后的过期时长
     * @return 自增后的值（异常时 0）
     */
    public long increment(String key, Duration timeout) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, timeout);
            }
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }
}
