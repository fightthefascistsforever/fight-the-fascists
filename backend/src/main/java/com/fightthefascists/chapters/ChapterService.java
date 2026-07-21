package com.fightthefascists.chapters;

import com.fightthefascists.common.AppException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class ChapterService {
    private final DatabaseClient db;

    public ChapterService(DatabaseClient db) {
        this.db = db;
    }

    public Flux<Chapter> listPublic() {
        return db.sql("""
                SELECT id, slug, name_en, name_hi, location_label_en, location_label_hi,
                       site_lat, site_lon, timezone, headcount_estimate, status::text, public_url, activated_at
                FROM chapters WHERE status IN ('ACTIVE','PLANNED') ORDER BY activated_at DESC NULLS LAST, slug
                """)
                .map(this::mapRow)
                .all();
    }

    public Mono<Chapter> findBySlug(String slug) {
        return db.sql("""
                SELECT id, slug, name_en, name_hi, location_label_en, location_label_hi,
                       site_lat, site_lon, timezone, headcount_estimate, status::text, public_url, activated_at
                FROM chapters WHERE slug = :slug
                """)
                .bind("slug", slug)
                .map(this::mapRow)
                .one();
    }

    public Mono<Chapter> requireActive(String slug) {
        return findBySlug(slug)
                .switchIfEmpty(Mono.error(new AppException("CHAPTER_NOT_FOUND",
                        "Chapter not found: " + slug, "अध्याय नहीं मिला: " + slug)))
                .flatMap(ch -> {
                    if ("ARCHIVED".equals(ch.status())) {
                        return Mono.error(new AppException("CHAPTER_ARCHIVED",
                                "This chapter has ended", "यह अध्याय समाप्त हो गया है"));
                    }
                    if (!"ACTIVE".equals(ch.status())) {
                        return Mono.error(new AppException("CHAPTER_NOT_ACTIVE",
                                "This chapter is not yet active", "यह अध्याय अभी सक्रिय नहीं है"));
                    }
                    return Mono.just(ch);
                });
    }

    public Mono<Chapter> create(CreateRequest req) {
        return db.sql("""
                INSERT INTO chapters (slug, name_en, name_hi, location_label_en, location_label_hi,
                    site_lat, site_lon, timezone, headcount_estimate, status, public_url)
                VALUES (:slug, :nameEn, :nameHi, :locEn, :locHi, :lat, :lon, :tz, :headcount, 'PLANNED', :url)
                RETURNING id
                """)
                .bind("slug", req.slug())
                .bind("nameEn", req.nameEn())
                .bind("nameHi", req.nameHi())
                .bind("locEn", req.locationLabelEn())
                .bind("locHi", req.locationLabelHi())
                .bind("lat", req.siteLat())
                .bind("lon", req.siteLon())
                .bind("tz", req.timezone() != null ? req.timezone() : "UTC")
                .bind("headcount", req.headcountEstimate() != null ? req.headcountEstimate() : 500)
                .bind("url", req.publicUrl())
                .map((row, meta) -> row.get("id", Short.class))
                .one()
                .flatMap(id -> findBySlug(req.slug()))
                .onErrorMap(e -> {
                    if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                        return new AppException("CHAPTER_EXISTS", "Chapter slug already exists", "अध्याय पहले से मौजूद है");
                    }
                    return e;
                });
    }

    public Mono<Chapter> update(String slug, UpdateRequest req) {
        return findBySlug(slug)
                .switchIfEmpty(Mono.error(new AppException("CHAPTER_NOT_FOUND", "Not found", "नहीं मिला")))
                .flatMap(existing -> db.sql("""
                        UPDATE chapters SET
                          name_en = COALESCE(:nameEn, name_en),
                          name_hi = COALESCE(:nameHi, name_hi),
                          location_label_en = COALESCE(:locEn, location_label_en),
                          location_label_hi = COALESCE(:locHi, location_label_hi),
                          site_lat = COALESCE(:lat, site_lat),
                          site_lon = COALESCE(:lon, site_lon),
                          timezone = COALESCE(:tz, timezone),
                          headcount_estimate = COALESCE(:headcount, headcount_estimate),
                          public_url = COALESCE(:url, public_url)
                        WHERE slug = :slug
                        """)
                        .bind("slug", slug)
                        .bind("nameEn", req.nameEn())
                        .bind("nameHi", req.nameHi())
                        .bind("locEn", req.locationLabelEn())
                        .bind("locHi", req.locationLabelHi())
                        .bind("lat", req.siteLat())
                        .bind("lon", req.siteLon())
                        .bind("tz", req.timezone())
                        .bind("headcount", req.headcountEstimate())
                        .bind("url", req.publicUrl())
                        .fetch().rowsUpdated()
                        .then(findBySlug(slug)));
    }

    public Mono<Chapter> activate(String slug) {
        return db.sql("""
                UPDATE chapters SET status = 'ACTIVE', activated_at = now() WHERE slug = :slug AND status = 'PLANNED'
                RETURNING slug
                """)
                .bind("slug", slug)
                .map((row, meta) -> row.get("slug", String.class))
                .one()
                .switchIfEmpty(Mono.error(new AppException("CHAPTER_NOT_FOUND", "Chapter not found or not planned", "नहीं मिला")))
                .flatMap(this::findBySlug);
    }

    public Mono<Chapter> archive(String slug) {
        return db.sql("UPDATE chapters SET status = 'ARCHIVED' WHERE slug = :slug RETURNING slug")
                .bind("slug", slug)
                .map((row, meta) -> row.get("slug", String.class))
                .one()
                .switchIfEmpty(Mono.error(new AppException("CHAPTER_NOT_FOUND", "Not found", "नहीं मिला")))
                .flatMap(this::findBySlug);
    }

    private Chapter mapRow(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return new Chapter(
                row.get("id", Short.class),
                row.get("slug", String.class),
                row.get("name_en", String.class),
                row.get("name_hi", String.class),
                row.get("location_label_en", String.class),
                row.get("location_label_hi", String.class),
                row.get("site_lat", Double.class),
                row.get("site_lon", Double.class),
                row.get("timezone", String.class),
                row.get("headcount_estimate", Integer.class),
                row.get("status", String.class),
                row.get("public_url", String.class),
                row.get("activated_at", Instant.class));
    }

    public record Chapter(short id, String slug, String nameEn, String nameHi,
                          String locationLabelEn, String locationLabelHi,
                          double siteLat, double siteLon, String timezone,
                          int headcountEstimate, String status, String publicUrl,
                          Instant activatedAt) {}

    public record CreateRequest(String slug, String nameEn, String nameHi,
                                String locationLabelEn, String locationLabelHi,
                                Double siteLat, Double siteLon, String timezone,
                                Integer headcountEstimate, String publicUrl) {}

    public record UpdateRequest(String nameEn, String nameHi,
                                String locationLabelEn, String locationLabelHi,
                                Double siteLat, Double siteLon, String timezone,
                                Integer headcountEstimate, String publicUrl) {}
}
