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
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public WxAuthService(@Value("${app.wx.appid:}") String appid,
                         @Value("${app.wx.secret:}") String secret,
                         ObjectMapper objectMapper) {
        this.appid = appid == null ? "" : appid.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return !appid.isEmpty() && !secret.isEmpty();
    }

    /** code → openid，失败抛 ApiException */
    public String openidFromCode(String code) {
        if (!configured()) {
            throw ApiException.badRequest("服务端未配置微信 AppID / AppSecret（WX_APPID / WX_SECRET）");
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
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
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
