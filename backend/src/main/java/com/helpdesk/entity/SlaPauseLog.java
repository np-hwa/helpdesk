package com.helpdesk.entity;

import com.helpdesk.enums.PauseReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sla_pause_log")
@Getter @Setter @NoArgsConstructor
public class SlaPauseLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(name = "paused_at", nullable = false)
    private LocalDateTime pausedAt;

    @Column(name = "resumed_at")                     // null = đang tạm dừng
    private LocalDateTime resumedAt;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PauseReason reason = PauseReason.WAITING_USER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paused_by")
    private User pausedBy;
}
