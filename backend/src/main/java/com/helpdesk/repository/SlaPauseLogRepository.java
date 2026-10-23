package com.helpdesk.repository;

import com.helpdesk.entity.SlaPauseLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPauseLogRepository extends JpaRepository<SlaPauseLog, Long> {
    /** Lần tạm dừng đang mở (chưa có resumed_at) của 1 ticket. */
    Optional<SlaPauseLog> findFirstByTicketIdAndResumedAtIsNull(Long ticketId);
}
