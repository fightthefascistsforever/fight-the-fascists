package com.fightthefascists.ops;

import com.fightthefascists.chapters.ChapterService;
import com.fightthefascists.config.RedisConfig.FtfProperties;
import com.fightthefascists.needs.NeedService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

@Component
public class MirrorSnapshotJob {
    private final NeedService needService;
    private final StatsService statsService;
    private final ChapterService chapters;
    private final FtfProperties props;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public MirrorSnapshotJob(NeedService needService, StatsService statsService,
                             ChapterService chapters, FtfProperties props) {
        this.needService = needService;
        this.statsService = statsService;
        this.chapters = chapters;
        this.props = props;
    }

    @Scheduled(fixedRate = 60_000)
    public void regenerate() {
        chapters.listPublic()
                .filter(ch -> "ACTIVE".equals(ch.status()))
                .flatMap(ch -> Mono.zip(
                        needService.listOpen(ch.id(), null, null).collectList(),
                        statsService.transparencyStats(ch.id())
                ).flatMap(tuple -> writeSnapshot(ch.slug(), ch.nameEn(), tuple.getT1(), tuple.getT2())))
                .onErrorComplete()
                .subscribe();
    }

    private Mono<Void> writeSnapshot(String slug, String name, java.util.List<NeedService.NeedDto> needs,
                                     Map<String, Object> stats) {
        return Mono.fromRunnable(() -> {
            try {
                var snapshot = Map.of(
                        "chapter", slug,
                        "generatedAt", Instant.now().toString(),
                        "needs", needs,
                        "stats", stats
                );
                Path dir = Path.of(props.mirrorPath(), slug);
                Files.createDirectories(dir);
                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
                Files.writeString(dir.resolve("board.json"), json);
                Files.writeString(dir.resolve("board.html"), toHtml(name, needs));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String toHtml(String chapterName, java.util.List<NeedService.NeedDto> needs) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=utf-8><title>Fight the Fascists Mirror</title>");
        sb.append("<meta http-equiv=refresh content=60>");
        sb.append("<style>body{font-family:system-ui;max-width:600px;margin:2rem auto;padding:0 1rem}");
        sb.append(".need{border:1px solid #ccc;padding:.5rem;margin:.5rem 0;border-radius:4px}</style></head><body>");
        sb.append("<h1>Fight the Fascists — ").append(escape(chapterName)).append("</h1>");
        sb.append("<p>Read-only snapshot. Refreshes every 60s.</p>");
        for (var n : needs) {
            sb.append("<div class=need><strong>").append(escape(n.zoneCode())).append("</strong> ")
                    .append(escape(n.category())).append(": ")
                    .append(n.pledged()).append("/").append(n.quantity()).append(" ")
                    .append(escape(n.unit())).append("</div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
