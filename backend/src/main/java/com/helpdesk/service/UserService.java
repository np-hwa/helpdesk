package com.helpdesk.service;

import com.helpdesk.domain.entity.Department;
import com.helpdesk.domain.entity.User;
import com.helpdesk.domain.enums.Role;
import com.helpdesk.domain.enums.UserStatus;
import com.helpdesk.dto.request.AdminResetPasswordRequest;
import com.helpdesk.dto.request.ChangePasswordRequest;
import com.helpdesk.dto.request.CreateUserRequest;
import com.helpdesk.dto.request.UpdateUserRequest;
import com.helpdesk.dto.response.UserResponse;
import com.helpdesk.exception.ConflictException;
import com.helpdesk.exception.NotFoundException;
import com.helpdesk.repository.DepartmentRepository;
import com.helpdesk.repository.RefreshTokenRepository;
import com.helpdesk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service quản lý tài khoản người dùng và phân quyền.
 * <p>
 * Phân quyền tầng Service (@PreAuthorize):
 * - Tạo / sửa / xóa / khóa user:   chỉ ADMIN
 * - Xem danh sách user:             ADMIN, IT_LEADER
 * - Đổi mật khẩu bản thân:         bất kỳ user đã đăng nhập
 * - Admin reset mật khẩu người dùng: chỉ ADMIN
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository         userRepo;
    private final DepartmentRepository   deptRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordEncoder        passwordEncoder;
    private final UserMapper             userMapper;

    // ─── Tạo tài khoản ────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        // Kiểm tra email trùng
        if (userRepo.existsByEmail(req.email().toLowerCase().trim())) {
            throw new ConflictException("Email đã tồn tại trong hệ thống: " + req.email());
        }

        Department dept = null;
        if (req.departmentId() != null) {
            dept = deptRepo.findById(req.departmentId())
                .orElseThrow(() -> new NotFoundException("Phòng ban không tồn tại: " + req.departmentId()));
        }

        User user = User.builder()
            .email(req.email().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(req.password()))
            .fullName(req.fullName().trim())
            .phone(req.phone())
            .department(dept)
            .role(req.role())
            .status(UserStatus.ACTIVE)
            .emailNotify(true)
            .build();

        User saved = userRepo.save(user);
        log.info("Tạo tài khoản mới: id={}, email={}, role={}", saved.getId(), saved.getEmail(), saved.getRole());
        return userMapper.toResponse(saved);
    }

    // ─── Lấy danh sách người dùng (phân trang, tìm kiếm) ─────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','IT_LEADER')")
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Role role, UserStatus status, String keyword, Pageable pageable) {
        return userRepo.searchUsers(role, status, keyword, pageable)
                       .map(userMapper::toResponse);
    }

    // ─── Lấy thông tin một người dùng ────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','IT_LEADER')")
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userMapper.toResponse(findById(id));
    }

    // ─── Cập nhật thông tin người dùng ───────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest req) {
        User user = findById(id);

        // Kiểm tra phòng ban
        if (req.departmentId() != null) {
            Department dept = deptRepo.findById(req.departmentId())
                .orElseThrow(() -> new NotFoundException("Phòng ban không tồn tại: " + req.departmentId()));
            user.setDepartment(dept);
        } else {
            user.setDepartment(null);
        }

        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone());
        user.setRole(req.role());
        user.setStatus(req.status());
        if (req.emailNotify() != null) user.setEmailNotify(req.emailNotify());

        User saved = userRepo.save(user);
        log.info("Cập nhật user id={}: role={}, status={}", id, req.role(), req.status());
        return userMapper.toResponse(saved);
    }

    // ─── Thay đổi trạng thái tài khoản ──────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse changeStatus(Long id, UserStatus newStatus) {
        User user = findById(id);
        UserStatus old = user.getStatus();
        user.setStatus(newStatus);

        // Khi khóa tài khoản → thu hồi toàn bộ refresh token
        if (newStatus == UserStatus.LOCKED || newStatus == UserStatus.INACTIVE) {
            int revoked = refreshTokenRepo.revokeAllByUserId(id, LocalDateTime.now());
            log.info("Thu hồi {} refresh token của user id={} do đổi status={}", revoked, id, newStatus);
        }

        User saved = userRepo.save(user);
        log.info("Đổi trạng thái user id={}: {} → {}", id, old, newStatus);
        return userMapper.toResponse(saved);
    }

    // ─── Admin đặt lại mật khẩu ──────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void adminResetPassword(Long id, AdminResetPasswordRequest req) {
        User user = findById(id);
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);

        // Thu hồi tất cả refresh token để force đăng nhập lại
        refreshTokenRepo.revokeAllByUserId(id, LocalDateTime.now());
        log.info("Admin đặt lại mật khẩu cho user id={}. Đã thu hồi tất cả session.", id);
    }

    // ─── Người dùng tự đổi mật khẩu ─────────────────────────────────────────

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        User user = findById(userId);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new AccessDeniedException("Mật khẩu hiện tại không đúng.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);

        // Thu hồi tất cả session khác (bảo mật sau khi đổi mật khẩu)
        refreshTokenRepo.revokeAllByUserId(userId, LocalDateTime.now());
        log.info("User id={} đã đổi mật khẩu. Thu hồi toàn bộ session cũ.", userId);
    }

    // ─── Lấy profile bản thân ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserResponse getMyProfile(Long userId) {
        return userMapper.toResponse(findById(userId));
    }

    // ─── Lấy IT Support ACTIVE (dùng cho phân công) ───────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','IT_LEADER')")
    @Transactional(readOnly = true)
    public List<UserResponse> getActiveItSupportList() {
        return userRepo.findByRoleAndStatus(Role.IT_SUPPORT, UserStatus.ACTIVE)
                       .stream()
                       .map(userMapper::toResponse)
                       .toList();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private User findById(Long id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với id: " + id));
    }
}
