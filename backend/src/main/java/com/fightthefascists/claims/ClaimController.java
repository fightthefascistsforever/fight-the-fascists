package com.fightthefascists.claims;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ClaimController {
    private final ClaimService service;
    private final com.fightthefascists.identity.DeviceRepository deviceRepo;

    public ClaimController(ClaimService service, com.fightthefascists.identity.DeviceRepository deviceRepo) {
        this.service = service;
        this.deviceRepo = deviceRepo;
    }

    @PostMapping("/needs/{needId}/claims")
    public Mono<ApiEnvelope<ClaimService.ClaimDto>> create(
            @PathVariable UUID needId,
            @RequestBody ClaimService.CreateClaimRequest req,
            @RequestHeader("X-PoW") String pow,
            ServerWebExchange exchange) {
        return service.create(exchange, needId, req, pow).map(ApiEnvelope::of);
    }

    @PostMapping("/claims/deliver")
    public Mono<ApiEnvelope<ClaimService.ClaimDto>> deliver(@RequestBody Map<String, Object> body) {
        var qty = new java.math.BigDecimal(body.get("deliveredQty").toString());
        return service.deliver(body.get("handoffCode").toString(), qty).map(ApiEnvelope::of);
    }

    @PostMapping("/claims/{id}/cancel")
    public Mono<ApiEnvelope<Map<String, String>>> cancel(@PathVariable UUID id, ServerWebExchange exchange) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> service.cancel(id, hash))
                .thenReturn(ApiEnvelope.of(Map.of("status", "cancelled")));
    }
}
