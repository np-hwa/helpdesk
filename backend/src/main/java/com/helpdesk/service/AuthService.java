package com.helpdesk.service;

import com.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.dto.auth.LoginRequest;
import com.helpdesk.dto.auth.RefreshTokenRequest;
import com.helpdesk.entity.User;
import com.helpdesk.exception.InvalidTokenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(request.getEmail());

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .fullName(userDetails.getFullName())
                .role(userDetails.getRole())
                .departmentId(userDetails.getDepartmentId())
                .departmentName(userDetails.getDepartmentName())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        
        if (tokenProvider.validateToken(refreshToken) 
                && "REFRESH".equals(tokenProvider.getTokenType(refreshToken))) {
            
            String email = tokenProvider.getEmailFromToken(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));

            String newAccessToken = tokenProvider.generateAccessTokenFromEmail(email);

            String deptName = (user.getDepartment() != null) ? user.getDepartment().getName() : null;
            Long deptId = (user.getDepartment() != null) ? user.getDepartment().getId() : null;

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .id(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .departmentId(deptId)
                    .departmentName(deptName)
                    .build();
        } else {
            throw new InvalidTokenException("Refresh token không hợp lệ hoặc đã hết hạn");
        }
    }
}