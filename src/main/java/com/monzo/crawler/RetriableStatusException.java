package com.monzo.crawler;

public class RetriableStatusException extends Exception {
    private final int statusCode;
    public RetriableStatusException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
    public int getStatusCode() { return statusCode; }
}
