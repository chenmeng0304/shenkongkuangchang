package com.deepmine.pvp;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pvp")
public class PvpController {
    private final PvpService pvpService;

    public PvpController(PvpService pvpService) {
        this.pvpService = pvpService;
    }

    @GetMapping("/opponent")
    public PvpService.OpponentDto opponent(@RequestAttribute("userId") Long userId) {
        return pvpService.opponent(userId);
    }

    @PostMapping("/battle")
    public PvpService.BattleResult battle(@RequestAttribute("userId") Long userId,
                                          @RequestBody(required = false) PvpService.BattleRequest request) {
        return pvpService.battle(userId, request);
    }

    @GetMapping("/players")
    public PvpService.PlayersPage players(@RequestAttribute("userId") Long userId) {
        return pvpService.players(userId);
    }

    /** 查看某个玩家的装备（竞技场点头像） */
    @GetMapping("/players/{id}")
    public PvpService.PlayerGearDto playerGear(@PathVariable Long id) {
        return pvpService.gearOf(id);
    }

    @GetMapping("/history")
    public List<PvpService.HistoryItem> history(@RequestAttribute("userId") Long userId) {
        return pvpService.history(userId);
    }
}
