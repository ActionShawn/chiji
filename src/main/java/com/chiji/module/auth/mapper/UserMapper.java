package com.chiji.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 * <p>
 * 由 {@link org.springframework.context.annotation.Configuration @SpringBootApplication}
 * 上的 {@code @MapperScan(basePackages = "com.chiji.module")} 扫描注册。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
