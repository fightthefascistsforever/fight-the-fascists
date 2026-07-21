package com.fightthefascists.ops;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.config.RedisConfig.FtfProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/chapters/{chapterSlug}/mirror")
public class MirrorController {
    private final FtfProperties props;
    private final ChapterService chapters;

    public MirrorController(FtfProperties props, ChapterService chapters) {
        this.props = props;
        this.chapters = chapters;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Resource> json(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> {
                    Path file = Path.of(props.mirrorPath(), chapterSlug, "board.json");
                    if (!Files.exists(file)) return Mono.empty();
                    return Mono.just((Resource) new FileSystemResource(file));
                });
    }

    @GetMapping(value = "/html", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> html(@PathVariable String chapterSlug) {
        return chapters.requireActive(chapterSlug)
                .flatMap(ch -> {
                    Path file = Path.of(props.mirrorPath(), chapterSlug, "board.html");
                    try {
                        if (!Files.exists(file)) {
                            return Mono.just("<html><body><p>Mirror not yet generated.</p></body></html>");
                        }
                        return Mono.just(Files.readString(file));
                    } catch (Exception e) {
                        return Mono.just("<html><body><p>Mirror unavailable.</p></body></html>");
                    }
                });
    }
}
