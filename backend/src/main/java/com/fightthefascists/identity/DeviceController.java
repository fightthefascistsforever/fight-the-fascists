package com.fightthefascists.identity;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceRepository repo;
    private final DeviceService deviceService;

    public DeviceController(DeviceRepository repo, DeviceService deviceService) {
        this.repo = repo;
        this.deviceService = deviceService;
    }

    @PostMapping("/register")
    public Mono<ApiEnvelope<Map<String, Object>>> register(@RequestHeader(value = "X-Device", required = false) String deviceSecret) {
        String secret = deviceSecret != null ? deviceSecret : deviceService.generateDeviceSecret();
        byte[] hash = deviceService.hashDeviceSecret(secret);
        String handle = deviceService.generateHandle();
        return repo.registerOrGet(hash, handle)
                .map(d -> ApiEnvelope.of(Map.of(
                        "handle", d.handle(),
                        "tier", d.tier(),
                        "deviceSecret", secret,
                        "serverNow", Instant.now())));
    }

    @DeleteMapping("/me")
    public Mono<ApiEnvelope<Map<String, String>>> forget(ServerWebExchange exchange) {
        return repo.resolveFromRequest(exchange)
                .flatMap(hash -> repo.deleteAllForDevice(hash).thenReturn(ApiEnvelope.of(Map.of("status", "forgotten"))));
    }
}
