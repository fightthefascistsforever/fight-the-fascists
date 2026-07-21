package com.fightthefascists.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    @Bean
    public FtfProperties ftfProperties(
            @Value("${ftf.device-pepper}") String devicePepper,
            @Value("${ftf.encryption-key}") String encryptionKey,
            @Value("${ftf.pow-secret}") String powSecret,
            @Value("${ftf.pow-difficulty:12}") int powDifficulty) {
        return new FtfProperties(devicePepper, encryptionKey, powSecret, powDifficulty);
    }

    public record FtfProperties(String devicePepper, String encryptionKey, String powSecret, int powDifficulty) {}
}
