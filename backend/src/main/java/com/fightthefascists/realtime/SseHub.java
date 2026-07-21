package com.fightthefascists.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;

@Component
public class SseHub {
    private final Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final ObjectMapper mapper = new ObjectMapper();

    public Flux<ServerSentEvent<String>> stream() {
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(i -> ServerSentEvent.<String>builder()
                        .event("heartbeat")
                        .data("{\"ts\":" + System.currentTimeMillis() + "}")
                        .build());
        return Flux.merge(sink.asFlux(), heartbeat);
    }

    public void broadcast(String event, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            sink.tryEmitNext(ServerSentEvent.<String>builder().event(event).data(json).build());
        } catch (Exception ignored) {}
    }
}
