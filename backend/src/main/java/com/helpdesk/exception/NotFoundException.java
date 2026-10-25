package com.helpdesk.exception;

/** 404 – không tìm thấy tài nguyên. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
