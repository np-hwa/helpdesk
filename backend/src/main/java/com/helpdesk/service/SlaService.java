package com.helpdesk.service;

import com.helpdesk.entity.SlaConfig;
import com.helpdesk.enums.Priority;
import com.helpdesk.repository.SlaConfigRepository;
import com.helpdesk.sla.BusinessCalendarProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SlaService {

    private final SlaConfigRepository slaConfigRepository;
    private final BusinessCalendarProvider calendarProvider;

    private SlaConfig config(Priority p) {
        return slaConfigRepository.findById(p)
                .orElseThrow(() -> new IllegalStateException("Thiếu cấu hình SLA cho " + p));
    }

    /** Critical (is_24x7) tính theo phút thực; các mức khác tính theo phút làm việc. */
    public LocalDateTime calculateDeadline(LocalDateTime start, Priority priority) {
        SlaConfig cfg = config(priority);
        if (Boolean.TRUE.equals(cfg.getIs24x7())) return start.plusMinutes(cfg.getResolutionMinutes());
        return calendarProvider.get().addWorkingMinutes(start, cfg.getResolutionMinutes());
    }

    /** Sau khi hết Tạm chờ: dời deadline đúng bằng khoảng thời gian đã tạm dừng (cùng loại phút với SLA). */
    public LocalDateTime extendAfterPause(LocalDateTime deadline, LocalDateTime pausedAt,
                                          LocalDateTime resumedAt, Priority priority) {
        SlaConfig cfg = config(priority);
        if (Boolean.TRUE.equals(cfg.getIs24x7())) {
            return deadline.plus(Duration.between(pausedAt, resumedAt));
        }
        var calendar = calendarProvider.get();
        long pausedWorkingMinutes = calendar.workingMinutesBetween(pausedAt, resumedAt);
        return calendar.addWorkingMinutes(deadline, pausedWorkingMinutes);
    }
}
