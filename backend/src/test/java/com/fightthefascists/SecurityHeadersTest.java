package com.fightthefascists;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityHeadersTest {
    @Autowired
    WebTestClient client;

    @Test
    void securityHeadersPresent() {
        client.get().uri("/api/v1/chapters/delhi-2026/zones")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Content-Security-Policy")
                .expectHeader().exists("X-Frame-Options")
                .expectHeader().exists("Referrer-Policy");
    }
}
