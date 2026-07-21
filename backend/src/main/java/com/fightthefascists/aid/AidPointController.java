package com.fightthefascists.aid;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/aid-points")
public class AidPointController {
    private final AidPointService service;

    public AidPointController(AidPointService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<AidPointService.AidPointDto>>> list() {
        return service.list().collectList().map(ApiEnvelope::of);
    }

    @PatchMapping("/{id}")
    public Mono<ApiEnvelope<AidPointService.AidPointDto>> update(
            @PathVariable short id, @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        return service.updateStatus(exchange, id, body.get("status")).map(ApiEnvelope::of);
    }
}
