package com.fightthefascists.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class WebConfig {

    @Value("${ftf.cors-allowed-origin:http://localhost:5173}")
    private String corsOrigin;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Idempotency-Replayed"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    public WebFilter securityHeadersFilter() {
        return (exchange, chain) -> {
            var headers = exchange.getResponse().getHeaders();
            headers.add("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
            headers.add("Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=(), usb=()");
            headers.add("Referrer-Policy", "no-referrer");
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
            return chain.filter(exchange);
        };
    }
}
