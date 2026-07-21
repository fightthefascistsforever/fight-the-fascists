package com.fightthefascists.forecast;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class ForecastController {
    private final DemandForecastService forecast;
    private final HeatBandService heatBand;

    public ForecastController(DemandForecastService forecast, HeatBandService heatBand) {
        this.forecast = forecast;
        this.heatBand = heatBand;
    }

    @GetMapping("/forecast")
    public Mono<ApiEnvelope<DemandForecastService.ForecastResponse>> getForecast() {
        return forecast.forecast().map(ApiEnvelope::of);
    }

    @GetMapping("/heat-band")
    public Mono<ApiEnvelope<HeatBandService.HeatBandResponse>> getHeatBand() {
        return heatBand.getHeatBand().map(ApiEnvelope::of);
    }
}
