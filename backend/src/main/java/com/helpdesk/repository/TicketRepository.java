package com.helpdesk.repository;

import com.helpdesk.entity.Ticket;
import com.helpdesk.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByRequesterId(Long requesterId, Pageable pageable);

    Page<Ticket> findByAssigneeIdAndStatusIn(Long assigneeId, Collection<TicketStatus> statuses, Pageable pageable);

    /** Ứng viên auto-close: đã RESOLVED quá hạn mà Employee chưa xác nhận. Dùng @Query vì tên field bắt đầu bằng "is". */
    @Query("""
           select t from Ticket t
           where t.status = com.helpdesk.enums.TicketStatus.RESOLVED
             and t.isResolutionConfirmed is null
             and t.resolvedAt < :before
           """)
    List<Ticket> findAutoCloseCandidates(@Param("before") LocalDateTime before);
}
