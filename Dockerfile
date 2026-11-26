# 微信云托管 · 深空矿场服务端（Spring Boot 3 / Java 17）
# 文档：https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/basic/guide.html
#
# 本地构建：docker build -t deepmine-server .
# 本地运行：docker run -p 80:80 -e PORT=80 -e MYSQL_ADDRESS=host:3306 deepmine-server

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# 先只拷 pom / settings，让依赖单独成层：改业务代码时不用重新下载依赖
COPY settings.xml pom.xml /app/
RUN mvn -s /app/settings.xml -f /app/pom.xml -q -DskipTests dependency:go-offline || true

# 源码 + 打包（产物统一改名 app.jar，避免版本号变化导致 CMD 失效）
COPY src /app/src
RUN mvn -s /app/settings.xml -f /app/pom.xml -q -DskipTests clean package \
    && cp /app/target/*.jar /app/app.jar

# ---------- 运行阶段 ----------
# 用 Debian 版 Temurin（自带完整 cacerts）。
# alpine 版曾出现访问 api.weixin.qq.com 报
#   PKIX path building failed: unable to find valid certification path
FROM eclipse-temurin:17-jre

# 时区 + 系统根证书（Debian 版已自带 ca-certificates，这里确保是最新的）
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates tzdata \
    && ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && update-ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# 再把系统根证书导进 JVM 信任库（双保险：JDK 的 cacerts 和系统 /etc/ssl/certs 不是同一份）
RUN set -e; \
    if [ -f /etc/ssl/certs/ca-certificates.crt ] && command -v keytool >/dev/null 2>&1; then \
      mkdir -p /tmp/ca; \
      awk 'BEGIN{n=0} /BEGIN CERTIFICATE/{n++} {if(n>0) print > ("/tmp/ca/c" n ".pem")}' \
        /etc/ssl/certs/ca-certificates.crt; \
      for f in /tmp/ca/*.pem; do \
        keytool -importcert -noprompt -cacerts -storepass changeit -file "$f" >/dev/null 2>&1 || true; \
      done; \
      rm -rf /tmp/ca; \
    fi; \
    echo "JVM 信任库条目数: $(keytool -list -cacerts -storepass changeit 2>/dev/null | grep -c trustedCertEntry || echo 0)"

WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar

# 云托管「服务设置 → 端口」要填 80，与这里保持一致
EXPOSE 80
# 不用固定 -Xmx：容器内存小时固定堆会被 OOM kill（表现就是 80 端口 connection refused）
ENV PORT=80 \
    TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:MaxMetaspaceSize=256m -XX:+ExitOnOutOfMemoryError"

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
