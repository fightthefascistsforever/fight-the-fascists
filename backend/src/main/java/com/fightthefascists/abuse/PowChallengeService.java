package com.fightthefascists.abuse;

import com.fightthefascists.config.RedisConfig.FtfProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class PowChallengeService {
    private final FtfProperties props;

    public PowChallengeService(FtfProperties props) {
        this.props = props;
    }

    public Map<String, Object> createChallenge() {
        String challenge = UUID.randomUUID().toString().replace("-", "");
        String sig = sign(challenge);
        return Map.of(
                "challenge", challenge,
                "signature", sig,
                "difficulty", props.powDifficulty(),
                "expiresAt", Instant.now().plusSeconds(300).toString());
    }

    public void verify(String powHeader) {
        if (powHeader == null || !powHeader.contains(".")) {
            throw new com.fightthefascists.common.AppException("POW_REQUIRED", "Proof of work required", "प्रूफ ऑफ वर्क आवश्यक है");
        }
        String[] parts = powHeader.split("\\.", 2);
        String challenge = parts[0];
        String nonce = parts[1];
        String expectedSig = sign(challenge);
        if (!MessageDigest.isEqual(expectedSig.getBytes(), sign(challenge).getBytes())) {
            throw new com.fightthefascists.common.AppException("POW_INVALID", "Invalid proof of work", "अमान्य प्रूफ ऑफ वर्क");
        }
        String hash = sha256(challenge + nonce);
        int leadingZeros = countLeadingZeroBits(hash);
        if (leadingZeros < props.powDifficulty()) {
            throw new com.fightthefascists.common.AppException("POW_INVALID", "Proof of work too weak", "प्रूफ ऑफ वर्क कमज़ोर है");
        }
    }

    private String sign(String challenge) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.powSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int countLeadingZeroBits(String hex) {
        int bits = 0;
        for (char c : hex.toCharArray()) {
            int nibble = Character.digit(c, 16);
            if (nibble == 0) {
                bits += 4;
            } else {
                bits += Integer.numberOfLeadingZeros(nibble) - 28;
                break;
            }
        }
        return bits;
    }
}
