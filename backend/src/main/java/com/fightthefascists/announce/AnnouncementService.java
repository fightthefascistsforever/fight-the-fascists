package com.fightthefascists.announce;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.AppException;
import com.fightthefascists.moderation.ContentFilter;
import com.fightthefascists.realtime.SseHub;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnnouncementService {
    private final DatabaseClient db;
    private final ContentFilter contentFilter;
    private final StewardAuthService auth;
    private final SseHub sseHub;

    public AnnouncementService(DatabaseClient db, ContentFilter contentFilter,
                               StewardAuthService auth, SseHub sseHub) {
        this.db = db;
        this.contentFilter = contentFilter;
        this.auth = auth;
        this.sseHub = sseHub;
    }

    public Flux<AnnouncementDto> listPublished(short chapterId) {
        return db.sql("""
                SELECT id, body_en, body_hi, source, urgent, published, created_at, correction_of
                FROM announcements
                WHERE chapter_id = :chapterId
                  AND published = true AND expires_at > now() AND retracted_by IS NULL
                ORDER BY urgent DESC, created_at DESC LIMIT 50
                """)
                .bind("chapterId", chapterId)
                .map(this::mapRow)
                .all();
    }

    public Mono<AnnouncementDto> create(short chapterId, ServerWebExchange exchange, CreateRequest req) {
        return auth.requireSteward(exchange)
                .flatMap(ctx -> {
                    contentFilter.filter(req.bodyEn());
                    if (req.bodyHi() != null) contentFilter.filter(req.bodyHi());
                    Instant now = Instant.now();
                    boolean published = !req.urgent();
                    short confirmations = req.urgent() ? (short) 1 : (short) 2;
                    return db.sql("""
                            INSERT INTO announcements (chapter_id, body_en, body_hi, source, urgent, confirmations,
                                                       published, author_hash, editable_until, expires_at, correction_of)
                            VALUES (:chapterId, :bodyEn, :bodyHi, :source, :urgent, :confirmations, :published,
                                    :author, :editableUntil, :expiresAt, :correctionOf)
                            RETURNING id
                            """)
                            .bind("chapterId", chapterId)
                            .bind("bodyEn", req.bodyEn())
                            .bind("bodyHi", req.bodyHi())
                            .bind("source", req.source())
                            .bind("urgent", req.urgent())
                            .bind("confirmations", confirmations)
                            .bind("published", published)
                            .bind("author", ctx.deviceHash())
                            .bind("editableUntil", now.plusSeconds(15 * 60))
                            .bind("expiresAt", now.plusSeconds(7 * 24 * 3600))
                            .bind("correctionOf", req.correctionOf())
                            .map((row, meta) -> row.get("id", UUID.class))
                            .one()
                            .flatMap(this::findById)
                            .doOnNext(a -> {
                                if (a.published()) sseHub.broadcast(chapterId, "announcement.published", a);
                            });
                });
    }

    public Mono<AnnouncementDto> confirm(short chapterId, ServerWebExchange exchange, UUID id) {
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("""
                        UPDATE announcements SET confirmations = confirmations + 1,
                            published = CASE WHEN confirmations + 1 >= 2 THEN true ELSE published END
                        WHERE id = :id AND chapter_id = :chapterId
                          AND urgent = true AND published = false
                        RETURNING id
                        """)
                        .bind("id", id)
                        .bind("chapterId", chapterId)
                        .map((row, meta) -> row.get("id", UUID.class))
                        .one()
                        .switchIfEmpty(Mono.error(new AppException("NOT_FOUND", "Announcement not found", "घोषणा नहीं मिली")))
                        .flatMap(aid -> auth.audit(ctx.deviceHash(), "CONFIRM_ANNOUNCEMENT", id).then(findById(aid)))
                        .doOnNext(a -> {
                            if (a.published()) sseHub.broadcast(chapterId, "announcement.published", a);
                        }));
    }

    public Mono<AnnouncementDto> retract(short chapterId, ServerWebExchange exchange, UUID id, String correctionBody) {
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("""
                        UPDATE announcements SET retracted_by = id
                        WHERE id = :id AND chapter_id = :chapterId AND retracted_by IS NULL
                        """)
                        .bind("id", id)
                        .bind("chapterId", chapterId)
                        .fetch().rowsUpdated()
                        .flatMap(updated -> {
                            if (updated == 0) return Mono.error(new AppException("NOT_FOUND", "Not found", "नहीं मिला"));
                            return create(chapterId, exchange, new CreateRequest(
                                    "CORRECTION: " + correctionBody, null,
                                    "OBSERVED_ON_SITE", false, id));
                        })
                        .then(auth.audit(ctx.deviceHash(), "RETRACT_ANNOUNCEMENT", id))
                        .then(findById(id)));
    }

    private Mono<AnnouncementDto> findById(UUID id) {
        return db.sql("""
                SELECT id, body_en, body_hi, source, urgent, published, created_at, correction_of
                FROM announcements WHERE id = :id
                """)
                .bind("id", id)
                .map(this::mapRow)
                .one();
    }

    private AnnouncementDto mapRow(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return new AnnouncementDto(
                row.get("id", UUID.class),
                row.get("body_en", String.class),
                row.get("body_hi", String.class),
                row.get("source", String.class),
                row.get("urgent", Boolean.class),
                row.get("published", Boolean.class),
                row.get("created_at", Instant.class),
                row.get("correction_of", UUID.class));
    }

    public record CreateRequest(String bodyEn, String bodyHi, String source, boolean urgent, UUID correctionOf) {
        public CreateRequest(String bodyEn, String bodyHi, String source, boolean urgent) {
            this(bodyEn, bodyHi, source, urgent, null);
        }
    }

    public record AnnouncementDto(UUID id, String bodyEn, String bodyHi, String source,
                                  boolean urgent, boolean published, Instant createdAt, UUID correctionOf) {}
}
