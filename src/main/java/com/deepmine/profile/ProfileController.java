package com.deepmine.profile;

import com.deepmine.pvp.PvpProfile;
import com.deepmine.pvp.PvpRepository;
import com.deepmine.user.User;
import com.deepmine.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final UserRepository users;
    private final ProfileService profileService;
    private final PvpRepository pvpRepository;
    private final ObjectMapper objectMapper;

    public ProfileController(UserRepository users, ProfileService profileService,
                             PvpRepository pvpRepository, ObjectMapper objectMapper) {
        this.users = users;
        this.profileService = profileService;
        this.pvpRepository = pvpRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public MeDto me(@RequestAttribute("userId") Long userId) {
        User user = users.findById(userId).orElseThrow(() -> com.deepmine.common.ApiException.unauthorized("账号不存在"));
        PlayerProfile profile = profileService.getOrCreate(userId, user.getUsername());
        PvpProfile pvp = pvpRepository.findById(userId).orElse(null);
        return build(user, profile, pvp);
    }

    @PostMapping("/sync")
    public MeDto sync(@RequestAttribute("userId") Long userId,
                      @RequestBody(required = false) Snapshot snapshot) {
        User user = users.findById(userId).orElseThrow(() -> com.deepmine.common.ApiException.unauthorized("账号不存在"));
        PlayerProfile profile = profileService.sync(userId, user.getUsername(), snapshot);
        PvpProfile pvp = pvpRepository.findById(userId).orElse(null);
        return build(user, profile, pvp);
    }

    @GetMapping("/snapshot")
    public Snapshot snapshot(@RequestAttribute("userId") Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> com.deepmine.common.ApiException.unauthorized("账号不存在"));
        PlayerProfile profile = profileService.getOrCreate(userId, user.getUsername());
        if (profile.getSnapshotJson() == null || profile.getSnapshotJson().isBlank()) {
            return Snapshot.empty();
        }
        try {
            return objectMapper.readValue(profile.getSnapshotJson(), Snapshot.class);
        } catch (Exception e) {
            return Snapshot.empty();
        }
    }

    private MeDto build(User user, PlayerProfile profile, PvpProfile pvp) {
        return new MeDto(user.getId(), user.getUsername(), profile.getPower(), profile.getCores(),
                profile.getPrestiges(), profile.getFacilities(), profile.combat(),
                pvp == null ? 1000 : pvp.getRating(),
                pvp == null ? 0 : pvp.getWins(),
                pvp == null ? 0 : pvp.getLosses(),
                pvp == null ? 0 : pvp.getStreak(),
                user.getNickname(), user.getAvatarUrl(),
                user.getCreatedAt(), profile.getUpdatedAt());
    }

    public record MeDto(Long userId, String username, long power, long cores, int prestiges,
                        int facilities, CombatStats combat,
                        int rating, int wins, int losses, int streak,
                        String nickname, String avatarUrl,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}
