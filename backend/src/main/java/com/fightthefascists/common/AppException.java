package com.fightthefascists.common;

public class AppException extends RuntimeException {
    private final String code;
    private final String messageHi;
    private final Object extras;

    public AppException(String code, String message, String messageHi) {
        this(code, message, messageHi, null);
    }

    public AppException(String code, String message, String messageHi, Object extras) {
        super(message);
        this.code = code;
        this.messageHi = messageHi;
        this.extras = extras;
    }

    public String getCode() { return code; }
    public String getMessageHi() { return messageHi; }
    public Object getExtras() { return extras; }
}
