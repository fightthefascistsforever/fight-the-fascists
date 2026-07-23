package com.fightthefascists.shifts;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.AppException;
import com.fightthefascists.identity.DeviceRepository;
import com.fightthefascists.moderation.ContentFilter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class ShiftService {
    private static final String NIGHT_NOTICE = "Overnight roles are for adults only.";
    private final DatabaseClient db;
    private final DeviceRepository deviceRepo;
    private final ContentFilter contentFilter;
    private final StewardAuthService auth;

    public ShiftService(DatabaseClient db, DeviceRepository deviceRepo,
                        ContentFilter contentFilter, StewardAuthService auth) {
        this.db = db;
        this.deviceRepo = deviceRepo;
        this.contentFilter = contentFilter;
        this.auth = auth;
    }

    public Flux<ShiftDto> list(short chapterId, Instant from, Instant to) {
        return db.sql("""
                SELECT s.id, s.zone_id, z.code as zone_code, s.role, s.starts_at, s.ends_at,
                       s.min_volunteers, s.max_volunteers,
                       (SELECT count(*) FROM shift_signups ss WHERE ss.shift_id = s.id) as signed_up
                FROM shifts s JOIN zones z ON z.id = s.zone_id
                WHERE s.chapter_id = :chapterId
                  AND s.starts_at >= :from AND s.starts_at < :to
                ORDER BY
                  CASE WHEN (SELECT count(*) FROM shift_signups ss WHERE ss.shift_id = s.id) < s.min_volunteers
                       AND s.starts_at <= now() + interval '3 hours' THEN 0 ELSE 1 END,
                  s.starts_at
                """)
                .bind("chapterId", chapterId)
                .bind("from", from != null ? from : Instant.now())
                .bind("to", to != null ? to : Instant.now().plusSeconds(7 * 24 * 3600))
                .map((row, meta) -> new ShiftDto(
                        row.get("id", UUID.class),
                        row.get("zone_id", Short.class),
                        row.get("zone_code", String.class),
                        row.get("role", String.class),
                        row.get("starts_at", Instant.class),
                        row.get("ends_at", Instant.class),
                        row.get("min_volunteers", Short.class),
                        row.get("max_volunteers", Short.class),
                        row.get("signed_up", Long.class).intValue(),
                        "NIGHT_WATCH".equals(row.get("role", String.class)) ? NIGHT_NOTICE : null))
                .all();
    }

    public Mono<SignupResult> signup(ServerWebExchange exchange, UUID shiftId) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> checkSignupCap(hash)
                        .then(checkShiftCapacity(shiftId))
                        .then(doSignup(hash, shiftId)));
    }

    private Mono<Void> checkSignupCap(byte[] hash) {
        // F4.E5 — max 2 active future shift claims
        return db.sql("""
                SELECT count(*) as c FROM shift_signups ss
                JOIN shifts s ON s.id = ss.shift_id
                WHERE ss.device_hash = :hash AND s.ends_at > now()
                """)
                .bind("hash", hash)
                .map((row, meta) -> row.get("c", Long.class))
                .one()
                .flatMap(count -> count >= 2
                        ? Mono.error(new AppException("SHIFT_CAP", "Max 2 future shifts — complete one first",
                                "अधिकतम २ भविष्य की शिफ्ट"))
                        : Mono.empty());
    }

    private Mono<Void> checkShiftCapacity(UUID shiftId) {
        return db.sql("""
                SELECT s.max_volunteers, (SELECT count(*) FROM shift_signups ss WHERE ss.shift_id = s.id) as c
                FROM shifts s WHERE s.id = :id
                """)
                .bind("id", shiftId)
                .map((row, meta) -> new long[]{row.get("max_volunteers", Short.class), row.get("c", Long.class)})
                .one()
                .flatMap(arr -> arr[1] >= arr[0]
                        ? Mono.error(new AppException("SHIFT_FULL", "Shift is full", "शिफ्ट भरी है"))
                        : Mono.empty());
    }

    private Mono<SignupResult> doSignup(byte[] hash, UUID shiftId) {
        return db.sql("""
                SELECT role, starts_at FROM shifts WHERE id = :id
                """)
                .bind("id", shiftId)
                .map((row, meta) -> new Object[]{row.get("role", String.class), row.get("starts_at", Instant.class)})
                .one()
                .flatMap(info -> {
                    String role = (String) info[0];
                    boolean needsConfirm = "NIGHT_WATCH".equals(role);
                    return db.sql("""
                            INSERT INTO shift_signups (shift_id, device_hash, confirmed)
                            VALUES (:shiftId, :hash, :confirmed)
                            ON CONFLICT DO NOTHING
                            """)
                            .bind("shiftId", shiftId).bind("hash", hash)
                            .bind("confirmed", !needsConfirm)
                            .fetch().rowsUpdated()
                            .flatMap(rows -> rows == 0
                                    ? Mono.error(new AppException("ALREADY_SIGNED_UP", "Already signed up", "पहले से साइन अप"))
                                    : Mono.just(new SignupResult(needsConfirm, needsConfirm ? NIGHT_NOTICE : null)));
                });
    }

    public Mono<Void> cancelSignup(ServerWebExchange exchange, UUID shiftId) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> db.sql("DELETE FROM shift_signups WHERE shift_id = :id AND device_hash = :hash")
                        .bind("id", shiftId).bind("hash", hash).fetch().rowsUpdated().then());
    }

    public Mono<Void> confirmSignup(ServerWebExchange exchange, UUID shiftId, byte[] targetHash) {
        // F4.E4 — steward confirms night watch signups
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("UPDATE shift_signups SET confirmed = true WHERE shift_id = :id AND device_hash = :hash")
                        .bind("id", shiftId).bind("hash", targetHash)
                        .fetch().rowsUpdated()
                        .then(auth.audit(ctx.deviceHash(), "CONFIRM_SHIFT", shiftId)));
    }

    public Mono<Void> handover(ServerWebExchange exchange, UUID shiftId, String note) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> {
                    if (note != null && note.length() > 500) {
                        return Mono.error(new AppException("NOTE_TOO_LONG", "Handover note max 500 chars",
                                "हैंडओवर नोट अधिकतम ५०० अक्षर"));
                    }
                    byte[] enc = note != null ? contentFilter.encrypt(note) : null;
                    return db.sql("UPDATE shifts SET handover_note_enc = :note WHERE id = :id")
                            .bind("note", enc).bind("id", shiftId)
                            .fetch().rowsUpdated().then();
                });
    }

    public record ShiftDto(UUID id, short zoneId, String zoneCode, String role,
                           Instant startsAt, Instant endsAt, short minVolunteers, short maxVolunteers,
                           int signedUp, String notice) {}

    public record SignupResult(boolean pendingConfirmation, String notice) {}
}
