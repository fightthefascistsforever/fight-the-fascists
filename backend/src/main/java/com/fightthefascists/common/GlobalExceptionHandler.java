package com.fightthefascists.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleApp(AppException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "DUPLICATE_NEED_CLUSTER", "NEED_ALREADY_COVERED", "REQUEST_IN_FLIGHT" -> HttpStatus.CONFLICT;
            case "NEED_NO_LONGER_OPEN" -> HttpStatus.GONE;
            case "QUANTITY_OUT_OF_RANGE", "EMERGENCY_INTERCEPT" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "POW_INVALID", "POW_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ApiEnvelope.error(ex.getCode(), ex.getMessage(), ex.getMessageHi(), ex.getExtras()));
    }
}
