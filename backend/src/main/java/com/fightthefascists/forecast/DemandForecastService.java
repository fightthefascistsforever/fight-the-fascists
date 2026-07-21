package com.fightthefascists.forecast;

import com.fightthefascists.bulk.BulkPledgeService;
import com.fightthefascists.chapters.ChapterService;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DemandForecastService {
    private final DatabaseClient db;
    private final BulkPledgeService bulkService;
    private final HeatBandService heatBand;

    public DemandForecastService(DatabaseClient db, BulkPledgeService bulkService, HeatBandService heatBand) {
        this.db = db;
        this.bulkService = bulkService;
        this.heatBand = heatBand;
    }

    public Mono<ForecastResponse> forecast(ChapterService.Chapter chapter) {
        ZoneId tz = ZoneId.of(chapter.timezone());
        return heatBand.currentBand(chapter).flatMap(band -> {
            int headcount = chapter.headcountEstimate();
            int hour = ZonedDateTime.now(tz).getHour();
            List<ShortfallDto> shortfalls = new ArrayList<>();

            return Mono.when(
                    addShortfall(shortfalls, chapter.id(), "WATER", "LITRES", headcount, band, hour, waterRate(band)),
                    addShortfall(shortfalls, chapter.id(), "ORS_ELECTROLYTE", "PACKETS", headcount, band, hour, orsRate(band)),
                    addShortfall(shortfalls, chapter.id(), "FOOD_COOKED", "MEALS", headcount, band, hour, 0.33)
            ).thenReturn(new ForecastResponse(band, headcount, shortfalls, timeWindow(hour)));
        });
    }

    private double waterRate(String band) {
        return switch (band) { case "RED" -> 0.50; case "AMBER" -> 0.35; default -> 0.20; };
    }

    private double orsRate(String band) {
        return "RED".equals(band) ? 0.08 : 0.03;
    }

    private Mono<Void> addShortfall(List<ShortfallDto> out, short chapterId, String category, String unit,
                                    int headcount, String band, int hour, double perCapitaPerHour) {
        BigDecimal projected = BigDecimal.valueOf(headcount * perCapitaPerHour * 6)
                .setScale(0, RoundingMode.CEILING);

        return bulkService.approvedSupplyFor(chapterId, category, hour)
                .zipWith(openNeeds(chapterId, category))
                .doOnNext(tuple -> {
                    BigDecimal bulk = tuple.getT1();
                    BigDecimal open = tuple.getT2();
                    BigDecimal gap = projected.subtract(bulk).subtract(open);
                    if (gap.compareTo(BigDecimal.ZERO) > 0) {
                        out.add(new ShortfallDto(category, unit, projected, bulk, open, gap));
                    }
                })
                .then();
    }

    private Mono<BigDecimal> openNeeds(short chapterId, String category) {
        return db.sql("""
                SELECT COALESCE(SUM(GREATEST(quantity - pledged, 0)), 0) as open
                FROM needs WHERE chapter_id = :chapterId AND category = :cat::need_category
                  AND state IN ('OPEN','CLAIMED') AND hidden_pending_review = false
                """)
                .bind("chapterId", chapterId)
                .bind("cat", category)
                .map((row, meta) -> row.get("open", BigDecimal.class))
                .one();
    }

    private String timeWindow(int hour) {
        return String.format("%02d:00–%02d:00", hour, (hour + 6) % 24);
    }

    public record ForecastResponse(String heatBand, int headcountEstimate,
                                 List<ShortfallDto> shortfalls, String timeWindow) {}

    public record ShortfallDto(String category, String unit, BigDecimal projected,
                               BigDecimal bulkSupply, BigDecimal openNeeds, BigDecimal shortfall) {}
}
