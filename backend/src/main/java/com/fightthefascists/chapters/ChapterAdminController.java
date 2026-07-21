package com.fightthefascists.chapters;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/chapters")
public class ChapterAdminController {
    private final ChapterService chapters;
    private final StewardAuthService auth;

    public ChapterAdminController(ChapterService chapters, StewardAuthService auth) {
        this.chapters = chapters;
        this.auth = auth;
    }

    @PostMapping
    public Mono<ApiEnvelope<ChapterService.Chapter>> create(
            ServerWebExchange exchange,
            @RequestBody ChapterService.CreateRequest req) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> chapters.create(req))
                .map(ApiEnvelope::of);
    }

    @PatchMapping("/{slug}")
    public Mono<ApiEnvelope<ChapterService.Chapter>> update(
            @PathVariable String slug,
            ServerWebExchange exchange,
            @RequestBody ChapterService.UpdateRequest req) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> chapters.update(slug, req))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{slug}/activate")
    public Mono<ApiEnvelope<ChapterService.Chapter>> activate(
            @PathVariable String slug, ServerWebExchange exchange) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> chapters.activate(slug))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{slug}/archive")
    public Mono<ApiEnvelope<ChapterService.Chapter>> archive(
            @PathVariable String slug, ServerWebExchange exchange) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> chapters.archive(slug))
                .map(ApiEnvelope::of);
    }
}
