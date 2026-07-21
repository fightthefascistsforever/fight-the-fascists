package com.fightthefascists.abuse;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class RateLimiter {
    private final ReactiveStringRedisTemplate redis;

    public RateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> check(String key, int limit, Duration window) {
        String redisKey = "ratelimit:" + key;
        return redis.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        return redis.expire(redisKey, window).thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    if (count > limit) {
                        return Mono.error(new com.fightthefascists.common.AppException(
                                "RATE_LIMITED",
                                "Too many requests — please wait a moment",
                                "बहुत सारे अनुरोध — कृपया थोड़ा इंतज़ार करें"));
                    }
                    return Mono.empty();
                });
    }
}
