package com.fightthefascists.ops;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fightthefascists.config.RedisConfig.FtfProperties;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/mirror")
public class MirrorController {
    private final FtfProperties props;

    public MirrorController(FtfProperties props) {
        this.props = props;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Resource> json() {
        Path file = Path.of(props.mirrorPath(), "board.json");
        if (!Files.exists(file)) {
            return Mono.empty();
        }
        return Mono.just(new FileSystemResource(file));
    }

    @GetMapping(value = "/html", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> html() {
        Path file = Path.of(props.mirrorPath(), "board.html");
        try {
            if (!Files.exists(file)) return Mono.just("<html><body><p>Mirror not yet generated.</p></body></html>");
            return Mono.just(Files.readString(file));
        } catch (Exception e) {
            return Mono.just("<html><body><p>Mirror unavailable.</p></body></html>");
        }
    }
}
