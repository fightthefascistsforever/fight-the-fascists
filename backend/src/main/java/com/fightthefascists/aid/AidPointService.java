package com.fightthefascists.aid;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.AppException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class AidPointService {
    private final DatabaseClient db;
    private final StewardAuthService auth;

    public AidPointService(DatabaseClient db, StewardAuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    public Flux<AidPointDto> list(short chapterId) {
        return db.sql("""
                SELECT a.id, a.zone_id, z.code as zone_code, a.name, a.status, a.status_at,
                       a.hours_note, a.cannot_handle
                FROM aid_points a JOIN zones z ON z.id = a.zone_id
                WHERE z.chapter_id = :chapterId
                ORDER BY a.id
                """)
                .bind("chapterId", chapterId)
                .map((row, meta) -> {
                    Instant statusAt = row.get("status_at", Instant.class);
                    String status = row.get("status", String.class);
                    if (statusAt.isBefore(Instant.now().minusSeconds(4 * 3600))
                            && !"UNKNOWN".equals(status)) {
                        status = "UNKNOWN";
                    }
                    return new AidPointDto(
                            row.get("id", Short.class),
                            row.get("zone_id", Short.class),
                            row.get("zone_code", String.class),
                            row.get("name", String.class),
                            status,
                            row.get("hours_note", String.class),
                            row.get("cannot_handle", String.class));
                })
                .all();
    }

    public Mono<AidPointDto> updateStatus(short chapterId, ServerWebExchange exchange, short id, String status) {
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("""
                        UPDATE aid_points SET status = :status, status_at = now()
                        WHERE id = :id AND zone_id IN (SELECT id FROM zones WHERE chapter_id = :chapterId)
                        RETURNING id, zone_id, name, status, hours_note, cannot_handle
                        """)
                        .bind("status", status)
                        .bind("id", id)
                        .bind("chapterId", chapterId)
                        .map((row, meta) -> new AidPointDto(
                                row.get("id", Short.class),
                                row.get("zone_id", Short.class),
                                null,
                                row.get("name", String.class),
                                row.get("status", String.class),
                                row.get("hours_note", String.class),
                                row.get("cannot_handle", String.class)))
                        .one()
                        .switchIfEmpty(Mono.error(new AppException("NOT_FOUND", "Aid point not found", "सहायता बिंदु नहीं मिला")))
                        .flatMap(dto -> auth.audit(ctx.deviceHash(), "UPDATE_AID_STATUS", null).thenReturn(dto)));
    }

    public record AidPointDto(short id, short zoneId, String zoneCode, String name,
                              String status, String hoursNote, String cannotHandle) {}
}
