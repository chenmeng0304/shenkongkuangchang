package com.deepmine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.static-dir:}")
    private String staticDir;

    @Value("${app.cors-origins:http://localhost:*,http://127.0.0.1:*}")
    private String corsOrigins;

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/health", "/api/public/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsOrigins.split(",");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (staticDir == null || staticDir.isBlank()) {
            return;
        }
        Path dir = Paths.get(staticDir).toAbsolutePath().normalize();
        String location = dir.toUri().toString();
        CacheControl noCache = CacheControl.maxAge(0, TimeUnit.SECONDS).cachePrivate();

        registry.addResourceHandler(
                        "/index.html", "/manifest.webmanifest", "/sw.js", "/favicon.ico")
                .addResourceLocations(location)
                .setCacheControl(noCache);

        // 每个资源目录单独映射，避免把 .mysql / backend 等目录暴露出去
        registry.addResourceHandler("/css/**")
                .addResourceLocations(dir.resolve("css").toUri().toString())
                .setCacheControl(noCache);
        registry.addResourceHandler("/js/**")
                .addResourceLocations(dir.resolve("js").toUri().toString())
                .setCacheControl(noCache);
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(dir.resolve("assets").toUri().toString())
                .setCacheControl(noCache);
        registry.addResourceHandler("/docs/**")
                .addResourceLocations(dir.resolve("docs").toUri().toString())
                .setCacheControl(noCache);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
