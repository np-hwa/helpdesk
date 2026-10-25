package com.helpdesk.exception;

/** 403 – tài khoản bị khóa hoặc vô hiệu hóa. */
public class UserLockedException extends RuntimeException {
    public UserLockedException(String message) { super(message); }
}
