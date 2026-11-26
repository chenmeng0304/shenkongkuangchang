package com.deepmine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeepMineApplication {

    private static final Logger log = LoggerFactory.getLogger(DeepMineApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DeepMineApplication.class, args);
    }

    /**
     * 启动后打印实际生效的端口和数据库地址。
     * 云托管里排查「Readiness/Liveness: connection refused」时，先看这一行：
     * 没有这行 = 应用根本没起来（多半是数据库连不上，见上面的 HikariPool 报错）。
     */
    @Bean
    ApplicationRunner startupBanner(Environment env) {
        return args -> {
            String port = env.getProperty("server.port", "?");
            String addr = env.getProperty("server.address", "?");
            String url = env.getProperty("spring.datasource.url", "?");
            log.info("[startup] 监听 {}:{}  数据库 {}  profile={}", addr, port, mask(url), env.getActiveProfiles());
            if (url.contains("127.0.0.1") || url.contains("localhost")) {
                log.warn("[startup] 数据库指向本机：云托管请确认已绑定 MySQL（会自动注入 MYSQL_ADDRESS / MYSQL_USERNAME / MYSQL_PASSWORD）");
            }
        };
    }

    private static String mask(String url) {
        if (url == null) return "?";
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
