package com.helpdesk.repository;

import com.helpdesk.domain.entity.User;
import com.helpdesk.domain.enums.Role;
import com.helpdesk.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Lấy danh sách IT Support ACTIVE để phân công (dùng index idx_users_role_status). */
    List<User> findByRoleAndStatus(Role role, UserStatus status);

    /** Tìm kiếm user có phân trang – dùng cho trang quản lý của Admin. */
    @Query("""
           SELECT u FROM User u
           WHERE (:role   IS NULL OR u.role   = :role)
             AND (:status IS NULL OR u.status = :status)
             AND (:keyword IS NULL
                  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :keyword, '%')))
           ORDER BY u.createdAt DESC
           """)
    Page<User> searchUsers(
        @Param("role")    Role role,
        @Param("status")  UserStatus status,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    /** Đếm user theo vai trò (Dashboard). */
    long countByRole(Role role);

    /** Đếm user theo trạng thái. */
    long countByStatus(UserStatus status);
}
