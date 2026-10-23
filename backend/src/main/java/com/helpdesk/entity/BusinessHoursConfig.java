package com.helpdesk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "business_hours_config")
@Getter @Setter @NoArgsConstructor
public class BusinessHoursConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week")  private Integer dayOfWeek;      // 1=Thứ Hai ... 7=Chủ Nhật; null nếu là ngày lễ
    @Column(name = "holiday_date") private LocalDate holidayDate;  // null nếu là dòng lịch tuần
    @Column(name = "holiday_name", length = 100) private String holidayName;

    @Column(name = "is_working_day", nullable = false)
    private Boolean isWorkingDay = true;

    @Column(name = "start_time")  private LocalTime startTime;
    @Column(name = "end_time")    private LocalTime endTime;
    @Column(name = "break_start") private LocalTime breakStart;
    @Column(name = "break_end")   private LocalTime breakEnd;
}
