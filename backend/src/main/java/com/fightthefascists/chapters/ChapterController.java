package com.fightthefascists.chapters;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chapters")
public class ChapterController {
    private final ChapterService chapters;

    public ChapterController(ChapterService chapters) {
        this.chapters = chapters;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<ChapterService.Chapter>>> list() {
        return chapters.listPublic().collectList().map(ApiEnvelope::of);
    }

    @GetMapping("/{slug}")
    public Mono<ApiEnvelope<ChapterService.Chapter>> get(@PathVariable String slug) {
        return chapters.findBySlug(slug)
                .switchIfEmpty(Mono.error(new com.fightthefascists.common.AppException(
                        "CHAPTER_NOT_FOUND", "Chapter not found", "अध्याय नहीं मिला")))
                .map(ApiEnvelope::of);
    }
}
