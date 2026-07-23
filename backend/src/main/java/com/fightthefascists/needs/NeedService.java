package com.fightthefascists.needs;

import com.fightthefascists.abuse.IdempotencyService;
import com.fightthefascists.abuse.PowChallengeService;
import com.fightthefascists.abuse.RateLimiter;
import com.fightthefascists.common.ApiEnvelope;
import com.fightthefascists.common.AppException;
import com.fightthefascists.identity.DeviceRepository;
import com.fightthefascists.identity.DeviceService;
import com.fightthefascists.moderation.ContentFilter;
import com.fightthefascists.realtime.SseHub;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class NeedService {
    private static final Map<String, BigDecimal> MAX_QUANTITY = Map.of(
            "WATER", BigDecimal.valueOf(2000),
            "FOOD_COOKED", BigDecimal.valueOf(5000),
            "FOOD_DRY", BigDecimal.valueOf(5000)
    );
    private static final BigDecimal DEFAULT_MAX = BigDecimal.valueOf(10000);

    private final DatabaseClient db;
    private final ContentFilter contentFilter;
    private final RateLimiter rateLimiter;
    private final PowChallengeService powService;
    private final IdempotencyService idempotencyService;
    private final DeviceRepository deviceRepo;
    private final DeviceService deviceService;
    private final SseHub sseHub;

    public NeedService(DatabaseClient db, ContentFilter contentFilter, RateLimiter rateLimiter,
                       PowChallengeService powService, IdempotencyService idempotencyService,
                       DeviceRepository deviceRepo, DeviceService deviceService, SseHub sseHub) {
        this.db = db;
        this.contentFilter = contentFilter;
        this.rateLimiter = rateLimiter;
        this.powService = powService;
        this.idempotencyService = idempotencyService;
        this.deviceRepo = deviceRepo;
        this.deviceService = deviceService;
        this.sseHub = sseHub;
    }

    public Flux<NeedDto> listOpen(short chapterId, Integer zoneId, String category) {
        var sql = new StringBuilder("""
                SELECT n.id, n.zone_id, z.code as zone_code, n.category::text, n.quantity, n.unit::text,
                       n.pledged, n.delivered, n.urgency::text, n.state::text, n.needed_by, n.expires_at,
                       n.covered_flags, n.version, n.note_enc
                FROM needs n JOIN zones z ON z.id = n.zone_id
                WHERE n.chapter_id = :chapterId
                  AND n.state IN ('OPEN','CLAIMED') AND n.hidden_pending_review = false
                """);
        if (zoneId != null) sql.append(" AND n.zone_id = :zoneId");
        if (category != null) sql.append(" AND n.category = :category::need_category");
        sql.append(" ORDER BY CASE n.urgency WHEN 'URGENT' THEN 0 WHEN 'SOON' THEN 1 ELSE 2 END, n.needed_by LIMIT 100");

        var spec = db.sql(sql.toString()).bind("chapterId", chapterId);
        if (zoneId != null) spec = spec.bind("zoneId", zoneId);
        if (category != null) spec = spec.bind("category", category);
        return spec.map(this::mapNeed).all();
    }

    public Mono<NeedDto> create(short chapterId, ServerWebExchange exchange, CreateNeedRequest req, String idempotencyKey, String powHeader) {
        powService.verify(powHeader);
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> {
                    String hex = deviceService.hashToHex(hash);
                    return idempotencyService.begin(hex, idempotencyKey)
                            .then(rateLimiter.check("device:" + hex + ":needs", 5, Duration.ofHours(1)))
                            .then(rateLimiter.check("zone:" + req.zoneId() + ":needs", 20, Duration.ofHours(1)))
                            .then(validateQuantity(req))
                            .then(checkDuplicateClient(req))
                            .then(verifyZoneInChapter(chapterId, req.zoneId()))
                            .then(doCreate(chapterId, hash, req));
                });
    }

    private Mono<Void> validateQuantity(CreateNeedRequest req) {
        // F1.E4
        if (req.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new AppException("QUANTITY_OUT_OF_RANGE", "Quantity must be positive", "मात्रा सकारात्मक होनी चाहिए"));
        }
        BigDecimal max = MAX_QUANTITY.getOrDefault(req.category(), DEFAULT_MAX);
        if (req.quantity().compareTo(max) > 0) {
            return Mono.error(new AppException("QUANTITY_OUT_OF_RANGE", "Quantity exceeds maximum allowed", "मात्रा अधिकतम सीमा से अधिक है"));
        }
        return Mono.empty();
    }

    private Mono<Void> checkDuplicateClient(CreateNeedRequest req) {
        // F1.E1 — client-side dedup hint is done on frontend; server enforces F1.E2 via trigger
        return Mono.empty();
    }

    private Mono<Void> verifyZoneInChapter(short chapterId, short zoneId) {
        return db.sql("SELECT 1 FROM zones WHERE id = :zoneId AND chapter_id = :chapterId")
                .bind("zoneId", zoneId).bind("chapterId", chapterId)
                .map((row, meta) -> 1).one()
                .switchIfEmpty(Mono.error(new AppException("ZONE_NOT_IN_CHAPTER",
                        "Zone does not belong to this chapter", "ज़ोन इस अध्याय से संबंधित नहीं है")))
                .then();
    }

    private Mono<NeedDto> doCreate(short chapterId, byte[] hash, CreateNeedRequest req) {
        var filterResult = contentFilter.filter(req.note());
        byte[] noteEnc = filterResult.text() != null ? contentFilter.encrypt(filterResult.text()) : null;
        Instant now = Instant.now();
        Duration ttl = switch (req.urgency()) {
            case "URGENT" -> Duration.ofHours(2);
            case "SOON" -> Duration.ofHours(6);
            default -> Duration.ofHours(12);
        };
        Instant neededBy = now.plus(req.neededByMinutes() != null ? Duration.ofMinutes(req.neededByMinutes()) : ttl);
        Instant expiresAt = now.plus(ttl);

        return db.sql("""
                INSERT INTO needs (chapter_id, zone_id, category, quantity, unit, urgency, note_enc, created_by, needed_by, expires_at, hidden_pending_review)
                VALUES (:chapterId, :zoneId, :category::need_category, :quantity, :unit::qty_unit, :urgency::urgency_level,
                        :noteEnc, :createdBy, :neededBy, :expiresAt, :hidden)
                RETURNING id
                """)
                .bind("chapterId", chapterId)
                .bind("zoneId", req.zoneId())
                .bind("category", req.category())
                .bind("quantity", req.quantity())
                .bind("unit", req.unit())
                .bind("urgency", req.urgency())
                .bind("noteEnc", noteEnc)
                .bind("createdBy", hash)
                .bind("neededBy", neededBy)
                .bind("expiresAt", expiresAt)
                .bind("hidden", filterResult.pendingReview())
                .map((row, meta) -> row.get("id", UUID.class))
                .one()
                .onErrorMap(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("DUPLICATE_NEED_CLUSTER")) {
                        return new AppException("DUPLICATE_NEED_CLUSTER",
                                "Zone already has 3 open needs in this category — add to existing instead",
                                "इस श्रेणी में पहले से ३ खुली ज़रूरतें हैं — मौजूदा में जोड़ें");
                    }
                    return e;
                })
                .flatMap(id -> findById(id))
                .doOnNext(n -> sseHub.broadcast(chapterId, "need.created", n));
    }

    public Mono<NeedDto> findById(UUID id) {
        return db.sql("""
                SELECT n.id, n.zone_id, z.code as zone_code, n.category::text, n.quantity, n.unit::text,
                       n.pledged, n.delivered, n.urgency::text, n.state::text, n.needed_by, n.expires_at,
                       n.covered_flags, n.version, n.note_enc
                FROM needs n JOIN zones z ON z.id = n.zone_id WHERE n.id = :id
                """)
                .bind("id", id)
                .map(this::mapNeed)
                .one();
    }

    public Mono<NeedDto> flagCovered(UUID needId, byte[] deviceHash) {
        return db.sql("SELECT chapter_id FROM needs WHERE id = :id")
                .bind("id", needId)
                .map((row, meta) -> row.get("chapter_id", Short.class))
                .one()
                .flatMap(chapterId -> db.sql("""
                        INSERT INTO flags (target_type, target_id, device_hash, reason, weight)
                        VALUES ('NEED', :needId, :hash, 'ALREADY_COVERED', 1.0)
                        ON CONFLICT (target_type, target_id, device_hash) DO NOTHING
                        """)
                        .bind("needId", needId)
                        .bind("hash", deviceHash)
                        .fetch().rowsUpdated()
                        .then(db.sql("UPDATE needs SET covered_flags = covered_flags + 1 WHERE id = :id RETURNING covered_flags")
                                .bind("id", needId)
                                .map((row, meta) -> row.get("covered_flags", Short.class))
                                .one())
                        .flatMap(flags -> {
                            if (flags >= 3) {
                                return db.sql("""
                                        UPDATE needs SET state = 'FULFILLED', resolved_at = now(), resolution_reason = 'COMMUNITY_RESOLVED'
                                        WHERE id = :id RETURNING id
                                        """)
                                        .bind("id", needId)
                                        .fetch().rowsUpdated()
                                        .then(findById(needId))
                                        .doOnNext(n -> sseHub.broadcast(chapterId, "need.resolved", n));
                            }
                            return findById(needId);
                        }));
    }

    private NeedDto mapNeed(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        byte[] noteEnc = row.get("note_enc", byte[].class);
        return new NeedDto(
                row.get("id", UUID.class),
                row.get("zone_id", Short.class),
                row.get("zone_code", String.class),
                row.get("category", String.class),
                row.get("quantity", BigDecimal.class),
                row.get("unit", String.class),
                row.get("pledged", BigDecimal.class),
                row.get("delivered", BigDecimal.class),
                row.get("urgency", String.class),
                row.get("state", String.class),
                row.get("needed_by", Instant.class),
                row.get("expires_at", Instant.class),
                row.get("covered_flags", Short.class),
                row.get("version", Integer.class),
                contentFilter.decrypt(noteEnc));
    }

    public record CreateNeedRequest(short zoneId, String category, BigDecimal quantity, String unit,
                                    String urgency, String note, Integer neededByMinutes) {}

    public record NeedDto(UUID id, short zoneId, String zoneCode, String category, BigDecimal quantity,
                          String unit, BigDecimal pledged, BigDecimal delivered, String urgency, String state,
                          Instant neededBy, Instant expiresAt, short coveredFlags, int version, String note) {}
}
