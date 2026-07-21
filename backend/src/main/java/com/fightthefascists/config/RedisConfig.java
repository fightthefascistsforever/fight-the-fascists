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
            @Value("${ftf.pow-difficulty:12}") int powDifficulty,
            @Value("${ftf.jwt-secret}") String jwtSecret,
            @Value("${ftf.admin.passphrase}") String adminPassphrase,
            @Value("${ftf.admin.totp-secret}") String adminTotpSecret,
            @Value("${ftf.headcount-estimate:800}") int headcountEstimate,
            @Value("${ftf.site-lat:28.627}") double siteLat,
            @Value("${ftf.site-lon:77.216}") double siteLon,
            @Value("${ftf.mirror-path:mirror}") String mirrorPath,
            @Value("${ftf.public-url:http://localhost:5173}") String publicUrl) {
        return new FtfProperties(devicePepper, encryptionKey, powSecret, powDifficulty,
                jwtSecret, adminPassphrase, adminTotpSecret,
                headcountEstimate, siteLat, siteLon, mirrorPath, publicUrl);
    }

    public record FtfProperties(String devicePepper, String encryptionKey, String powSecret, int powDifficulty,
                                String jwtSecret, String adminPassphrase, String adminTotpSecret,
                                int headcountEstimate, double siteLat, double siteLon,
                                String mirrorPath, String publicUrl) {}
}
