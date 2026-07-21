package com.fightthefascists.forecast;

import com.fightthefascists.bulk.BulkPledgeService;
import com.fightthefascists.config.RedisConfig.FtfProperties;
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
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DatabaseClient db;
    private final BulkPledgeService bulkService;
    private final HeatBandService heatBand;
    private final FtfProperties props;

    public DemandForecastService(DatabaseClient db, BulkPledgeService bulkService,
                                 HeatBandService heatBand, FtfProperties props) {
        this.db = db;
        this.bulkService = bulkService;
        this.heatBand = heatBand;
        this.props = props;
    }

    // F11 — projected_need = headcount × per_capita_rate × hours − confirmed supply − open needs
    public Mono<ForecastResponse> forecast() {
        return heatBand.currentBand().flatMap(band -> {
            int headcount = props.headcountEstimate();
            int hour = ZonedDateTime.now(IST).getHour();
            List<ShortfallDto> shortfalls = new ArrayList<>();

            return Mono.when(
                    addShortfall(shortfalls, "WATER", "LITRES", headcount, band, hour, waterRate(band)),
                    addShortfall(shortfalls, "ORS_ELECTROLYTE", "PACKETS", headcount, band, hour, orsRate(band)),
                    addShortfall(shortfalls, "FOOD_COOKED", "MEALS", headcount, band, hour, 0.33)
            ).thenReturn(new ForecastResponse(band, headcount, shortfalls, timeWindow(hour)));
        });
    }

    private double waterRate(String band) {
        return switch (band) { case "RED" -> 0.50; case "AMBER" -> 0.35; default -> 0.20; };
    }

    private double orsRate(String band) {
        return "RED".equals(band) ? 0.08 : 0.03;
    }

    private Mono<Void> addShortfall(List<ShortfallDto> out, String category, String unit,
                                    int headcount, String band, int hour, double perCapitaPerHour) {
        BigDecimal projected = BigDecimal.valueOf(headcount * perCapitaPerHour * 6)
                .setScale(0, RoundingMode.CEILING);

        return bulkService.approvedSupplyFor(category, hour)
                .zipWith(openNeeds(category))
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

    private Mono<BigDecimal> openNeeds(String category) {
        return db.sql("""
                SELECT COALESCE(SUM(GREATEST(quantity - pledged, 0)), 0) as open
                FROM needs WHERE category = :cat::need_category
                  AND state IN ('OPEN','CLAIMED') AND hidden_pending_review = false
                """)
                .bind("cat", category)
                .map((row, meta) -> row.get("open", BigDecimal.class))
                .one();
    }

    private String timeWindow(int hour) {
        return String.format("%02d:00–%02d:00 IST", hour, (hour + 6) % 24);
    }

    public record ForecastResponse(String heatBand, int headcountEstimate,
                                 List<ShortfallDto> shortfalls, String timeWindow) {}

    public record ShortfallDto(String category, String unit, BigDecimal projected,
                               BigDecimal bulkSupply, BigDecimal openNeeds, BigDecimal shortfall) {}
}
