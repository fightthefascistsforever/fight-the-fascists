package com.fightthefascists.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseHub {
    private final ConcurrentHashMap<Short, Sinks.Many<ServerSentEvent<String>>> sinks = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public Flux<ServerSentEvent<String>> stream(short chapterId) {
        Sinks.Many<ServerSentEvent<String>> sink = sinks.computeIfAbsent(chapterId,
                id -> Sinks.many().multicast().onBackpressureBuffer());
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(25))
                .map(i -> ServerSentEvent.<String>builder()
                        .event("heartbeat")
                        .data("{\"ts\":" + System.currentTimeMillis() + "}")
                        .build());
        return Flux.merge(sink.asFlux(), heartbeat);
    }

    public void broadcast(short chapterId, String event, Object payload) {
        Sinks.Many<ServerSentEvent<String>> sink = sinks.get(chapterId);
        if (sink == null) return;
        try {
            String json = mapper.writeValueAsString(payload);
            sink.tryEmitNext(ServerSentEvent.<String>builder().event(event).data(json).build());
        } catch (Exception ignored) {}
    }
}
