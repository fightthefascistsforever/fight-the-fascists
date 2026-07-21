package com.fightthefascists.bulk;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bulk-pledges")
public class BulkPledgeController {
    private final BulkPledgeService service;

    public BulkPledgeController(BulkPledgeService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<BulkPledgeService.BulkPledgeDto>>> list() {
        return service.list().collectList().map(ApiEnvelope::of);
    }

    @PostMapping
    public Mono<ApiEnvelope<BulkPledgeService.BulkPledgeDto>> create(
            ServerWebExchange exchange, @RequestBody BulkPledgeService.CreateRequest req) {
        return service.create(exchange, req).map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/approve")
    public Mono<ApiEnvelope<BulkPledgeService.BulkPledgeDto>> approve(
            @PathVariable UUID id, ServerWebExchange exchange) {
        return service.approve(exchange, id).map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/confirm")
    public Mono<ApiEnvelope<Map<String, String>>> confirm(@PathVariable UUID id) {
        return service.confirmDelivery(id).thenReturn(ApiEnvelope.of(Map.of("status", "confirmed")));
    }
}
