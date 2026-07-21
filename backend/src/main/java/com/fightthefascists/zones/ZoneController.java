package com.fightthefascists.zones;

import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {
    private final ZoneRepository repo;

    public ZoneController(ZoneRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<ZoneRepository.ZoneDto>>> list() {
        return repo.findAllActive().collectList().map(ApiEnvelope::of);
    }
}
