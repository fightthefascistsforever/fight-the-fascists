package com.fightthefascists.aid;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/aid-points")
public class AidPointController {
    private final AidPointService service;
    private final ChapterService chapters;

    public AidPointController(AidPointService service, ChapterService chapters) {
        this.service = service;
        this.chapters = chapters;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<AidPointService.AidPointDto>>> list(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.list(ch.id()).collectList())
                .map(ApiEnvelope::of);
    }

    @PatchMapping("/{id}")
    public Mono<ApiEnvelope<AidPointService.AidPointDto>> update(
            @PathVariable String chapterSlug,
            @PathVariable short id,
            @RequestBody Map<String, String> body,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.updateStatus(ch.id(), exchange, id, body.get("status")))
                .map(ApiEnvelope::of);
    }
}
