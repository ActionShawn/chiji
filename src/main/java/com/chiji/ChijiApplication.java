package com.chiji;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 齿迹应用启动入口。
 * <p>
 * 采用 Spring Boot 3.5 + JDK21 虚拟线程，开启 MyBatis-Plus Mapper 扫描：
 * 业务模块落地后，在 {@code com.chiji.module} 包下的实体接口上标注
 * {@link Mapper} 注解即会被自动注册。
 */
@SpringBootApplication
@MapperScan(basePackages = "com.chiji.module", annotationClass = Mapper.class)
public class ChijiApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ChijiApplication.class, args);
    }
}
