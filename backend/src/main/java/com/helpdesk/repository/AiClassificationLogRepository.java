package com.helpdesk.repository;

import com.helpdesk.entity.AiClassificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiClassificationLogRepository extends JpaRepository<AiClassificationLog, Long> {
    Optional<AiClassificationLog> findFirstByTicketIdOrderByIdDesc(Long ticketId);
}
