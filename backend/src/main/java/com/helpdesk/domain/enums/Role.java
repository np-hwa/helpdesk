package com.helpdesk.domain.enums;

/**
 * 4 vai trò cố định trong hệ thống.
 * Được lưu dạng STRING vào cột `users.role` (VARCHAR + CHECK).
 */
public enum Role {
    ADMIN,
    IT_LEADER,
    IT_SUPPORT,
    EMPLOYEE
}
