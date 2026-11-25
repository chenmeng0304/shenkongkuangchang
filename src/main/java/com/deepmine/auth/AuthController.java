package com.deepmine.auth;

import com.deepmine.common.ApiException;
import com.deepmine.profile.PlayerProfile;
import com.deepmine.profile.ProfileService;
import com.deepmine.pvp.PvpProfile;
import com.deepmine.pvp.PvpService;
import com.deepmine.user.User;
import com.deepmine.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProfileService profileService;
    private final PvpService pvpService;
    private final WxAuthService wxAuth;
    private final boolean trustOpenidHeader;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder,
                          JwtService jwtService, ProfileService profileService,
                          PvpService pvpService, WxAuthService wxAuth,
                          @Value("${app.wx.trust-openid-header:false}") boolean trustOpenidHeader) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.profileService = profileService;
        this.pvpService = pvpService;
        this.wxAuth = wxAuth;
        this.trustOpenidHeader = trustOpenidHeader;
    }

    /**
     * 微信登录：客户端 wx.login() 拿到 code，这里换 openid 并自动建号。
     * 云托管会在请求头注入 X-WX-OPENID；trust-openid-header=true（默认）时直接用它，
     * 不需要 AppSecret。自建公网服务器建议用 WX_TRUST_OPENID=false 关掉，只走 code 校验。
     */
    @PostMapping("/wx")
    public AuthResponse wxLogin(@RequestBody(required = false) WxLoginRequest request,
                                @RequestHeader(value = "X-WX-OPENID", required = false) String platformOpenid) {
        boolean headerSeen = platformOpenid != null && !platformOpenid.isBlank();
        String openid = trustOpenidHeader && headerSeen ? platformOpenid.trim() : "";
        if (openid.isEmpty()) {
            if (!wxAuth.configured()) {
                // 报错说清楚到底是「没收到头」还是「收到但不信任」，方便一眼定位
                throw ApiException.badRequest(headerSeen
                        ? "已收到云托管的 X-WX-OPENID，但服务端未开启信任：请在云托管环境变量里设置 WX_TRUST_OPENID=true"
                        : "服务端没收到云托管的 X-WX-OPENID 头，也没有配置 WX_APPID / WX_SECRET："
                          + "请确认客户端调用的是 wx.cloud.callContainer，或在服务设置里配置 WX_APPID / WX_SECRET");
            }
            String code = request == null ? null : request.code();
            openid = wxAuth.openidFromCode(code);
        }
        String finalOpenid = openid;
        User user = users.findByOpenid(finalOpenid).orElseGet(() -> createWxUser(finalOpenid));

        PlayerProfile profile = profileService.getOrCreate(user.getId(), user.getUsername());
        PvpProfile pvp = pvpService.getOrCreate(user.getId(), user.getUsername());
        String token = jwtService.issue(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), profile.getPower(), pvp.getRating());
    }

    /** 诊断用：看看云托管到底注入了哪些 X-WX-* 头（不含敏感值）；?deep=1 会额外做 DNS/TLS 检查 */
    @GetMapping("/wx/probe")
    public java.util.Map<String, Object> wxProbe(
            @RequestHeader java.util.Map<String, String> headers,
            @RequestParam(value = "deep", required = false) String deep) {
        java.util.List<String> wxKeys = new java.util.ArrayList<>();
        for (String key : headers.keySet()) {
            if (key != null && key.toLowerCase().startsWith("x-wx")) wxKeys.add(key.toLowerCase());
        }
        java.util.Collections.sort(wxKeys);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("xwxHeaders", wxKeys);
        out.put("trustHeader", trustOpenidHeader);
        out.put("appidConfigured", wxAuth.configured());
        if ("1".equals(deep)) out.putAll(wxAuth.diagnostics());
        return out;
    }

    /** 新建微信账号：昵称用「矿工 + 4 位数字」，撞名就重试 */
    private User createWxUser(String openid) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 30; attempt++) {
            String username = "矿工" + (1000 + random.nextInt(9000));
            if (users.existsByUsername(username)) continue;
            User user = new User();
            user.setOpenid(openid);
            user.setUsername(username);
            user.setCreatedAt(LocalDateTime.now());
            try {
                return users.save(user);
            } catch (Exception e) {
                // 唯一键冲突（并发注册同一 openid）→ 再查一次
                User existing = users.findByOpenid(openid).orElse(null);
                if (existing != null) return existing;
            }
        }
        throw new ApiException(500, "账号创建失败，请重试");
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("这个昵称已经被占用了");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(LocalDateTime.now());
        user = users.save(user);

        PlayerProfile profile = profileService.getOrCreate(user.getId(), username);
        PvpProfile pvp = pvpService.getOrCreate(user.getId(), username);
        String token = jwtService.issue(user.getId(), username);
        return new AuthResponse(token, user.getId(), username, profile.getPower(), pvp.getRating());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        User user = users.findByUsername(request.username().trim())
                .orElseThrow(() -> ApiException.unauthorized("账号不存在，请先用微信登录"));
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("密码错误，请重试");
        }
        PlayerProfile profile = profileService.getOrCreate(user.getId(), user.getUsername());
        PvpProfile pvp = pvpService.getOrCreate(user.getId(), user.getUsername());
        String token = jwtService.issue(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), profile.getPower(), pvp.getRating());
    }

    public record WxLoginRequest(String code) {
    }

    public record AuthRequest(
            @NotBlank(message = "请输入昵称")
            @Pattern(regexp = "^[\\w\\u4e00-\\u9fa5]{2,16}$", message = "昵称需为 2-16 位中文、字母、数字或下划线")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
            String password) {
    }

    public record AuthResponse(String token, Long userId, String username, long power, int rating) {
    }
}
