package com.fightthefascists.shifts;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/shifts")
public class ShiftController {
    private final ShiftService service;
    private final ChapterService chapters;

    public ShiftController(ShiftService service, ChapterService chapters) {
        this.service = service;
        this.chapters = chapters;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<ShiftService.ShiftDto>>> list(
            @PathVariable String chapterSlug,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now();
        Instant t = to != null ? Instant.parse(to) : f.plusSeconds(7 * 24 * 3600);
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> service.list(ch.id(), f, t).collectList())
                .map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/signup")
    public Mono<ApiEnvelope<ShiftService.SignupResult>> signup(
            @PathVariable String chapterSlug, @PathVariable UUID id, ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .then(service.signup(exchange, id))
                .map(ApiEnvelope::of);
    }

    @DeleteMapping("/{id}/signup")
    public Mono<ApiEnvelope<Map<String, String>>> cancel(
            @PathVariable String chapterSlug, @PathVariable UUID id, ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .then(service.cancelSignup(exchange, id))
                .thenReturn(ApiEnvelope.of(Map.of("status", "cancelled")));
    }

    @PutMapping("/{id}/handover")
    public Mono<ApiEnvelope<Map<String, String>>> handover(
            @PathVariable String chapterSlug, @PathVariable UUID id,
            @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        return chapters.requireActive(chapterSlug)
                .then(service.handover(exchange, id, body.get("note")))
                .thenReturn(ApiEnvelope.of(Map.of("status", "saved")));
    }
}
