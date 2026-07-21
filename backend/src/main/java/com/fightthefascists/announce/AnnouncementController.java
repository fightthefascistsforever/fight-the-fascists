package com.fightthefascists.announce;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<AnnouncementService.AnnouncementDto>>> list() {
        return service.listPublished().collectList().map(ApiEnvelope::of);
    }

    @PostMapping
    public Mono<ApiEnvelope<AnnouncementService.AnnouncementDto>> create(
            ServerWebExchange exchange, @RequestBody AnnouncementService.CreateRequest req) {
        return service.create(exchange, req).map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/confirm")
    public Mono<ApiEnvelope<AnnouncementService.AnnouncementDto>> confirm(
            @PathVariable UUID id, ServerWebExchange exchange) {
        return service.confirm(exchange, id).map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/retract")
    public Mono<ApiEnvelope<AnnouncementService.AnnouncementDto>> retract(
            @PathVariable UUID id, @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        return service.retract(exchange, id, body.get("correctionBody")).map(ApiEnvelope::of);
    }
}
