package com.helpdesk.exception;

/** 401 – refresh token không hợp lệ hoặc đã thu hồi. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) { super(message); }
}
