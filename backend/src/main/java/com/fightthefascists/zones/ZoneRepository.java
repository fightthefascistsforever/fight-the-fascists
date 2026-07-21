package com.fightthefascists.zones;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class ZoneRepository {
    private final DatabaseClient db;

    public ZoneRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<ZoneDto> findAllActive(short chapterId) {
        return db.sql("""
                SELECT id, code, name_en, name_hi, landmark_en, landmark_hi, handoff_point,
                       svg_x, svg_y, status::text
                FROM zones WHERE chapter_id = :chapterId AND status != 'ARCHIVED' ORDER BY sort_order
                """)
                .bind("chapterId", chapterId)
                .map((row, meta) -> new ZoneDto(
                        row.get("id", Short.class),
                        row.get("code", String.class),
                        row.get("name_en", String.class),
                        row.get("name_hi", String.class),
                        row.get("landmark_en", String.class),
                        row.get("landmark_hi", String.class),
                        row.get("handoff_point", String.class),
                        row.get("svg_x", Short.class),
                        row.get("svg_y", Short.class),
                        row.get("status", String.class)))
                .all();
    }

    public record ZoneDto(short id, String code, String nameEn, String nameHi,
                          String landmarkEn, String landmarkHi, String handoffPoint,
                          short svgX, short svgY, String status) {}
}
