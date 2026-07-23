package com.fightthefascists.needs;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/needs")
public class NeedController {
    private final NeedService service;
    private final ChapterService chapters;
    private final com.fightthefascists.identity.DeviceRepository deviceRepo;

    public NeedController(NeedService service, ChapterService chapters,
                          com.fightthefascists.identity.DeviceRepository deviceRepo) {
        this.service = service;
        this.chapters = chapters;
        this.deviceRepo = deviceRepo;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<NeedService.NeedDto>>> list(
            @PathVariable String chapterSlug,
            @RequestParam(required = false) Integer zone,
            @RequestParam(required = false) String category) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.listOpen(ch.id(), zone, category).collectList())
                .map(ApiEnvelope::of);
    }

    @PostMapping
    public Mono<ApiEnvelope<NeedService.NeedDto>> create(
            @PathVariable String chapterSlug,
            ServerWebExchange exchange,
            @RequestBody NeedService.CreateNeedRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-PoW") String pow) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.create(ch.id(), exchange, req, idempotencyKey, pow))
                .map(ApiEnvelope::of);
    }

    @GetMapping("/{id}")
    public Mono<ApiEnvelope<NeedService.NeedDto>> get(@PathVariable String chapterSlug, @PathVariable UUID id) {
        return chapters.requireActive(chapterSlug)
                .then(service.findById(id))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/flag")
    public Mono<ApiEnvelope<NeedService.NeedDto>> flagCovered(
            @PathVariable String chapterSlug,
            @PathVariable UUID id, ServerWebExchange exchange,
            @RequestBody Map<String, String> body) {
        return chapters.requireActive(chapterSlug)
                .then(deviceRepo.resolveFromRequest(exchange))
                .flatMap(hash -> service.flagCovered(id, hash))
                .map(ApiEnvelope::of);
    }
}
