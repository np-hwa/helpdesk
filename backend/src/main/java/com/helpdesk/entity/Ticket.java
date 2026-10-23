package com.helpdesk.entity;

import com.helpdesk.enums.Level;
import com.helpdesk.enums.Priority;
import com.helpdesk.enums.SlaStatus;
import com.helpdesk.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tên field camelCase được Spring Boot tự đổi sang snake_case
 * (spring.jpa.hibernate.naming.physical-strategy mặc định) nên không cần @Column(name=...)
 * trừ khi tên khác quy tắc.
 */
@Entity
@Table(name = "tickets")
@Getter @Setter @NoArgsConstructor
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    // ---- giá trị chính thức (sau khi IT Leader xác nhận) ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Level impact;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Level urgency;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private Priority priority;

    // ---- gợi ý AI ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_suggested_category_id")
    private Category aiSuggestedCategory;

    @Column(name = "ai_category_confidence", precision = 4, scale = 3)
    private BigDecimal aiCategoryConfidence;

    @Enumerated(EnumType.STRING) @Column(name = "ai_suggested_impact", length = 10)
    private Level aiSuggestedImpact;

    @Enumerated(EnumType.STRING) @Column(name = "ai_suggested_urgency", length = 10)
    private Level aiSuggestedUrgency;

    @Enumerated(EnumType.STRING) @Column(name = "ai_suggested_priority", length = 10)
    private Priority aiSuggestedPriority;

    /** Dùng Boolean (không phải boolean) vì có 3 giá trị: null / true / false. */
    @Column(name = "is_ai_suggestion_accepted")
    private Boolean isAiSuggestionAccepted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.NEW;

    // ---- mốc thời gian ----
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "first_response_at") private LocalDateTime firstResponseAt;
    @Column(name = "resolved_at")       private LocalDateTime resolvedAt;
    @Column(name = "closed_at")         private LocalDateTime closedAt;

    // ---- xử lý & xác nhận ----
    @Column(columnDefinition = "TEXT")
    private String resolution;

    /** null = chờ xác nhận | true = đã xong (được phép đóng) | false = chưa xong (chỉ tồn tại thoáng qua, sau đó reopen và reset về null) */
    @Column(name = "is_resolution_confirmed")
    private Boolean isResolutionConfirmed;

    @Column(name = "resolution_confirmed_at")
    private LocalDateTime resolutionConfirmedAt;

    @Column(name = "is_auto_closed", nullable = false)
    private Boolean isAutoClosed = false;

    @Column(name = "reopen_count", nullable = false)
    private Integer reopenCount = 0;

    // ---- SLA ----
    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", nullable = false, length = 10)
    private SlaStatus slaStatus = SlaStatus.ON_TRACK;

    @Column(name = "overdue_minutes")
    private Integer overdueMinutes;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
