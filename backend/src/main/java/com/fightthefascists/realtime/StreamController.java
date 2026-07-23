package com.fightthefascists.realtime;

import com.fightthefascists.chapters.ChapterService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/stream")
public class StreamController {
    private final SseHub hub;
    private final ChapterService chapters;

    public StreamController(SseHub hub, ChapterService chapters) {
        this.hub = hub;
        this.chapters = chapters;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMapMany(ch -> hub.stream(ch.id()));
    }
}
