package com.fightthefascists.ops;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatsService {
    private final DatabaseClient db;

    public StatsService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Map<String, Object>> transparencyStats() {
        return Mono.zip(
                aggregate("needs_posted", "SELECT count(*) FROM needs WHERE created_at > now() - interval '24 hours'"),
                aggregate("needs_fulfilled", "SELECT count(*) FROM needs WHERE state = 'FULFILLED' AND resolved_at > now() - interval '24 hours'"),
                aggregate("litres_delivered", """
                        SELECT COALESCE(SUM(delivered), 0) FROM needs
                        WHERE category = 'WATER' AND state = 'FULFILLED' AND resolved_at > now() - interval '24 hours'
                        """),
                aggregate("meals_delivered", """
                        SELECT COALESCE(SUM(delivered), 0) FROM needs
                        WHERE category IN ('FOOD_COOKED','FOOD_DRY') AND state = 'FULFILLED'
                          AND resolved_at > now() - interval '24 hours'
                        """),
                aggregate("claims_delivered", "SELECT count(*) FROM claims WHERE state = 'DELIVERED' AND resolved_at > now() - interval '24 hours'"),
                aggregate("volunteer_shifts", "SELECT count(*) FROM shift_signups WHERE created_at > now() - interval '24 hours'"),
                aggregate("active_devices", "SELECT count(*) FROM devices WHERE last_seen_at > now() - interval '24 hours'")
        ).map(tuple -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("period", "24h");
            stats.put("needsPosted", bucket(tuple.getT1()));
            stats.put("needsFulfilled", bucket(tuple.getT2()));
            stats.put("litresDelivered", bucket(tuple.getT3()));
            stats.put("mealsDelivered", bucket(tuple.getT4()));
            stats.put("claimsDelivered", bucket(tuple.getT5()));
            stats.put("volunteerShifts", bucket(tuple.getT6()));
            stats.put("activeDevices", bucket(tuple.getT7()));
            stats.put("note", "Aggregate counts only — no individual attribution");
            return stats;
        });
    }

    private Mono<Long> aggregate(String name, String sql) {
        return db.sql(sql).map((row, meta) -> row.get(0, Long.class)).one().defaultIfEmpty(0L);
    }

    // k-anonymity: bucket small counts to 0
    private long bucket(Long value) {
        if (value == null || value < 5) return 0;
        return value;
    }
}
