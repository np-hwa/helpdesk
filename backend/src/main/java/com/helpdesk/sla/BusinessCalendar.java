package com.helpdesk.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lịch làm việc thuần Java (không phụ thuộc Spring) để dễ unit test.
 * Dữ liệu lấy từ bảng business_hours_config.
 */
public class BusinessCalendar {

    /** Quy tắc 1 ngày trong tuần. breakStart/breakEnd có thể null (không nghỉ trưa). */
    public record DayRule(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd) {}

    private static final int MAX_DAYS = 3650;

    private final Map<DayOfWeek, DayRule> weekly;
    private final Set<LocalDate> holidays;

    public BusinessCalendar(Map<DayOfWeek, DayRule> weekly, Set<LocalDate> holidays) {
        this.weekly = weekly;
        this.holidays = holidays;
    }

    /** Các khoảng làm việc trong 1 ngày (đã tách theo giờ nghỉ trưa). */
    private List<LocalDateTime[]> windowsOn(LocalDate d) {
        List<LocalDateTime[]> res = new ArrayList<>();
        DayRule r = weekly.get(d.getDayOfWeek());
        if (r == null || holidays.contains(d)) return res;
        if (r.breakStart() != null && r.breakEnd() != null) {
            res.add(new LocalDateTime[]{d.atTime(r.start()), d.atTime(r.breakStart())});
            res.add(new LocalDateTime[]{d.atTime(r.breakEnd()), d.atTime(r.end())});
        } else {
            res.add(new LocalDateTime[]{d.atTime(r.start()), d.atTime(r.end())});
        }
        return res;
    }

    /** Cộng N phút LÀM VIỆC vào mốc from. Ticket tạo ngoài giờ sẽ bắt đầu tính từ giờ làm việc kế tiếp. */
    public LocalDateTime addWorkingMinutes(LocalDateTime from, long minutes) {
        if (minutes <= 0) return from;
        long remaining = minutes;
        LocalDate day = from.toLocalDate();
        for (int i = 0; i < MAX_DAYS; i++, day = day.plusDays(1)) {
            for (LocalDateTime[] w : windowsOn(day)) {
                LocalDateTime s = w[0].isBefore(from) ? from : w[0];
                if (!s.isBefore(w[1])) continue;
                long available = Duration.between(s, w[1]).toMinutes();
                if (remaining <= available) return s.plusMinutes(remaining);
                remaining -= available;
            }
        }
        throw new IllegalStateException("Không tìm được mốc hạn: kiểm tra cấu hình business_hours_config");
    }

    /** Số phút làm việc giữa from và to (dùng để trừ thời gian Tạm chờ khỏi SLA). */
    public long workingMinutesBetween(LocalDateTime from, LocalDateTime to) {
        if (!to.isAfter(from)) return 0;
        long total = 0;
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            for (LocalDateTime[] w : windowsOn(d)) {
                LocalDateTime s = w[0].isBefore(from) ? from : w[0];
                LocalDateTime e = w[1].isAfter(to) ? to : w[1];
                if (s.isBefore(e)) total += Duration.between(s, e).toMinutes();
            }
        }
        return total;
    }
}
