# =====================================================================
# search-service 镜像
# 构建上下文必须是仓库根目录（需要父 pom 与公共模块）：
#   docker build -f backend/search-service/Dockerfile -t java-agent-platform/search-service:1.0.0 .
# =====================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 容器内构建使用阿里云镜像加速
COPY infrastructure/docker/maven-settings.xml /root/.m2/settings.xml

# 整仓拷贝源码：backend/pom.xml 中声明的每个 <module> 都必须存在，
# Maven 反应堆才能解析（只拷自己那一份 pom 会报 Child module ... does not exist）
COPY pom.xml ./
COPY backend ./backend

# BuildKit cache mount：5 个服务共用同一个 ~/.m2 仓库，依赖只下载一次，
# 仓库也不会被打进镜像层（.dockerignore 已排除 **/target）
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -pl backend/search-service -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

COPY --from=builder /build/backend/search-service/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]