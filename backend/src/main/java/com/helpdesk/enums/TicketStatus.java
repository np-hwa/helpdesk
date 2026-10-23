package com.helpdesk.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Máy trạng thái của Ticket, khớp bảng chuyển trạng thái ở mục 6 README. */
public enum TicketStatus {
    NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, CANCELLED;

    private static final Map<TicketStatus, Set<TicketStatus>> NEXT = new EnumMap<>(TicketStatus.class);

    static {
        NEXT.put(NEW,         EnumSet.of(ASSIGNED, CANCELLED));
        NEXT.put(ASSIGNED,    EnumSet.of(IN_PROGRESS, CANCELLED));
        NEXT.put(IN_PROGRESS, EnumSet.of(ON_HOLD, RESOLVED, CANCELLED));
        NEXT.put(ON_HOLD,     EnumSet.of(IN_PROGRESS, CANCELLED));
        NEXT.put(RESOLVED,    EnumSet.of(CLOSED, IN_PROGRESS));   // IN_PROGRESS = Reopen
        NEXT.put(CLOSED,      EnumSet.noneOf(TicketStatus.class));
        NEXT.put(CANCELLED,   EnumSet.noneOf(TicketStatus.class));
    }

    public boolean canMoveTo(TicketStatus target) { return NEXT.get(this).contains(target); }

    public boolean isTerminal() { return this == CLOSED || this == CANCELLED; }

    /** Trạng thái "đang mở": còn được phép Hủy bởi IT Leader/Admin. */
    public boolean isOpen() { return this == NEW || this == ASSIGNED || this == IN_PROGRESS || this == ON_HOLD; }
}
