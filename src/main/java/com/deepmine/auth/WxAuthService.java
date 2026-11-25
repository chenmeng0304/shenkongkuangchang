package com.deepmine.auth;

import com.deepmine.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信登录：用小程序/小游戏 wx.login() 拿到的 code 换 openid。
 * 文档：https://developers.weixin.qq.com/miniprogram/dev/api-backend/open-api/login/auth.code2Session.html
 */
@Service
public class WxAuthService {

    private static final Logger log = LoggerFactory.getLogger(WxAuthService.class);
    private static final String CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";

    private final String appid;
    private final String secret;
    private final ObjectMapper objectMapper;
    private final boolean insecureTls;
    private final HttpClient http;
    private final HttpClient httpInsecure;

    public WxAuthService(@Value("${app.wx.appid:}") String appid,
                         @Value("${app.wx.secret:}") String secret,
                         @Value("${app.wx.insecure-tls:false}") boolean insecureTls,
                         ObjectMapper objectMapper) {
        this.appid = appid == null ? "" : appid.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.objectMapper = objectMapper;
        this.insecureTls = insecureTls;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.httpInsecure = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(trustAll())
                .build();
    }

    /** 兜底：容器里 CA 装不上时的逃生通道（WX_INSECURE_TLS=true 才会用到） */
    private static javax.net.ssl.SSLContext trustAll() {
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }}, null);
            return ctx;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean configured() {
        return !appid.isEmpty() && !secret.isEmpty();
    }

    /** code → openid，失败抛 ApiException */
    public String openidFromCode(String code) {
        if (!configured()) {
            throw ApiException.badRequest(
                    "服务端既没收到云托管的 X-WX-OPENID 头，也没配置 WX_APPID / WX_SECRET。"
                            + "请在云托管「服务设置 → 环境变量」里加 WX_TRUST_OPENID=true，并确认客户端走的是 wx.cloud.callContainer");
        }
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("缺少登录凭证 code");
        }
        String url = CODE2SESSION
                + "?appid=" + enc(appid)
                + "&secret=" + enc(secret)
                + "&js_code=" + enc(code.trim())
                + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (javax.net.ssl.SSLException ssl) {
                // 证书链问题：容器的 JDK 信任库没带根证书时会出现
                //   PKIX path building failed: unable to find valid certification path
                log.warn("[wx] TLS 校验失败（容器缺根证书？）: {}", ssl.toString());
                if (!insecureTls) {
                    throw new ApiException(502,
                            "服务端无法验证微信服务器证书：请更新后端镜像（已自动导入系统根证书），"
                                    + "或临时设置环境变量 WX_INSECURE_TLS=true");
                }
                log.warn("[wx] 已按 WX_INSECURE_TLS=true 跳过证书校验");
                response = httpInsecure.send(request, HttpResponse.BodyHandlers.ofString());
            }
            JsonNode node = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            String openid = node.path("openid").asText("");
            if (!openid.isEmpty()) {
                return openid;
            }
            int errcode = node.path("errcode").asInt(-1);
            String errmsg = node.path("errmsg").asText("");
            log.warn("[wx] code2session 失败: errcode={} errmsg={}", errcode, errmsg);
            if (errcode == 40029 || errcode == 40163) {
                throw ApiException.unauthorized("登录凭证已失效，请重试");
            }
            throw ApiException.unauthorized("微信登录失败（" + errcode + "）");
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "微信登录被中断");
        } catch (Exception e) {
            log.warn("[wx] code2session 异常: {}", e.toString());
            throw new ApiException(502, "微信服务器暂时不可用，请稍后重试");
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
