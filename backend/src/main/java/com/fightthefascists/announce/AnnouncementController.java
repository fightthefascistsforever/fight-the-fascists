package com.fightthefascists.announce;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/announcements")
public class AnnouncementController {
    private final AnnouncementService service;
    private final ChapterService chapters;

    public AnnouncementController(AnnouncementService service, ChapterService chapters) {
        this.service = service;
        this.chapters = chapters;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<AnnouncementService.AnnouncementDto>>> list(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.listPublished(ch.id()).collectList())
                .map(ApiEnvelope::of);
    }

    @PostMapping
    public Mono<ApiEnvelope<AnnouncementService.AnnouncementDto>> create(
            @PathVariable String chapterSlug,
            ServerWebExchange exchange,
            @RequestBody AnnouncementService.CreateRequest req) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.create(ch.id(), exchange, req))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/confirm")
    public Mono<ApiEnvelope<AnnouncementService.AnnouncementDto>> confirm(
            @PathVariable String chapterSlug,
            @PathVariable UUID id,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.confirm(ch.id(), exchange, id))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/retract")
    public Mono<ApiEnvelope<AnnouncementService.AnnouncementDto>> retract(
            @PathVariable String chapterSlug,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.retract(ch.id(), exchange, id, body.get("correctionBody")))
                .map(ApiEnvelope::of);
    }
}
