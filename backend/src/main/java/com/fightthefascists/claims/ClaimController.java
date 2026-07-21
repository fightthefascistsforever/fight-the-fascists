package com.fightthefascists.claims;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}")
public class ClaimController {
    private final ClaimService service;
    private final ChapterService chapters;
    private final com.fightthefascists.identity.DeviceRepository deviceRepo;

    public ClaimController(ClaimService service, ChapterService chapters,
                           com.fightthefascists.identity.DeviceRepository deviceRepo) {
        this.service = service;
        this.chapters = chapters;
        this.deviceRepo = deviceRepo;
    }

    @PostMapping("/needs/{needId}/claims")
    public Mono<ApiEnvelope<ClaimService.ClaimDto>> create(
            @PathVariable String chapterSlug,
            @PathVariable UUID needId,
            @RequestBody ClaimService.CreateClaimRequest req,
            @RequestHeader("X-PoW") String pow,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.create(ch.id(), exchange, needId, req, pow))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/claims/deliver")
    public Mono<ApiEnvelope<ClaimService.ClaimDto>> deliver(
            @PathVariable String chapterSlug,
            @RequestBody Map<String, Object> body) {
        var qty = new java.math.BigDecimal(body.get("deliveredQty").toString());
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.deliver(ch.id(), body.get("handoffCode").toString(), qty))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/claims/{id}/cancel")
    public Mono<ApiEnvelope<Map<String, String>>> cancel(
            @PathVariable String chapterSlug,
            @PathVariable UUID id, ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> deviceRepo.resolveFromRequest(exchange)
                        .flatMap(hash -> service.cancel(ch.id(), id, hash)))
                .thenReturn(ApiEnvelope.of(Map.of("status", "cancelled")));
    }
}
