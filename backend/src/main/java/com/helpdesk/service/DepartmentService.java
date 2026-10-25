package com.helpdesk.service;

import com.helpdesk.dto.department.DepartmentRequest;
import com.helpdesk.dto.department.DepartmentResponse;
import com.helpdesk.entity.Department;
import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.DepartmentRepository;
import com.helpdesk.repository.UserRepository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository; // Bổ sung để kiểm tra nhân viên thuộc phòng ban

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(@NonNull Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id));
        return mapToResponse(department);
    }

    @SuppressWarnings("null")
    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        String normalizedName = request.getName().trim();

        if (departmentRepository.existsByName(normalizedName)) {
            throw new BadRequestException("Tên phòng ban đã tồn tại: " + normalizedName);
        }

                 Department department = Department.builder()
                .name(normalizedName)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        @NonNull Department saved = departmentRepository.save(department);
        return mapToResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(@NonNull Long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id));

        String normalizedName = request.getName().trim();

        // Dùng equalsIgnoreCase để cho phép sửa kiểu chữ (ví dụ: "it" -> "IT") của chính phòng ban đó
        if (!department.getName().equalsIgnoreCase(normalizedName) 
                && departmentRepository.existsByName(normalizedName)) {
            throw new BadRequestException("Tên phòng ban đã tồn tại: " + normalizedName);
        }

        department.setName(normalizedName);
        department.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        @NonNull Department updated = departmentRepository.save(department);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteDepartment(@NonNull Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy phòng ban với ID: " + id);
        }

        // Chặn xóa nếu phòng ban đang có nhân viên để tránh lỗi Foreign Key SQL
        if (userRepository.existsByDepartmentId(id)) {
            throw new BadRequestException("Không thể xóa phòng ban này vì vẫn còn nhân viên trực thuộc");
        }

        departmentRepository.deleteById(id);
    }

    private DepartmentResponse mapToResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .createdAt(department.getCreatedAt())
                .build();
    }
}