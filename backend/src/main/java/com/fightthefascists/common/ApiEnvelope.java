package com.fightthefascists.common;

import java.time.Instant;
import java.util.Map;

public record ApiEnvelope<T>(T data, Instant serverNow) {
    public static <T> ApiEnvelope<T> of(T data) {
        return new ApiEnvelope<>(data, Instant.now());
    }

    public static Map<String, Object> error(String code, String message, String messageHi, Object extras) {
        var error = new java.util.LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", message);
        error.put("messageHi", messageHi);
        if (extras != null) {
            if (extras instanceof Map<?, ?> m) {
                m.forEach((k, v) -> error.put(String.valueOf(k), v));
            }
        }
        return Map.of("error", error, "serverNow", Instant.now());
    }
}
