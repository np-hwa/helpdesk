package com.helpdesk.entity;

import com.helpdesk.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_status_history")
@Getter @Setter @NoArgsConstructor
public class TicketStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20)
    private TicketStatus fromStatus;                     // null khi tạo mới

    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 20)
    private TicketStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;                              // null = hệ thống (auto-close, job SLA...)

    @Column(length = 500)
    private String note;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static TicketStatusHistory of(Ticket t, TicketStatus from, TicketStatus to, User by, String note) {
        TicketStatusHistory h = new TicketStatusHistory();
        h.ticket = t; h.fromStatus = from; h.toStatus = to; h.changedBy = by; h.note = note;
        h.changedAt = LocalDateTime.now();
        return h;
    }
}
