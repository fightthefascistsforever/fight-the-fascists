package com.fightthefascists.auth;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.fightthefascists.common.AppException;
import com.fightthefascists.config.RedisConfig.FtfProperties;
import com.fightthefascists.identity.DeviceRepository;
import com.fightthefascists.identity.DeviceService;
import com.fightthefascists.moderation.ContentFilter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class StewardAuthService {
    private final DatabaseClient db;
    private final DeviceRepository deviceRepo;
    private final DeviceService deviceService;
    private final ContentFilter contentFilter;
    private final ReactiveStringRedisTemplate redis;
    private final FtfProperties props;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator();

    public StewardAuthService(DatabaseClient db, DeviceRepository deviceRepo, DeviceService deviceService,
                              ContentFilter contentFilter, ReactiveStringRedisTemplate redis,
                              FtfProperties props) {
        this.db = db;
        this.deviceRepo = deviceRepo;
        this.deviceService = deviceService;
        this.contentFilter = contentFilter;
        this.redis = redis;
        this.props = props;
    }

    public Mono<LoginResponse> login(ServerWebExchange exchange, String passphrase, String totpCode) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> verifyCredentials(hash, passphrase, totpCode)
                        .then(issueToken(hash, "ADMIN", null)));
    }

    public Mono<LoginResponse> stewardLogin(ServerWebExchange exchange, String passphrase, String totpCode) {
        return deviceRepo.resolveFromRequest(exchange)
                .flatMap(hash -> db.sql("""
                        SELECT tier::text, steward_zone_id, passphrase_hash, totp_secret_enc
                        FROM steward_credentials WHERE device_hash = :hash AND revoked = false
                        """)
                        .bind("hash", hash)
                        .map((row, meta) -> new CredRow(
                                row.get("tier", String.class),
                                row.get("steward_zone_id", Short.class),
                                row.get("passphrase_hash", String.class),
                                row.get("totp_secret_enc", byte[].class)))
                        .one()
                        .switchIfEmpty(Mono.error(new AppException("UNAUTHORIZED", "Not a steward", "स्टीवर्ड नहीं हैं")))
                        .flatMap(cred -> {
                            if (!bcrypt.matches(passphrase, cred.passphraseHash())) {
                                return Mono.error(new AppException("UNAUTHORIZED", "Invalid credentials", "अमान्य प्रमाण-पत्र"));
                            }
                            return verifyTotp(contentFilter.decrypt(cred.totpSecretEnc()), totpCode)
                                    .then(issueToken(hash, cred.tier(), cred.zoneId()));
                        }));
    }

    private Mono<Void> verifyCredentials(byte[] hash, String passphrase, String totpCode) {
        if (!props.adminPassphrase().equals(passphrase)) {
            return Mono.error(new AppException("UNAUTHORIZED", "Invalid credentials", "अमान्य प्रमाण-पत्र"));
        }
        return verifyTotp(props.adminTotpSecret(), totpCode)
                .then(promoteToAdmin(hash));
    }

    private Mono<Void> promoteToAdmin(byte[] hash) {
        return db.sql("UPDATE devices SET tier = 'ADMIN' WHERE device_hash = :hash")
                .bind("hash", hash).fetch().rowsUpdated().then();
    }

    private Mono<Void> verifyTotp(String secret, String code) {
        try {
            byte[] keyBytes = decodeBase32(secret);
            SecretKey key = new javax.crypto.spec.SecretKeySpec(keyBytes, "HMAC");
            int expected = totp.generateOneTimePassword(key, Instant.now());
            if (!String.valueOf(expected).equals(code.trim())) {
                return Mono.error(new AppException("UNAUTHORIZED", "Invalid TOTP code", "अमान्य TOTP कोड"));
            }
            return Mono.empty();
        } catch (Exception e) {
            return Mono.error(new AppException("UNAUTHORIZED", "TOTP verification failed", "TOTP सत्यापन विफल"));
        }
    }

    public Mono<AuthContext> requireSteward(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return Mono.error(new AppException("UNAUTHORIZED", "Steward token required", "स्टीवर्ड टोकन आवश्यक"));
        }
        return validateToken(auth.substring(7))
                .flatMap(ctx -> redis.hasKey("jwt:deny:" + ctx.jti())
                        .flatMap(denied -> denied
                                ? Mono.error(new AppException("UNAUTHORIZED", "Token revoked", "टोकन रद्द"))
                                : Mono.just(ctx)));
    }

    public Mono<AuthContext> requireAdmin(ServerWebExchange exchange) {
        return requireSteward(exchange)
                .flatMap(ctx -> "ADMIN".equals(ctx.tier())
                        ? Mono.just(ctx)
                        : Mono.error(new AppException("FORBIDDEN", "Admin required", "एडमिन आवश्यक")));
    }

    private Mono<LoginResponse> issueToken(byte[] hash, String tier, Short zoneId) {
        try {
            String jti = UUID.randomUUID().toString();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(deviceService.hashToHex(hash))
                    .claim("tier", tier)
                    .claim("zoneId", zoneId)
                    .jwtID(jti)
                    .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(10))))
                    .issueTime(new Date())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(props.jwtSecret().getBytes(StandardCharsets.UTF_8)));
            return Mono.just(new LoginResponse(jwt.serialize(), tier, Duration.ofMinutes(10).toSeconds()));
        } catch (Exception e) {
            return Mono.error(new AppException("INTERNAL", "Token issue failed", "टोकन जारी करने में विफल"));
        }
    }

    private Mono<AuthContext> validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(props.jwtSecret().getBytes(StandardCharsets.UTF_8)))) {
                return Mono.error(new AppException("UNAUTHORIZED", "Invalid token", "अमान्य टोकन"));
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                return Mono.error(new AppException("UNAUTHORIZED", "Token expired", "टोकन समाप्त"));
            }
            byte[] hash = HexFormat.of().parseHex(claims.getSubject());
            return Mono.just(new AuthContext(hash, claims.getStringClaim("tier"),
                    claims.getLongClaim("zoneId") != null ? claims.getLongClaim("zoneId").shortValue() : null,
                    claims.getJWTID()));
        } catch (Exception e) {
            return Mono.error(new AppException("UNAUTHORIZED", "Invalid token", "अमान्य टोकन"));
        }
    }

    public Mono<Void> grantSteward(byte[] adminHash, byte[] targetHash, short zoneId, String passphrase, String totpSecret) {
        String passHash = bcrypt.encode(passphrase);
        byte[] totpEnc = contentFilter.encrypt(totpSecret);
        return db.sql("UPDATE devices SET tier = 'ZONE_STEWARD', steward_zone_id = :zone WHERE device_hash = :hash")
                .bind("zone", zoneId).bind("hash", targetHash).fetch().rowsUpdated()
                .then(db.sql("""
                        INSERT INTO steward_credentials (device_hash, passphrase_hash, totp_secret_enc, tier, steward_zone_id)
                        VALUES (:hash, :pass, :totp, 'ZONE_STEWARD', :zone)
                        ON CONFLICT (device_hash) DO UPDATE SET passphrase_hash = :pass, totp_secret_enc = :totp,
                            tier = 'ZONE_STEWARD', steward_zone_id = :zone, revoked = false
                        """)
                        .bind("hash", targetHash).bind("pass", passHash)
                        .bind("totp", totpEnc).bind("zone", zoneId)
                        .fetch().rowsUpdated().then())
                .then(audit(adminHash, "GRANT_STEWARD", null));
    }

    public Mono<Void> revokeSteward(byte[] adminHash, byte[] targetHash) {
        return db.sql("UPDATE devices SET tier = 'ANON', steward_zone_id = NULL WHERE device_hash = :hash")
                .bind("hash", targetHash).fetch().rowsUpdated()
                .then(db.sql("UPDATE steward_credentials SET revoked = true WHERE device_hash = :hash")
                        .bind("hash", targetHash).fetch().rowsUpdated().then())
                .then(audit(adminHash, "REVOKE_STEWARD", null));
    }

    public Mono<Void> revokeAllStewards(byte[] adminHash) {
        // F6.E1 panic action
        return db.sql("UPDATE devices SET tier = 'ANON', steward_zone_id = NULL WHERE tier IN ('ZONE_STEWARD','ADMIN') AND device_hash != :admin")
                .bind("admin", adminHash).fetch().rowsUpdated()
                .then(db.sql("UPDATE steward_credentials SET revoked = true").fetch().rowsUpdated().then())
                .then(audit(adminHash, "REVOKE_ALL_STEWARDS", null));
    }

    public Mono<Void> audit(byte[] actor, String action, UUID targetId) {
        return db.sql("INSERT INTO mod_audit (actor_hash, action, target_id) VALUES (:actor, :action, :target)")
                .bind("actor", actor).bind("action", action).bind("target", targetId)
                .fetch().rowsUpdated().then();
    }

    private static byte[] decodeBase32(String secret) {
        String base32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        secret = secret.toUpperCase().replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : secret.toCharArray()) {
            int val = base32.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    public record LoginResponse(String token, String tier, long expiresInSeconds) {}
    public record AuthContext(byte[] deviceHash, String tier, Short zoneId, String jti) {}
    private record CredRow(String tier, Short zoneId, String passphraseHash, byte[] totpSecretEnc) {}
}
