package com.helpdesk.repository;

import com.helpdesk.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    @EntityGraph(attributePaths = {"department"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    
    // Dùng để kiểm tra trước khi xóa phòng ban
    boolean existsByDepartmentId(Long departmentId);
    // Nhân viên đang Active trong phòng ban
    boolean existsByDepartmentIdAndStatus(Long departmentId, com.helpdesk.enums.UserStatus status);
}