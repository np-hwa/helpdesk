package com.helpdesk.config;

import com.helpdesk.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cấu hình Spring Security – JWT Stateless + RBAC.
 * <p>
 * Phân quyền theo vai trò:
 * ┌────────────────┬──────────────────────────────────────────────────────────────┐
 * │ ADMIN          │ Toàn bộ hệ thống: CRUD user, phòng ban, danh mục, SLA, KPI  │
 * │ IT_LEADER      │ Xem tất cả ticket, phân công, confirm AI, báo cáo            │
 * │ IT_SUPPORT     │ Ticket được giao, bình luận, cập nhật trạng thái             │
 * │ EMPLOYEE       │ Tạo ticket, xem ticket của mình, xác nhận xử lý xong         │
 * └────────────────┴──────────────────────────────────────────────────────────────┘
 * Quyền chi tiết được kiểm tra tại tầng Service bằng @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    // ─── PasswordEncoder ──────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // cost 12 – cân bằng bảo mật/hiệu năng
    }

    // ─── AuthenticationProvider ───────────────────────────────────────────────

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    // ─── SecurityFilterChain ──────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless – không dùng Session
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Tắt CSRF (vì dùng JWT)
            .csrf(AbstractHttpConfigurer::disable)

            // CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Phân quyền endpoint
            .authorizeHttpRequests(auth -> auth

                // Public: đăng nhập, refresh token, swagger
                .requestMatchers(
                    "/auth/login",
                    "/auth/refresh",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()

                // ─── Auth endpoints ───────────────────────────────────────────
                .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()
                .requestMatchers(HttpMethod.POST, "/auth/logout-all").authenticated()

                // ─── User management (ADMIN only) ─────────────────────────────
                .requestMatchers(HttpMethod.GET,    "/users/**").hasAnyRole("ADMIN", "IT_LEADER")
                .requestMatchers(HttpMethod.POST,   "/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/users/*/reset-password").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/users/*/status").hasRole("ADMIN")

                // ─── Profile (bản thân) ───────────────────────────────────────
                .requestMatchers("/users/me/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/users/me/change-password").authenticated()

                // ─── Department management (ADMIN) ────────────────────────────
                .requestMatchers(HttpMethod.GET,    "/departments/**").authenticated()
                .requestMatchers(HttpMethod.POST,   "/departments").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/departments/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/departments/**").hasRole("ADMIN")

                // Tất cả request còn lại: phải xác thực
                .anyRequest().authenticated()
            )

            // Thêm JWT filter trước UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

            // Custom 401/403 response
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authException) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        """
                        {"success":false,"message":"Chưa xác thực. Vui lòng đăng nhập.","data":null}
                        """.strip()
                    );
                })
                .accessDeniedHandler((req, res, accessDeniedException) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        """
                        {"success":false,"message":"Bạn không có quyền thực hiện hành động này.","data":null}
                        """.strip()
                    );
                })
            );

        return http.build();
    }

    // ─── CORS ─────────────────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.helpdesk.internal"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
