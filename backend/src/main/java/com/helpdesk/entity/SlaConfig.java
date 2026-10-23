package com.helpdesk.entity;

import com.helpdesk.enums.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sla_config")
@Getter @Setter @NoArgsConstructor
public class SlaConfig {
    @Id @Enumerated(EnumType.STRING) @Column(length = 10)
    private Priority priority;                       // khóa chính = priority

    @Column(name = "response_minutes", nullable = false)
    private Integer responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private Integer resolutionMinutes;

    @Column(name = "is_24x7", nullable = false)
    private Boolean is24x7 = false;

    @Column(name = "warn_level1_percent", nullable = false)
    private Integer warnLevel1Percent = 50;

    @Column(name = "warn_level2_percent", nullable = false)
    private Integer warnLevel2Percent = 80;
}
