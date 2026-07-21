package com.fightthefascists.moderation;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/moderation")
public class ModerationController {
    private final ModerationService service;
    private final StewardAuthService auth;
    private final ChapterService chapters;

    public ModerationController(ModerationService service, StewardAuthService auth, ChapterService chapters) {
        this.service = service;
        this.auth = auth;
        this.chapters = chapters;
    }

    @GetMapping("/queue")
    public Mono<ApiEnvelope<List<ModerationService.QueueItem>>> queue(
            @PathVariable String chapterSlug,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> auth.requireSteward(exchange)
                        .thenMany(service.reviewQueue(ch.id()))
                        .collectList())
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{needId}/approve")
    public Mono<ApiEnvelope<Map<String, String>>> approve(
            @PathVariable String chapterSlug,
            @PathVariable UUID needId,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.approve(ch.id(), exchange, needId))
                .thenReturn(ApiEnvelope.of(Map.of("status", "approved")));
    }

    @PostMapping("/{needId}/remove")
    public Mono<ApiEnvelope<Map<String, String>>> remove(
            @PathVariable String chapterSlug,
            @PathVariable UUID needId,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.remove(ch.id(), exchange, needId))
                .thenReturn(ApiEnvelope.of(Map.of("status", "removed")));
    }
}
