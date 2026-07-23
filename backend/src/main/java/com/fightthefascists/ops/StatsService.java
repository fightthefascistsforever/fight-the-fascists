package com.fightthefascists.ops;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatsService {
    private final DatabaseClient db;

    public StatsService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Map<String, Object>> transparencyStats(short chapterId) {
        return Mono.zip(
                aggregate("SELECT count(*) FROM needs WHERE chapter_id = :chapterId AND created_at > now() - interval '24 hours'", chapterId),
                aggregate("SELECT count(*) FROM needs WHERE chapter_id = :chapterId AND state = 'FULFILLED' AND resolved_at > now() - interval '24 hours'", chapterId),
                aggregate("""
                        SELECT COALESCE(SUM(delivered), 0) FROM needs
                        WHERE chapter_id = :chapterId AND category = 'WATER'
                          AND state = 'FULFILLED' AND resolved_at > now() - interval '24 hours'
                        """, chapterId),
                aggregate("""
                        SELECT COALESCE(SUM(delivered), 0) FROM needs
                        WHERE chapter_id = :chapterId AND category IN ('FOOD_COOKED','FOOD_DRY')
                          AND state = 'FULFILLED' AND resolved_at > now() - interval '24 hours'
                        """, chapterId),
                aggregate("""
                        SELECT count(*) FROM claims c JOIN needs n ON n.id = c.need_id
                        WHERE n.chapter_id = :chapterId AND c.state = 'DELIVERED'
                          AND c.resolved_at > now() - interval '24 hours'
                        """, chapterId),
                aggregate("""
                        SELECT count(*) FROM shift_signups s JOIN shifts sh ON sh.id = s.shift_id
                        WHERE sh.chapter_id = :chapterId AND s.created_at > now() - interval '24 hours'
                        """, chapterId),
                aggregate("SELECT count(*) FROM devices WHERE last_seen_at > now() - interval '24 hours'", chapterId)
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

    private Mono<Long> aggregate(String sql, short chapterId) {
        return db.sql(sql).bind("chapterId", chapterId)
                .map((row, meta) -> row.get(0, Long.class)).one().defaultIfEmpty(0L);
    }

    private long bucket(Long value) {
        if (value == null || value < 5) return 0;
        return value;
    }
}
