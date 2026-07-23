package com.fightthefascists.identity;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class DeviceRepository {
    private final DatabaseClient db;
    private final DeviceService deviceService;

    public DeviceRepository(DatabaseClient db, DeviceService deviceService) {
        this.db = db;
        this.deviceService = deviceService;
    }

    public Mono<DeviceRecord> registerOrGet(byte[] hash, String handle) {
        return db.sql("""
                INSERT INTO devices (device_hash, handle, pepper_version)
                VALUES (:hash, :handle, 1)
                ON CONFLICT (device_hash) DO UPDATE SET last_seen_at = now()
                RETURNING device_hash, handle, tier::text, reliability_score, deliveries_ok
                """)
                .bind("hash", hash)
                .bind("handle", handle)
                .map((row, meta) -> new DeviceRecord(
                        row.get("device_hash", byte[].class),
                        row.get("handle", String.class),
                        row.get("tier", String.class),
                        row.get("reliability_score", Integer.class),
                        row.get("deliveries_ok", Integer.class)))
                .one();
    }

    public Mono<DeviceRecord> findByHash(byte[] hash) {
        return db.sql("SELECT device_hash, handle, tier::text, reliability_score, deliveries_ok FROM devices WHERE device_hash = :hash AND revoked = false")
                .bind("hash", hash)
                .map((row, meta) -> new DeviceRecord(
                        row.get("device_hash", byte[].class),
                        row.get("handle", String.class),
                        row.get("tier", String.class),
                        row.get("reliability_score", Integer.class),
                        row.get("deliveries_ok", Integer.class)))
                .one();
    }

    public Mono<Void> deleteAllForDevice(byte[] hash) {
        return db.sql("DELETE FROM devices WHERE device_hash = :hash").bind("hash", hash).fetch().rowsUpdated().then();
    }

    public Mono<byte[]> resolveFromRequest(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("X-Device");
        if (header == null || header.isBlank()) {
            return Mono.error(new com.fightthefascists.common.AppException("UNAUTHORIZED", "Device identity required", "डिवाइस पहचान आवश्यक है"));
        }
        return Mono.fromCallable(() -> deviceService.hashDeviceSecret(header));
    }

    public record DeviceRecord(byte[] hash, String handle, String tier, int reliabilityScore, int deliveriesOk) {}

    public record RegisterResponse(String handle, String tier, Instant serverNow) {}
}
