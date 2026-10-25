package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động của Helpdesk System Backend.
 * <p>
 * Kiến trúc: Controller → Service → Repository → Database
 * Auth: JWT (access 15 phút + refresh 7 ngày, rotation)
 * Phân quyền: RBAC với 4 vai trò – ADMIN | IT_LEADER | IT_SUPPORT | EMPLOYEE
 */
@SpringBootApplication
@EnableScheduling
public class HelpdeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpdeskApplication.class, args);
    }
}
