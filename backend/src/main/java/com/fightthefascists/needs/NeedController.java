package com.fightthefascists.needs;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/needs")
public class NeedController {
    private final NeedService service;
    private final com.fightthefascists.identity.DeviceRepository deviceRepo;

    public NeedController(NeedService service, com.fightthefascists.identity.DeviceRepository deviceRepo) {
        this.service = service;
        this.deviceRepo = deviceRepo;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<NeedService.NeedDto>>> list(
            @RequestParam(required = false) Integer zone,
            @RequestParam(required = false) String category) {
        return service.listOpen(zone, category).collectList().map(ApiEnvelope::of);
    }

    @PostMapping
    public Mono<ApiEnvelope<NeedService.NeedDto>> create(
            ServerWebExchange exchange,
            @RequestBody NeedService.CreateNeedRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-PoW") String pow) {
        return service.create(exchange, req, idempotencyKey, pow).map(ApiEnvelope::of);
    }

    @GetMapping("/{id}")
    public Mono<ApiEnvelope<NeedService.NeedDto>> get(@PathVariable UUID id) {
        return service.findById(id).map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/flag")
    public Mono<ApiEnvelope<NeedService.NeedDto>> flagCovered(
            @PathVariable UUID id, ServerWebExchange exchange,
            @RequestBody Map<String, String> body) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> service.flagCovered(id, hash))
                .map(ApiEnvelope::of);
    }
}
