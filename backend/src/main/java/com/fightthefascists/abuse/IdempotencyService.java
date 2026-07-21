package com.fightthefascists.abuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class IdempotencyService {
    private static final String IN_PROGRESS = "__IN_PROGRESS__";
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdempotencyService(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> begin(String deviceHex, String key) {
        String redisKey = "idem:" + deviceHex + ":" + key;
        return redis.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, Duration.ofHours(24))
                .flatMap(created -> {
                    if (Boolean.FALSE.equals(created)) {
                        return redis.opsForValue().get(redisKey)
                                .flatMap(existing -> {
                                    if (IN_PROGRESS.equals(existing)) {
                                        return Mono.error(new com.fightthefascists.common.AppException(
                                                "REQUEST_IN_FLIGHT", "Request already in progress", "अनुरोध पहले से चल रहा है"));
                                    }
                                    return Mono.error(new ReplayException(existing));
                                });
                    }
                    return Mono.just(redisKey);
                });
    }

    public Mono<Void> complete(String redisKey, String responseJson) {
        return redis.opsForValue().set(redisKey, responseJson, Duration.ofHours(24)).then();
    }

    public static class ReplayException extends RuntimeException {
        private final String storedResponse;
        public ReplayException(String storedResponse) { this.storedResponse = storedResponse; }
        public String getStoredResponse() { return storedResponse; }
    }
}
