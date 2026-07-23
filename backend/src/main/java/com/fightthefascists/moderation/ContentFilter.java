package com.fightthefascists.moderation;

import com.fightthefascists.config.RedisConfig.FtfProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ContentFilter {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?91[-\\s]?\\d{10}|\\b\\d{10,}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w.-]+");
    private static final Pattern UPI_PATTERN = Pattern.compile("\\w+@[\\w]+");

    private final List<String> blocklist = new ArrayList<>();
    private final List<String> medicalKeywords = new ArrayList<>();
    private final FtfProperties props;
    private final SecureRandom random = new SecureRandom();

    public ContentFilter(FtfProperties props) throws Exception {
        this.props = props;
        loadList("filters/blocklist.txt", blocklist);
        loadList("filters/medical_keywords.txt", medicalKeywords);
    }

    private void loadList(String path, List<String> target) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream()))) {
            reader.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).forEach(target::add);
        }
    }

    public FilterResult filter(String input) {
        if (input == null || input.isBlank()) {
            return new FilterResult(null, false, false);
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        String lower = normalized.toLowerCase();

        // F5.E1 — emergency intercept
        for (String kw : medicalKeywords) {
            if (lower.contains(kw.toLowerCase())) {
                throw new com.fightthefascists.common.AppException("EMERGENCY_INTERCEPT",
                        "Medical emergency — call 102/108 immediately",
                        "चिकित्सा आपातकाल — तुरंत 102/108 पर कॉल करें");
            }
        }

        // F1.E12 — strip PII patterns
        String stripped = URL_PATTERN.matcher(normalized).replaceAll("[removed]");
        stripped = PHONE_PATTERN.matcher(stripped).replaceAll("[removed]");
        stripped = EMAIL_PATTERN.matcher(stripped).replaceAll("[removed]");
        stripped = UPI_PATTERN.matcher(stripped).replaceAll("[removed]");

        if (stripped.length() > 200) {
            stripped = stripped.substring(0, 200);
        }

        boolean pendingReview = false;
        for (String blocked : blocklist) {
            if (lower.contains(blocked.toLowerCase())) {
                pendingReview = true;
                break;
            }
        }

        return new FilterResult(stripped, pendingReview, stripped.contains("[removed]"));
    }

    public byte[] encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] key = props.encryptionKey().getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = new byte[32];
            System.arraycopy(key, 0, keyBytes, 0, Math.min(key.length, 32));
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(byte[] data) {
        if (data == null) return null;
        try {
            byte[] key = props.encryptionKey().getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = new byte[32];
            System.arraycopy(key, 0, keyBytes, 0, Math.min(key.length, 32));
            byte[] iv = new byte[12];
            System.arraycopy(data, 0, iv, 0, 12);
            byte[] encrypted = new byte[data.length - 12];
            System.arraycopy(data, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[encrypted]";
        }
    }

    public record FilterResult(String text, boolean pendingReview, boolean stripped) {}
}
