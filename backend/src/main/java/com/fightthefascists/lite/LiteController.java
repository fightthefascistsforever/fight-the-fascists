package com.fightthefascists.lite;

import com.fightthefascists.needs.NeedService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class LiteController {
    private final NeedService needService;

    public LiteController(NeedService needService) {
        this.needService = needService;
    }

    @GetMapping(value = "/api/v1/lite", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> lite() {
        return needService.listOpen(null, null)
                .collectList()
                .map(needs -> {
                    var sb = new StringBuilder();
                    sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
                    sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
                    sb.append("<title>Fight the Fascists — Lite Board</title>");
                    sb.append("<style>body{font-family:system-ui,sans-serif;margin:1rem;background:#111;color:#eee}");
                    sb.append("h1{font-size:1.2rem}.need{border:1px solid #444;padding:.5rem;margin:.5rem 0;border-radius:4px}");
                    sb.append(".urgent{border-color:#c44}.soon{border-color:#ca4}</style></head><body>");
                    sb.append("<h1>Fight the Fascists — Supply Board (Lite)</h1>");
                    if (needs.isEmpty()) {
                        sb.append("<p>No open needs right now.</p>");
                    }
                    for (var n : needs) {
                        String cls = "URGENT".equals(n.urgency()) ? "need urgent" :
                                "SOON".equals(n.urgency()) ? "need soon" : "need";
                        sb.append("<div class=\"").append(cls).append("\">");
                        sb.append("<strong>").append(escape(n.zoneCode())).append("</strong> — ");
                        sb.append(escape(n.category())).append(": ");
                        sb.append(escape(n.pledged().toPlainString())).append("/");
                        sb.append(escape(n.quantity().toPlainString())).append(" ");
                        sb.append(escape(n.unit()));
                        if (n.note() != null) {
                            sb.append("<br><small>").append(escape(n.note())).append("</small>");
                        }
                        sb.append("</div>");
                    }
                    sb.append("<p><small>Full app: /</small></p></body></html>");
                    return sb.toString();
                });
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
