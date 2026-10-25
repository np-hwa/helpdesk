package com.helpdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình Springdoc OpenAPI (Swagger UI).
 * Truy cập: http://localhost:8080/api/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI helpdeskOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("Helpdesk System API")
                .description("""
                    REST API cho Hệ thống Helpdesk quản lý yêu cầu hỗ trợ kỹ thuật.
                    
                    **Vai trò:**
                    - `ADMIN` – Quản lý toàn hệ thống
                    - `IT_LEADER` – Phân công, xác nhận AI, báo cáo
                    - `IT_SUPPORT` – Xử lý ticket được phân công
                    - `EMPLOYEE` – Tạo ticket, xác nhận hoàn tất
                    
                    **Xác thực:** Bearer JWT (đăng nhập tại `/auth/login`)
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Helpdesk Dev Team")
                    .email("dev@helpdesk.internal")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Nhập access token JWT (không cần tiền tố 'Bearer')")
                )
            );
    }
}
