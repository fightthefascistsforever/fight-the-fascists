package com.fightthefascists.forecast;

import com.fightthefascists.chapters.ChapterService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeatBandService {
    private final WebClient client = WebClient.builder().build();
    private final Map<Short, CachedWeather> cache = new ConcurrentHashMap<>();

    public Mono<HeatBandResponse> getHeatBand(ChapterService.Chapter chapter) {
        return fetchTemperature(chapter)
                .map(temp -> {
                    String band = temp >= 42 ? "RED" : temp >= 38 ? "AMBER" : "GREEN";
                    String message = switch (band) {
                        case "RED" -> "Extreme heat — hydrate constantly, seek shade, watch for dizziness";
                        case "AMBER" -> "High heat — drink water regularly, take breaks in shade";
                        default -> "Moderate conditions — stay hydrated";
                    };
                    String messageHi = switch (band) {
                        case "RED" -> "अत्यधिक गर्मी — लगातार पानी पिएँ, छाया में रहें, चक्कर पर ध्यान दें";
                        case "AMBER" -> "अधिक गर्मी — नियमित पानी पिएँ, छाया में विश्राम करें";
                        default -> "सामान्य स्थिति — हाइड्रेटेड रहें";
                    };
                    return new HeatBandResponse(band, temp, message, messageHi, Instant.now());
                });
    }

    public Mono<String> currentBand(ChapterService.Chapter chapter) {
        return fetchTemperature(chapter).map(temp -> temp >= 42 ? "RED" : temp >= 38 ? "AMBER" : "GREEN");
    }

    private Mono<Double> fetchTemperature(ChapterService.Chapter chapter) {
        CachedWeather c = cache.get(chapter.id());
        if (c != null && c.expires.isAfter(Instant.now())) {
            return Mono.just(c.temp);
        }
        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m",
                chapter.siteLat(), chapter.siteLon());
        return client.get().uri(url).retrieve().bodyToMono(Map.class)
                .map(body -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> current = (Map<String, Object>) body.get("current");
                    double temp = ((Number) current.get("temperature_2m")).doubleValue();
                    cache.put(chapter.id(), new CachedWeather(temp, Instant.now().plus(Duration.ofMinutes(15))));
                    return temp;
                })
                .onErrorReturn(40.0);
    }

    private record CachedWeather(double temp, Instant expires) {}
    public record HeatBandResponse(String band, double temperatureC, String messageEn, String messageHi, Instant asOf) {}
}
