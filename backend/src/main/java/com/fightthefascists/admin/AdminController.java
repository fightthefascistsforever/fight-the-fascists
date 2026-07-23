package com.fightthefascists.admin;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.ApiEnvelope;
import com.fightthefascists.identity.DeviceService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final StewardAuthService auth;
    private final DeviceService deviceService;

    public AdminController(StewardAuthService auth, DeviceService deviceService) {
        this.auth = auth;
        this.deviceService = deviceService;
    }

    @PostMapping("/grant-steward")
    public Mono<ApiEnvelope<Map<String, String>>> grantSteward(
            ServerWebExchange exchange,
            @RequestBody Map<String, Object> body) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> {
                    byte[] target = HexFormat.of().parseHex((String) body.get("targetDeviceHex"));
                    short zoneId = ((Number) body.get("zoneId")).shortValue();
                    return auth.grantSteward(ctx.deviceHash(), target, zoneId,
                            (String) body.get("passphrase"), (String) body.get("totpSecret"));
                })
                .thenReturn(ApiEnvelope.of(Map.of("status", "granted")));
    }

    @PostMapping("/revoke-steward")
    public Mono<ApiEnvelope<Map<String, String>>> revokeSteward(
            ServerWebExchange exchange,
            @RequestBody Map<String, String> body) {
        return auth.requireAdmin(exchange)
                .flatMap(ctx -> auth.revokeSteward(ctx.deviceHash(),
                        HexFormat.of().parseHex(body.get("targetDeviceHex"))))
                .thenReturn(ApiEnvelope.of(Map.of("status", "revoked")));
    }
}
