package com.helpdesk.exception;

/** 409 – xung đột dữ liệu (email trùng, tên phòng ban trùng…). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
