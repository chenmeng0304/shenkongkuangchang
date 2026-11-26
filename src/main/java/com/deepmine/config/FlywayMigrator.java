package com.deepmine.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动时跑 Flyway，但**失败不拖垮应用**。
 *
 * 原因：云托管里如果数据库连不上（没绑定实例 / 安全组 / 冷启动），
 * Spring 自带的 Flyway 初始化会抛异常 → 整个应用退出 → 80 端口没人监听 →
 * 探针报 "connection refused"，控制台只看到探针失败，看不到真正原因。
 *
 * 这里改成：迁移失败只打 ERROR 日志，应用照常起来（探针能过、健康检查能访问），
 * 日志里直接能看到 [flyway] 的真实报错。
 */
@Component
public class FlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrator.class);

    private final DataSource dataSource;

    /** 测试环境（H2）关掉，避免 MySQL 语法的迁移脚本在 H2 上失败 */
    private final boolean enabled;

    public FlywayMigrator(DataSource dataSource,
                          @org.springframework.beans.factory.annotation.Value("${app.db.auto-migrate:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @PostConstruct
    public void migrate() {
        if (!enabled) {
            log.info("[flyway] 已关闭自动迁移（app.db.auto-migrate=false）");
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .baselineOnMigrate(true)
                    .locations("classpath:db/migration")
                    .load();
            var result = flyway.migrate();
            log.info("[flyway] 迁移完成，本次执行 {} 个脚本，用时 {} ms",
                    result.migrationsExecuted, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("[flyway] 迁移失败（应用继续启动，接口会报数据库错误）：{}", describe(e));
            log.error("[flyway] 请检查：1) 云托管是否绑定了 MySQL 实例  2) MYSQL_ADDRESS / MYSQL_USERNAME / MYSQL_PASSWORD  3) 数据库白名单/安全组");
        }
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && sb.length() < 400; c = c.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(c.getClass().getSimpleName());
            String msg = c.getMessage();
            if (msg != null && !msg.isBlank()) {
                sb.append(": ").append(msg.length() > 160 ? msg.substring(0, 160) : msg);
            }
            if (c.getCause() == c) break;
        }
        return sb.toString();
    }
}
