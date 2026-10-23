package com.helpdesk.entity;

import com.helpdesk.enums.Level;
import com.helpdesk.enums.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "priority_matrix")
@Getter @Setter @NoArgsConstructor
public class PriorityMatrix {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Level impact;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Level urgency;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Priority priority;
}
