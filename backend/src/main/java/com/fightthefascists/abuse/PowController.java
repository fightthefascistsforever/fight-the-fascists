package com.fightthefascists.abuse;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pow")
public class PowController {
    private final PowChallengeService powService;

    public PowController(PowChallengeService powService) {
        this.powService = powService;
    }

    @GetMapping("/challenge")
    public Mono<ApiEnvelope<Map<String, Object>>> challenge() {
        return Mono.just(ApiEnvelope.of(powService.createChallenge()));
    }
}
