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

# ============================ 运行阶段：JDK 21 JRE ============================
# 官方 openjdk 镜像已弃用，选用 eclipse-temurin（Ubuntu 22.04 基础，稳定兼容）
FROM eclipse-temurin:21-jre-jammy

# 业务依赖 Asia/Shanghai 时区（记录创建时间、日志时间戳）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装/更新系统 CA 证书，并同步为 JVM 信任库
# （精简 JRE 镜像缺省证书不完整，会导致调用微信 api.weixin.qq.com 等 HTTPS 接口时 PKIX 证书校验失败）
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates ca-certificates-java && \
    update-ca-certificates -f && \
    rm -rf /var/lib/apt/lists/* && \
    cp -f /etc/ssl/certs/java/cacerts "$JAVA_HOME/lib/security/cacerts"

# 运行时工作目录（日志默认落盘 ./logs）
WORKDIR /app

# 将构建产物 jar 拷贝到运行时目录
COPY --from=build /app/target/chiji-server-1.0.0.jar /app/app.jar

# 暴露端口：此处端口必须与「服务设置」中填写的容器端口一致（8080）
EXPOSE 8080

# 启动命令（只保留一行 CMD，多行只有最后一行生效）
CMD ["java", "-Xmx512m", "-Xms256m", "-XX:+HeapDumpOnOutOfMemoryError", "-jar", "/app/app.jar"]
