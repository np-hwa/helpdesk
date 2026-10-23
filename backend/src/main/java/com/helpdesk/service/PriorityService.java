package com.helpdesk.service;

import com.helpdesk.entity.PriorityMatrix;
import com.helpdesk.enums.Level;
import com.helpdesk.enums.Priority;
import com.helpdesk.repository.PriorityMatrixRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriorityService {

    private final PriorityMatrixRepository matrixRepository;

    /** Tra bảng priority_matrix (Admin sửa được); thiếu dòng thì dùng công thức điểm làm dự phòng. */
    public Priority resolve(Level impact, Level urgency) {
        return matrixRepository.findByImpactAndUrgency(impact, urgency)
                .map(PriorityMatrix::getPriority)
                .orElseGet(() -> Priority.of(impact, urgency));
    }
}
