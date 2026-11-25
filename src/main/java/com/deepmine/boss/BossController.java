package com.deepmine.boss;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/boss")
public class BossController {
    private final BossService bossService;

    public BossController(BossService bossService) {
        this.bossService = bossService;
    }

    @GetMapping
    public BossService.BossView current(@RequestAttribute("userId") Long userId) {
        return bossService.current(userId);
    }

    @PostMapping("/attack")
    public BossService.AttackResult attack(@RequestAttribute("userId") Long userId,
                                           @RequestBody(required = false) BossService.AttackRequest request) {
        return bossService.attack(userId, request);
    }

    @PostMapping("/claim")
    public BossService.ClaimResult claim(@RequestAttribute("userId") Long userId) {
        return bossService.claim(userId);
    }
}
