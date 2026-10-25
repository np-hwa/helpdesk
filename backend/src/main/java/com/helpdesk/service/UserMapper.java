package com.helpdesk.service;

import com.helpdesk.domain.entity.User;
import com.helpdesk.dto.response.UserResponse;
import com.helpdesk.repository.DepartmentRepository;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi User entity → UserResponse DTO.
 * Dùng component thay vì MapStruct để kiểm soát logic mapping phức tạp.
 */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) return null;

        String deptName = user.getDepartment() != null
            ? user.getDepartment().getName() : null;
        Long deptId = user.getDepartment() != null
            ? user.getDepartment().getId() : null;

        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            deptId,
            deptName,
            user.getRole(),
            user.getStatus(),
            user.getEmailNotify(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
