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
FROM eclipse-temurin:17-jre-alpine

# 国内镜像加速 + 时区 + HTTPS 证书
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.cloud.tencent.com/g' /etc/apk/repositories \
    && apk add --no-cache ca-certificates tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/cache/apk/*

# 把系统根证书导进 JVM 信任库。
# 不这么做，容器里用 JDK HttpClient 访问 api.weixin.qq.com 会报
#   PKIX path building failed: unable to find valid certification path
# （JDK 用的是自己的 cacerts，和系统 /etc/ssl/certs 不是同一份）
RUN set -e; \
    CA="$JAVA_HOME/lib/security/cacerts"; \
    if [ -f /etc/ssl/certs/ca-certificates.crt ]; then \
      mkdir -p /tmp/ca; \
      awk 'BEGIN{n=0} /BEGIN CERTIFICATE/{n++} {if(n>0) print > ("/tmp/ca/c" n ".pem")}' \
        /etc/ssl/certs/ca-certificates.crt; \
      for f in /tmp/ca/*.pem; do \
        keytool -importcert -noprompt -cacerts -storepass changeit -file "$f" >/dev/null 2>&1 || true; \
      done; \
      rm -rf /tmp/ca; \
    fi; \
    keytool -list -cacerts -storepass changeit 2>/dev/null | wc -l || true

WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar

# 云托管「服务设置 → 端口」要填 80，与这里保持一致
EXPOSE 80
ENV PORT=80 \
    TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m"

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
