package com.fightthefascists.shifts;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftController {
    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<ShiftService.ShiftDto>>> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now();
        Instant t = to != null ? Instant.parse(to) : f.plusSeconds(7 * 24 * 3600);
        return service.list(f, t).collectList().map(ApiEnvelope::of);
    }

    @PostMapping("/{id}/signup")
    public Mono<ApiEnvelope<ShiftService.SignupResult>> signup(
            @PathVariable UUID id, ServerWebExchange exchange) {
        return service.signup(exchange, id).map(ApiEnvelope::of);
    }

    @DeleteMapping("/{id}/signup")
    public Mono<ApiEnvelope<Map<String, String>>> cancel(
            @PathVariable UUID id, ServerWebExchange exchange) {
        return service.cancelSignup(exchange, id).thenReturn(ApiEnvelope.of(Map.of("status", "cancelled")));
    }

    @PutMapping("/{id}/handover")
    public Mono<ApiEnvelope<Map<String, String>>> handover(
            @PathVariable UUID id, @RequestBody Map<String, String> body, ServerWebExchange exchange) {
        return service.handover(exchange, id, body.get("note")).thenReturn(ApiEnvelope.of(Map.of("status", "saved")));
    }
}
