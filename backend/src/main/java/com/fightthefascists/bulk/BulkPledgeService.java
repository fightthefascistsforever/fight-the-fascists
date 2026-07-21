package com.fightthefascists.bulk;

import com.fightthefascists.auth.StewardAuthService;
import com.fightthefascists.common.AppException;
import com.fightthefascists.identity.DeviceRepository;
import com.fightthefascists.moderation.ContentFilter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class BulkPledgeService {
    private static final BigDecimal APPROVAL_THRESHOLD = BigDecimal.valueOf(100);

    private final DatabaseClient db;
    private final ContentFilter contentFilter;
    private final StewardAuthService auth;
    private final DeviceRepository deviceRepo;

    public BulkPledgeService(DatabaseClient db, ContentFilter contentFilter,
                             StewardAuthService auth, DeviceRepository deviceRepo) {
        this.db = db;
        this.contentFilter = contentFilter;
        this.auth = auth;
        this.deviceRepo = deviceRepo;
    }

    public Flux<BulkPledgeDto> list() {
        return db.sql("""
                SELECT id, org_name, contact_note, category::text, quantity, unit::text,
                       slot_hour, slot_label, approved_by IS NOT NULL as approved, active, food_safety_ack
                FROM bulk_pledges WHERE active = true ORDER BY slot_hour, category
                """)
                .map((row, meta) -> new BulkPledgeDto(
                        row.get("id", UUID.class),
                        row.get("org_name", String.class),
                        row.get("contact_note", String.class),
                        row.get("category", String.class),
                        row.get("quantity", BigDecimal.class),
                        row.get("unit", String.class),
                        row.get("slot_hour", Short.class),
                        row.get("slot_label", String.class),
                        row.get("approved", Boolean.class),
                        row.get("food_safety_ack", Boolean.class)))
                .all();
    }

    public Mono<BulkPledgeDto> create(ServerWebExchange exchange, CreateRequest req) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> {
                    contentFilter.filter(req.orgName());
                    if (req.contactNote() != null) contentFilter.filter(req.contactNote());

                    // F8.E2 — cooked food safety
                    if ("FOOD_COOKED".equals(req.category())) {
                        if (!req.foodSafetyAck()) {
                            return Mono.error(new AppException("FOOD_SAFETY_REQUIRED",
                                    "Cooked food pledges require food safety acknowledgement",
                                    "पके हुए भोजन के लिए सुरक्षा पुष्टि आवश्यक"));
                        }
                        if (req.prepWindowMinutes() != null && req.prepWindowMinutes() > 240) {
                            return Mono.error(new AppException("PREP_TIME_EXCEEDED",
                                    "Prep-to-delivery must be within 4 hours",
                                    "तैयारी से वितरण ४ घंटे के भीतर होना चाहिए"));
                        }
                    }

                    boolean needsApproval = req.quantity().compareTo(APPROVAL_THRESHOLD) >= 0
                            || "FOOD_COOKED".equals(req.category());

                    var sql = db.sql("""
                            INSERT INTO bulk_pledges (org_name, contact_note, category, quantity, unit,
                                slot_hour, slot_label, prep_window_minutes, food_safety_ack, approved_by)
                            VALUES (:org, :contact, :category::need_category, :qty, :unit::qty_unit,
                                :hour, :label, :prep, :ack, :approved)
                            RETURNING id
                            """)
                            .bind("org", req.orgName())
                            .bind("contact", req.contactNote())
                            .bind("category", req.category())
                            .bind("qty", req.quantity())
                            .bind("unit", req.unit())
                            .bind("hour", req.slotHour())
                            .bind("label", req.slotLabel())
                            .bind("prep", req.prepWindowMinutes())
                            .bind("ack", req.foodSafetyAck());
                    if (needsApproval) {
                        sql = sql.bindNull("approved", byte[].class);
                    } else {
                        sql = sql.bind("approved", hash);
                    }
                    return sql
                            .map((row, meta) -> row.get("id", UUID.class))
                            .one()
                            .flatMap(this::findById);
                });
    }

    public Mono<BulkPledgeDto> approve(ServerWebExchange exchange, UUID id) {
        // F8.E3 — steward approval before counting against demand
        return auth.requireSteward(exchange)
                .flatMap(ctx -> db.sql("""
                        UPDATE bulk_pledges SET approved_by = :hash WHERE id = :id AND approved_by IS NULL
                        RETURNING id
                        """)
                        .bind("hash", ctx.deviceHash()).bind("id", id)
                        .map((row, meta) -> row.get("id", UUID.class))
                        .one()
                        .switchIfEmpty(Mono.error(new AppException("NOT_FOUND", "Not found or already approved", "नहीं मिला")))
                        .flatMap(this::findById)
                        .flatMap(dto -> auth.audit(ctx.deviceHash(), "APPROVE_BULK", id).thenReturn(dto)));
    }

    public Mono<Void> confirmDelivery(UUID id) {
        return db.sql("UPDATE bulk_pledges SET last_confirmed_at = now(), missed_streak = 0 WHERE id = :id")
                .bind("id", id).fetch().rowsUpdated().then();
    }

    public Mono<BigDecimal> approvedSupplyFor(String category, int hour) {
        return db.sql("""
                SELECT COALESCE(SUM(quantity), 0) as total FROM bulk_pledges
                WHERE category = :cat::need_category AND slot_hour = :hour
                  AND active = true AND approved_by IS NOT NULL
                """)
                .bind("cat", category).bind("hour", hour)
                .map((row, meta) -> row.get("total", BigDecimal.class))
                .one();
    }

    private Mono<BulkPledgeDto> findById(UUID id) {
        return db.sql("""
                SELECT id, org_name, contact_note, category::text, quantity, unit::text,
                       slot_hour, slot_label, approved_by IS NOT NULL as approved, active, food_safety_ack
                FROM bulk_pledges WHERE id = :id
                """)
                .bind("id", id)
                .map((row, meta) -> new BulkPledgeDto(
                        row.get("id", UUID.class),
                        row.get("org_name", String.class),
                        row.get("contact_note", String.class),
                        row.get("category", String.class),
                        row.get("quantity", BigDecimal.class),
                        row.get("unit", String.class),
                        row.get("slot_hour", Short.class),
                        row.get("slot_label", String.class),
                        row.get("approved", Boolean.class),
                        row.get("food_safety_ack", Boolean.class)))
                .one();
    }

    public record CreateRequest(String orgName, String contactNote, String category,
                                BigDecimal quantity, String unit, short slotHour, String slotLabel,
                                Integer prepWindowMinutes, boolean foodSafetyAck) {}

    public record BulkPledgeDto(UUID id, String orgName, String contactNote, String category,
                                BigDecimal quantity, String unit, short slotHour, String slotLabel,
                                boolean approved, boolean foodSafetyAck) {}
}
