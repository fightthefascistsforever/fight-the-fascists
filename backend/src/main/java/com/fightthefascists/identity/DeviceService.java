package com.fightthefascists.identity;

import com.fightthefascists.config.RedisConfig.FtfProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeviceService {
    private static final List<String> ADJECTIVES = List.of(
            "Teal", "Amber", "Coral", "Sage", "Indigo", "Rust", "Slate", "Moss");
    private static final List<String> ANIMALS = List.of(
            "Ibex", "Heron", "Lynx", "Finch", "Panda", "Otter", "Crane", "Falcon");

    private final FtfProperties props;
    private final SecureRandom random = new SecureRandom();

    public DeviceService(FtfProperties props) {
        this.props = props;
    }

    public byte[] hashDeviceSecret(String deviceSecretB64) {
        try {
            byte[] secret = Base64.getDecoder().decode(deviceSecretB64);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.devicePepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(secret);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid device secret");
        }
    }

    public String hashToHex(byte[] hash) {
        return HexFormat.of().formatHex(hash);
    }

    public String generateHandle() {
        String adj = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String animal = ANIMALS.get(random.nextInt(ANIMALS.size()));
        int num = ThreadLocalRandom.current().nextInt(10, 99);
        return adj + " " + animal + " " + num;
    }

    public String generateDeviceSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
