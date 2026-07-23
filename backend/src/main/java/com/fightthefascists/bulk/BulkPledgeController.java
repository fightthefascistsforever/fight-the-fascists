package com.fightthefascists.bulk;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/bulk-pledges")
public class BulkPledgeController {
    private final BulkPledgeService service;
    private final ChapterService chapters;

    public BulkPledgeController(BulkPledgeService service, ChapterService chapters) {
        this.service = service;
        this.chapters = chapters;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<BulkPledgeService.BulkPledgeDto>>> list(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.list(ch.id()).collectList())
                .map(ApiEnvelope::of);
    }

    @PostMapping
    public Mono<ApiEnvelope<BulkPledgeService.BulkPledgeDto>> create(
            @PathVariable String chapterSlug,
            ServerWebExchange exchange,
            @RequestBody BulkPledgeService.CreateRequest req) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.create(ch.id(), exchange, req))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/approve")
    public Mono<ApiEnvelope<BulkPledgeService.BulkPledgeDto>> approve(
            @PathVariable String chapterSlug,
            @PathVariable UUID id,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.approve(ch.id(), exchange, id))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/confirm")
    public Mono<ApiEnvelope<Map<String, String>>> confirm(
            @PathVariable String chapterSlug,
            @PathVariable UUID id) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.confirmDelivery(ch.id(), id))
                .thenReturn(ApiEnvelope.of(Map.of("status", "confirmed")));
    }
}
