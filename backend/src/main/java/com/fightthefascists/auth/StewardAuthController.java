package com.fightthefascists.auth;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class StewardAuthController {
    private final StewardAuthService auth;

    public StewardAuthController(StewardAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public Mono<ApiEnvelope<StewardAuthService.LoginResponse>> adminLogin(
            ServerWebExchange exchange,
            @RequestBody Map<String, String> body) {
        return auth.login(exchange, body.get("passphrase"), body.get("totpCode"))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/steward-login")
    public Mono<ApiEnvelope<StewardAuthService.LoginResponse>> stewardLogin(
            ServerWebExchange exchange,
            @RequestBody Map<String, String> body) {
        return auth.stewardLogin(exchange, body.get("passphrase"), body.get("totpCode"))
                .map(ApiEnvelope::of);
    }

    @PostMapping("/revoke-all-stewards")
    public Mono<ApiEnvelope<Map<String, String>>> revokeAll(ServerWebExchange exchange) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> auth.revokeAllStewards(ctx.deviceHash()))
                .thenReturn(ApiEnvelope.of(Map.of("status", "revoked")));
    }
}
