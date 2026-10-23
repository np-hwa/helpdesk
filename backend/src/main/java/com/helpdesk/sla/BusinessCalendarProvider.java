package com.helpdesk.sla;

import com.helpdesk.entity.BusinessHoursConfig;
import com.helpdesk.repository.BusinessHoursConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Đọc business_hours_config và dựng BusinessCalendar. Bảng nhỏ nên đọc mỗi lần; cần nhanh hơn thì thêm @Cacheable. */
@Component
@RequiredArgsConstructor
public class BusinessCalendarProvider {

    private final BusinessHoursConfigRepository repository;

    public BusinessCalendar get() {
        Map<DayOfWeek, BusinessCalendar.DayRule> weekly = new EnumMap<>(DayOfWeek.class);
        Set<LocalDate> holidays = new HashSet<>();
        for (BusinessHoursConfig c : repository.findAll()) {
            if (c.getHolidayDate() != null) {
                if (!Boolean.TRUE.equals(c.getIsWorkingDay())) holidays.add(c.getHolidayDate());
            } else if (c.getDayOfWeek() != null && Boolean.TRUE.equals(c.getIsWorkingDay())) {
                weekly.put(DayOfWeek.of(c.getDayOfWeek()),
                        new BusinessCalendar.DayRule(c.getStartTime(), c.getEndTime(), c.getBreakStart(), c.getBreakEnd()));
            }
        }
        return new BusinessCalendar(weekly, holidays);
    }
}
