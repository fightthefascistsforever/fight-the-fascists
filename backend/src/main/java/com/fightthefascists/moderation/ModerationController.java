package com.fightthefascists.moderation;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {
    private final ModerationService service;
    private final StewardAuthService auth;

    public ModerationController(ModerationService service, StewardAuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @GetMapping("/queue")
    public Mono<ApiEnvelope<List<ModerationService.QueueItem>>> queue(ServerWebExchange exchange) {
        return auth.requireSteward(exchange)
                .thenMany(service.reviewQueue())
                .collectList()
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{needId}/approve")
    public Mono<ApiEnvelope<Map<String, String>>> approve(
            @PathVariable UUID needId, ServerWebExchange exchange) {
        return service.approve(exchange, needId).thenReturn(ApiEnvelope.of(Map.of("status", "approved")));
    }

    @PostMapping("/{needId}/remove")
    public Mono<ApiEnvelope<Map<String, String>>> remove(
            @PathVariable UUID needId, ServerWebExchange exchange) {
        return service.remove(exchange, needId).thenReturn(ApiEnvelope.of(Map.of("status", "removed")));
    }
}
