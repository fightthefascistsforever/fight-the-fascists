package com.fightthefascists.forecast;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}")
public class ForecastController {
    private final DemandForecastService forecast;
    private final HeatBandService heatBand;
    private final ChapterService chapters;

    public ForecastController(DemandForecastService forecast, HeatBandService heatBand, ChapterService chapters) {
        this.forecast = forecast;
        this.heatBand = heatBand;
        this.chapters = chapters;
    }

    @GetMapping("/forecast")
    public Mono<ApiEnvelope<DemandForecastService.ForecastResponse>> getForecast(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(forecast::forecast)
                .map(ApiEnvelope::of);
    }

    @GetMapping("/heat-band")
    public Mono<ApiEnvelope<HeatBandService.HeatBandResponse>> getHeatBand(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(heatBand::getHeatBand)
                .map(ApiEnvelope::of);
    }
}
