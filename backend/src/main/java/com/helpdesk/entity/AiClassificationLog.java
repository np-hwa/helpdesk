package com.helpdesk.entity;

import com.helpdesk.enums.Level;
import com.helpdesk.enums.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_classification_logs")
@Getter @Setter @NoArgsConstructor
public class AiClassificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @Column(nullable = false, length = 12)
    private String source = "AI";                    // AI | RULE_BASED

    @Column(name = "model_version", length = 50) private String modelVersion;
    @Column(name = "latency_ms")                 private Integer latencyMs;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "predicted_category_id")
    private Category predictedCategory;
    @Column(name = "predicted_confidence", precision = 4, scale = 3)
    private BigDecimal predictedConfidence;
    @Enumerated(EnumType.STRING) @Column(name = "predicted_impact", length = 10)   private Level predictedImpact;
    @Enumerated(EnumType.STRING) @Column(name = "predicted_urgency", length = 10)  private Level predictedUrgency;
    @Enumerated(EnumType.STRING) @Column(name = "predicted_priority", length = 10) private Priority predictedPriority;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "final_category_id")
    private Category finalCategory;
    @Enumerated(EnumType.STRING) @Column(name = "final_impact", length = 10)   private Level finalImpact;
    @Enumerated(EnumType.STRING) @Column(name = "final_urgency", length = 10)  private Level finalUrgency;
    @Enumerated(EnumType.STRING) @Column(name = "final_priority", length = 10) private Priority finalPriority;
    @Column(name = "is_category_accepted") private Boolean isCategoryAccepted;
    @Column(name = "is_priority_accepted") private Boolean isPriorityAccepted;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by")
    private User reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
