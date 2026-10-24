package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class HelpdeskApplication {

    public static void main(String[] args) {
        // Đảm bảo TimeZone ứng dụng đồng bộ với Việt Nam
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(HelpdeskApplication.class, args);
    }
}
