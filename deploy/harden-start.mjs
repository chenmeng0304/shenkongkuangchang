/* 云托管启动更稳：JVM 按容器内存比例限制、缩短首次可服务时间、出错时把原因打到 stdout */
import fs from 'node:fs';

// 1) Dockerfile：JVM 内存按容器限制走（避免 -Xmx1024m 在小容器里被 OOM kill）
const df = 'D:/skkc/Dockerfile';
let d = fs.readFileSync(df, 'utf8');
if (!d.includes('MaxRAMPercentage')) {
  d = d.replace(
    'ENV PORT=80 \\\n    TZ=Asia/Shanghai \\\n    JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m"',
    'ENV PORT=80 \\\n    TZ=Asia/Shanghai \\\n    # 不用固定 -Xmx：容器内存小时固定堆会被 OOM kill（表现为 80 端口 connection refused）\n    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"',
  );
  fs.writeFileSync(df, d, 'utf8');
  console.log('✓ Dockerfile 已改为按容器内存限额');
} else {
  console.log('Dockerfile 已是内存比例模式');
}

// 2) application.yml：启动失败也要把原因留在 stdout
const yml = 'D:/skkc/src/main/resources/application.yml';
let y = fs.readFileSync(yml, 'utf8');
if (!y.includes('logging.level.org.flywaydb')) {
  y = y.replace(
    '\nspring:\n',
    '\n# 云托管排查用：把启动失败原因（Flyway / 连接池）完整打到 stdout\nlogging:\n  level:\n    org.flywaydb: INFO\n    com.zaxxer.hikari: INFO\n    org.springframework.boot.web.embedded.tomcat: INFO\n\nspring:\n',
  );
  fs.writeFileSync(yml, y, 'utf8');
  console.log('✓ application.yml 已加启动日志级别');
} else {
  console.log('application.yml 已有日志配置');
}

console.log('\n--- Dockerfile 尾部 ---');
console.log(d.split('\n').slice(-8).join('\n'));
