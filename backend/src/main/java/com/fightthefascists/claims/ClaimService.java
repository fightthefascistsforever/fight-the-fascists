package com.fightthefascists.claims;

import com.fightthefascists.abuse.PowChallengeService;
import com.fightthefascists.abuse.RateLimiter;
import com.fightthefascists.common.AppException;
import com.fightthefascists.identity.DeviceRepository;
import com.fightthefascists.identity.DeviceService;
import com.fightthefascists.needs.NeedService;
import com.fightthefascists.realtime.SseHub;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ClaimService {
    private final DatabaseClient db;
    private final HandoffCodeService handoffCodes;
    private final RateLimiter rateLimiter;
    private final PowChallengeService powService;
    private final DeviceRepository deviceRepo;
    private final DeviceService deviceService;
    private final NeedService needService;
    private final SseHub sseHub;

    public ClaimService(DatabaseClient db, HandoffCodeService handoffCodes, RateLimiter rateLimiter,
                        PowChallengeService powService, DeviceRepository deviceRepo, DeviceService deviceService,
                        NeedService needService, SseHub sseHub) {
        this.db = db;
        this.handoffCodes = handoffCodes;
        this.rateLimiter = rateLimiter;
        this.powService = powService;
        this.deviceRepo = deviceRepo;
        this.deviceService = deviceService;
        this.needService = needService;
        this.sseHub = sseHub;
    }

    public Mono<ClaimDto> create(ServerWebExchange exchange, UUID needId, CreateClaimRequest req, String powHeader) {
        powService.verify(powHeader);
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> {
                    String hex = deviceService.hashToHex(hash);
                    return rateLimiter.check("device:" + hex + ":claims", 10, Duration.ofHours(1))
                            .then(checkActiveClaimCap(hash))
                            .then(doClaim(hash, needId, req));
                });
    }

    private Mono<Void> checkActiveClaimCap(byte[] hash) {
        // F2.E8 — max 3 active claims per device
        return db.sql("SELECT count(*) as c FROM claims WHERE device_hash = :hash AND state = 'ACTIVE'")
                .bind("hash", hash)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .flatMap(count -> {
                    if (count >= 3) {
                        return Mono.error(new AppException("CLAIM_CAP_EXCEEDED",
                                "You have 3 active claims — deliver or cancel one first",
                                "आपके पास ३ सक्रिय दावे हैं — पहले एक पूरा या रद्द करें"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<ClaimDto> doClaim(byte[] hash, UUID needId, CreateClaimRequest req) {
        Instant eta = Instant.now().plus(Duration.ofMinutes(req.etaMinutes()));
        Instant lapseAt = eta.plus(Duration.ofMinutes(60));
        String code = handoffCodes.generate();

        // F2.E5 — race-safe optimistic update
        return db.sql("""
                UPDATE needs
                SET pledged = pledged + :qty,
                    state = CASE WHEN pledged + :qty >= quantity THEN 'CLAIMED' ELSE state END,
                    version = version + 1
                WHERE id = :needId
                  AND state IN ('OPEN','CLAIMED')
                  AND hidden_pending_review = false
                  AND pledged + :qty <= quantity * 1.5
                """)
                .bind("qty", req.quantity())
                .bind("needId", needId)
                .fetch().rowsUpdated()
                .flatMap(updated -> {
                    if (updated == 0) {
                        return findAlternatives(needId)
                                .flatMap(alts -> Mono.error(new AppException("NEED_ALREADY_COVERED",
                                        "This need is already covered — try another",
                                        "यह ज़रूरत पहले से पूरी है — दूसरी आज़माएँ",
                                        java.util.Map.of("alternatives", alts))));
                    }
                    return insertClaim(hash, needId, req.quantity(), eta, lapseAt, code);
                });
    }

    private Mono<List<NeedService.NeedDto>> findAlternatives(UUID excludeId) {
        return needService.listOpen(null, null)
                .filter(n -> !n.id().equals(excludeId))
                .take(3)
                .collectList();
    }

    private Mono<ClaimDto> insertClaim(byte[] hash, UUID needId, BigDecimal qty, Instant eta, Instant lapseAt, String code) {
        return db.sql("""
                INSERT INTO claims (need_id, device_hash, quantity, eta, lapse_at, handoff_code)
                VALUES (:needId, :hash, :qty, :eta, :lapseAt, :code)
                RETURNING id, handoff_code
                """)
                .bind("needId", needId)
                .bind("hash", hash)
                .bind("qty", qty)
                .bind("eta", eta)
                .bind("lapseAt", lapseAt)
                .bind("code", code)
                .map((row, meta) -> new ClaimDto(
                        row.get("id", UUID.class),
                        needId,
                        qty,
                        eta,
                        lapseAt,
                        row.get("handoff_code", String.class),
                        "ACTIVE"))
                .one()
                .doOnNext(c -> sseHub.broadcast("claim.created", c));
    }

    public Mono<ClaimDto> deliver(String handoffCode, BigDecimal deliveredQty) {
        // F2.E3 — any on-site device can confirm delivery
        return db.sql("""
                UPDATE claims SET state = 'DELIVERED', delivered_qty = :qty, resolved_at = now()
                WHERE handoff_code = :code AND state = 'ACTIVE'
                RETURNING id, need_id, quantity, eta, lapse_at, handoff_code, state::text
                """)
                .bind("qty", deliveredQty)
                .bind("code", handoffCode.toUpperCase())
                .map((row, meta) -> new ClaimDto(
                        row.get("id", UUID.class),
                        row.get("need_id", UUID.class),
                        row.get("quantity", BigDecimal.class),
                        row.get("eta", Instant.class),
                        row.get("lapse_at", Instant.class),
                        row.get("handoff_code", String.class),
                        row.get("state", String.class)))
                .one()
                .switchIfEmpty(Mono.error(new AppException("INVALID_HANDOFF",
                        "Handoff code not found or already used",
                        "हैंडऑफ़ कोड नहीं मिला या पहले से उपयोग हो चुका")))
                .flatMap(claim -> db.sql("""
                        UPDATE needs SET delivered = delivered + :qty,
                            state = CASE WHEN delivered + :qty >= quantity THEN 'FULFILLED' ELSE state END,
                            resolved_at = CASE WHEN delivered + :qty >= quantity THEN now() ELSE resolved_at END
                        WHERE id = :needId
                        """)
                        .bind("qty", deliveredQty)
                        .bind("needId", claim.needId())
                        .fetch().rowsUpdated()
                        .thenReturn(claim))
                .doOnNext(c -> sseHub.broadcast("claim.delivered", c));
    }

    public Mono<Void> cancel(UUID claimId, byte[] hash) {
        return db.sql("""
                UPDATE claims SET state = 'CANCELLED', resolved_at = now()
                WHERE id = :id AND device_hash = :hash AND state = 'ACTIVE'
                RETURNING need_id, quantity
                """)
                .bind("id", claimId)
                .bind("hash", hash)
                .map((row, meta) -> new Object[]{row.get("need_id", UUID.class), row.get("quantity", BigDecimal.class)})
                .one()
                .flatMap(arr -> {
                    UUID needId = (UUID) arr[0];
                    BigDecimal qty = (BigDecimal) arr[1];
                    return db.sql("UPDATE needs SET pledged = GREATEST(0, pledged - :qty) WHERE id = :id")
                            .bind("qty", qty).bind("id", needId).fetch().rowsUpdated().then();
                });
    }

    public record CreateClaimRequest(BigDecimal quantity, int etaMinutes) {}

    public record ClaimDto(UUID id, UUID needId, BigDecimal quantity, Instant eta, Instant lapseAt,
                           String handoffCode, String state) {}
}
