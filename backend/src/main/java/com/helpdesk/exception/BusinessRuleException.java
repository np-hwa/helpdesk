package com.helpdesk.exception;

/** Vi phạm quy tắc nghiệp vụ (chuyển trạng thái sai, sai quyền...). Map sang HTTP 409/403 ở @RestControllerAdvice. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
