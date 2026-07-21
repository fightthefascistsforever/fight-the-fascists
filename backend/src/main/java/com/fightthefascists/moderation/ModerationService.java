package com.fightthefascists.moderation;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.realtime.SseHub;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class ModerationService {
    private final DatabaseClient db;
    private final ContentFilter contentFilter;
    private final StewardAuthService auth;
    private final SseHub sseHub;

    public ModerationService(DatabaseClient db, ContentFilter contentFilter,
                             StewardAuthService auth, SseHub sseHub) {
        this.db = db;
        this.contentFilter = contentFilter;
        this.auth = auth;
        this.sseHub = sseHub;
    }

    public Flux<QueueItem> reviewQueue() {
        return db.sql("""
                SELECT n.id, n.zone_id, z.code as zone_code, n.category::text, n.created_at, n.note_enc
                FROM needs n JOIN zones z ON z.id = n.zone_id
                WHERE n.hidden_pending_review = true AND n.state IN ('OPEN','CLAIMED')
                ORDER BY n.created_at
                """)
                .map((row, meta) -> new QueueItem(
                        row.get("id", UUID.class),
                        "NEED",
                        row.get("zone_code", String.class),
                        row.get("category", String.class),
                        row.get("created_at", Instant.class),
                        contentFilter.decrypt(row.get("note_enc", byte[].class))))
                .all();
    }

    public Mono<Void> approve(ServerWebExchange exchange, UUID needId) {
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("""
                        UPDATE needs SET hidden_pending_review = false WHERE id = :id
                        """)
                        .bind("id", needId).fetch().rowsUpdated()
                        .then(auth.audit(ctx.deviceHash(), "APPROVE_NEED", needId)));
    }

    public Mono<Void> remove(ServerWebExchange exchange, UUID needId) {
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("""
                        UPDATE needs SET state = 'WITHDRAWN', resolved_at = now(), hidden_pending_review = false
                        WHERE id = :id
                        """)
                        .bind("id", needId).fetch().rowsUpdated()
                        .then(auth.audit(ctx.deviceHash(), "REMOVE_NEED", needId)));
    }

    public record QueueItem(UUID id, String targetType, String zoneCode, String category,
                            Instant createdAt, String notePreview) {}
}
