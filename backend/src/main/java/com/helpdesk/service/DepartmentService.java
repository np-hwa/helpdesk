package com.helpdesk.service;

import com.helpdesk.dto.department.DepartmentRequest;
import com.helpdesk.dto.department.DepartmentResponse;
import com.helpdesk.entity.Department;
import com.helpdesk.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById( Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban với ID: " + id));
        return mapToResponse(department);
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new RuntimeException("Tên phòng ban đã tồn tại: " + request.getName());
        }

        Department department = Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Department saved = departmentRepository.save(department);
        return mapToResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban với ID: " + id));

        if (!department.getName().equals(request.getName()) && departmentRepository.existsByName(request.getName())) {
            throw new RuntimeException("Tên phòng ban đã tồn tại: " + request.getName());
        }

        department.setName(request.getName());
        department.setDescription(request.getDescription());

        Department updated = departmentRepository.save(department);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy phòng ban với ID: " + id);
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
