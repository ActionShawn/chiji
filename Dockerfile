# 二开推荐阅读[如何提高项目构建效率](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/scene/build/speed.html)
# ============================ 构建阶段：Maven 3.9 + JDK 21 ============================
# 项目要求 Spring Boot 3.5.3 + JDK 21（虚拟线程），构建镜像必须为 JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# 先拷贝 pom 与 settings.xml（腾讯镜像加速依赖下载），利用 Docker 层缓存复用依赖
COPY pom.xml settings.xml ./
RUN mvn -s /app/settings.xml -B dependency:go-offline || true

# 再拷贝源码执行打包
COPY src ./src
RUN mvn -s /app/settings.xml -B clean package -DskipTests

# 按 Spring Boot 分层规范拆包：依赖层内容稳定，可被后续构建复用镜像层，
# 显著减少每次部署向 TCR 推送、节点拉取的体积（总大小不变，但加快发布与冷启动）
RUN java -Djarmode=layertools -jar target/chiji-server-1.0.0.jar extract

# ============================ 运行阶段：JDK 21 JRE（Alpine 精简基础镜像）============================
# 由 Ubuntu(jammy) 换为 Alpine：剔除整套 Ubuntu 用户态，镜像大幅瘦身（原 819MB），
# 缩短云托管冷启动的镜像拉取耗时，避免健康检查窗口内 8080 尚未监听而报 connection refused
FROM eclipse-temurin:21-jre-alpine

# 业务依赖 Asia/Shanghai 时区（记录创建时间、日志时间戳）
ENV TZ=Asia/Shanghai
# Alpine 基础镜像已自带完整 JVM 信任库（cacerts），此处仅补齐 tzdata / ca-certificates 系统层，
# 相比原先 apt 安装 ca-certificates-java 再回写 cacerts 更轻、构建更快
RUN apk add --no-cache tzdata ca-certificates

# 运行时工作目录（日志默认落盘 ./logs）
WORKDIR /app

# 分层拷贝：依赖层（变化少）在前，应用层（每次构建变化）在后，最大化镜像层缓存复用
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./

# 暴露端口：此处端口必须与「服务设置」中填写的容器端口一致（8080）
EXPOSE 8080

# 启动命令（只保留一行，多行只有最后一行生效）；JarLauncher 由 spring-boot-loader 层提供
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-XX:+HeapDumpOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
