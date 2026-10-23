package com.helpdesk.repository;

import com.helpdesk.entity.PriorityMatrix;
import com.helpdesk.enums.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriorityMatrixRepository extends JpaRepository<PriorityMatrix, Long> {
    Optional<PriorityMatrix> findByImpactAndUrgency(Level impact, Level urgency);
}
