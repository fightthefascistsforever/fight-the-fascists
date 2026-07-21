package com.fightthefascists.zones;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.common.ApiEnvelope;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/zones")
public class ZoneController {
    private final ZoneRepository repo;
    private final ChapterService chapters;

    public ZoneController(ZoneRepository repo, ChapterService chapters) {
        this.repo = repo;
        this.chapters = chapters;
    }

    @GetMapping
    public Mono<ApiEnvelope<List<ZoneRepository.ZoneDto>>> list(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> repo.findAllActive(ch.id()).collectList())
                .map(ApiEnvelope::of);
    }
}
