package com.fightthefascists.identity;

import com.fightthefascists.config.RedisConfig.FtfProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeviceServiceTest {
    private final DeviceService service = new DeviceService(
            new FtfProperties("test-pepper", "test-key", "pow-secret", 8,
                    "jwt-secret-test", "pass", "JBSWY3DPEHPK3PXP",
                    800, 28.627, 77.216, "mirror", "http://localhost:5173"));

    @Test
    void hashIsDeterministic() {
        String secret = service.generateDeviceSecret();
        byte[] h1 = service.hashDeviceSecret(secret);
        byte[] h2 = service.hashDeviceSecret(secret);
        assertArrayEquals(h1, h2);
    }

    @Test
    void generateHandleHasThreeParts() {
        String handle = service.generateHandle();
        assertTrue(handle.split(" ").length >= 3);
    }
}
