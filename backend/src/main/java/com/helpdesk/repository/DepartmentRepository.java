package com.helpdesk.repository;

import com.helpdesk.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByName(String name);

    Optional<Department> findByName(String name);

    // Lấy danh sách các phòng ban đang hoạt động để gán cho User/Ticket
    List<Department> findByIsActiveTrue();
}