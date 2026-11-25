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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProfileService profileService;
    private final PvpService pvpService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder,
                          JwtService jwtService, ProfileService profileService,
                          PvpService pvpService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.profileService = profileService;
        this.pvpService = pvpService;
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
                .orElseThrow(() -> ApiException.unauthorized("账号不存在，请先注册"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("密码错误，请重试");
        }
        PlayerProfile profile = profileService.getOrCreate(user.getId(), user.getUsername());
        PvpProfile pvp = pvpService.getOrCreate(user.getId(), user.getUsername());
        String token = jwtService.issue(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), profile.getPower(), pvp.getRating());
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
