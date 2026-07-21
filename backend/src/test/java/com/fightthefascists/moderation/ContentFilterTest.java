package com.fightthefascists.moderation;

import com.fightthefascists.config.RedisConfig.FtfProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentFilterTest {
    private ContentFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new ContentFilter(new FtfProperties("pepper", "encryption-key-32-bytes-long!!", "pow", 12));
    }

    @Test
    void stripsPhoneNumbers() {
        var result = filter.filter("Need water call 9876543210");
        assertTrue(result.text().contains("[removed]"));
    }

    @Test
    void interceptsMedicalEmergency() {
        assertThrows(com.fightthefascists.common.AppException.class,
                () -> filter.filter("person collapsed near gate"));
    }
}
