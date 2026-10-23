package com.helpdesk.service;

import com.helpdesk.entity.*;
import com.helpdesk.enums.*;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Toàn bộ quy tắc nghiệp vụ của vòng đời ticket nằm ở đây (KHÔNG đặt ở Controller/Frontend).
 * Mỗi hàm public = 1 thao tác của 1 vai trò; mọi lần đổi trạng thái đi qua transition().
 */
@Service
@RequiredArgsConstructor
public class TicketWorkflowService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final SlaPauseLogRepository pauseLogRepository;
    private final AiClassificationLogRepository aiLogRepository;
    private final PriorityService priorityService;
    private final SlaService slaService;

    // ------------------------------------------------------------------ IT Leader
    /** Xác nhận/điều chỉnh gợi ý AI: ghi category, impact, urgency, priority CHÍNH THỨC + cờ chấp nhận. */
    @Transactional
    public Ticket confirmClassification(Long ticketId, User leader, Long categoryId, Level impact, Level urgency) {
        requireRole(leader, Role.IT_LEADER, Role.ADMIN);
        Ticket t = load(ticketId);
        if (t.getStatus() != TicketStatus.NEW) throw new BusinessRuleException("Chỉ phân loại được ticket ở trạng thái NEW");

        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessRuleException("Danh mục không tồn tại"));
        Priority priority = priorityService.resolve(impact, urgency);

        t.setCategory(cat);
        t.setImpact(impact);
        t.setUrgency(urgency);
        t.setPriority(priority);

        boolean hasAi = t.getAiSuggestedCategory() != null;
        boolean categoryAccepted = hasAi && Objects.equals(t.getAiSuggestedCategory().getId(), cat.getId());
        boolean priorityAccepted = hasAi && priority == t.getAiSuggestedPriority();
        // Chỉ ghi cờ khi CÓ gợi ý AI (đủ tin cậy); ngược lại để NULL để không làm sai Acceptance Rate.
        t.setIsAiSuggestionAccepted(hasAi ? (categoryAccepted && priorityAccepted) : null);

        // Cập nhật log AI: đây là dữ liệu nhãn để retrain
        aiLogRepository.findFirstByTicketIdOrderByIdDesc(ticketId).ifPresent(l -> {
            l.setFinalCategory(cat);
            l.setFinalImpact(impact);
            l.setFinalUrgency(urgency);
            l.setFinalPriority(priority);
            l.setIsCategoryAccepted(l.getPredictedCategory() != null
                    && Objects.equals(l.getPredictedCategory().getId(), cat.getId()));
            l.setIsPriorityAccepted(l.getPredictedPriority() == priority);
            l.setReviewedBy(leader);
            l.setReviewedAt(LocalDateTime.now());
        });
        return ticketRepository.save(t);
    }

    @Transactional
    public Ticket assign(Long ticketId, Long assigneeId, User leader) {
        requireRole(leader, Role.IT_LEADER, Role.ADMIN);
        Ticket t = load(ticketId);
        if (t.getPriority() == null || t.getCategory() == null)
            throw new BusinessRuleException("Cần xác nhận category và priority trước khi phân công");

        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new BusinessRuleException("Người xử lý không tồn tại"));
        if (assignee.getRole() != Role.IT_SUPPORT || assignee.getStatus() != UserStatus.ACTIVE)
            throw new BusinessRuleException("Người được phân công phải là IT Support đang hoạt động");

        t.setAssignee(assignee);
        // Chính sách: SLA tính từ lúc tạo ticket (created_at). Đổi sang "từ lúc xác nhận" thì thay tham số thứ nhất.
        t.setSlaDeadline(slaService.calculateDeadline(t.getCreatedAt(), t.getPriority()));
        t.setSlaStatus(SlaStatus.ON_TRACK);
        return transition(t, TicketStatus.ASSIGNED, leader, "Phân công cho " + assignee.getFullName());
    }

    // ------------------------------------------------------------------ IT Support
    @Transactional
    public Ticket start(Long ticketId, User actor) {
        Ticket t = load(ticketId);
        requireAssignee(t, actor);
        if (t.getFirstResponseAt() == null) t.setFirstResponseAt(LocalDateTime.now());
        return transition(t, TicketStatus.IN_PROGRESS, actor, "Tiếp nhận xử lý");
    }

    @Transactional
    public Ticket hold(Long ticketId, User actor, PauseReason reason, String note) {
        Ticket t = load(ticketId);
        requireAssignee(t, actor);
        SlaPauseLog p = new SlaPauseLog();
        p.setTicket(t);
        p.setPausedAt(LocalDateTime.now());
        p.setReason(reason);
        p.setPausedBy(actor);
        pauseLogRepository.save(p);
        return transition(t, TicketStatus.ON_HOLD, actor, note);
    }

    @Transactional
    public Ticket resume(Long ticketId, User actor) {
        Ticket t = load(ticketId);
        requireAssignee(t, actor);
        SlaPauseLog p = pauseLogRepository.findFirstByTicketIdAndResumedAtIsNull(ticketId)
                .orElseThrow(() -> new BusinessRuleException("Ticket không có lần tạm dừng đang mở"));
        LocalDateTime now = LocalDateTime.now();
        p.setResumedAt(now);
        if (t.getSlaDeadline() != null)
            t.setSlaDeadline(slaService.extendAfterPause(t.getSlaDeadline(), p.getPausedAt(), now, t.getPriority()));
        return transition(t, TicketStatus.IN_PROGRESS, actor, "Tiếp tục xử lý");
    }

    /** IT Support báo "Đã xử lý xong". Ticket CHƯA đóng: chờ Employee xác nhận. */
    @Transactional
    public Ticket resolve(Long ticketId, User actor, String resolution) {
        Ticket t = load(ticketId);
        requireAssignee(t, actor);
        if (resolution == null || resolution.isBlank())
            throw new BusinessRuleException("Bắt buộc ghi cách xử lý (resolution)");

        LocalDateTime now = LocalDateTime.now();
        t.setResolution(resolution.trim());
        t.setResolvedAt(now);
        t.setIsResolutionConfirmed(null);            // bắt đầu chu kỳ chờ xác nhận mới

        // SLA đo hiệu suất IT Support: chốt tại resolved_at, KHÔNG tính thời gian chờ Employee xác nhận
        if (t.getSlaDeadline() != null) {
            boolean breached = now.isAfter(t.getSlaDeadline());
            t.setSlaStatus(breached ? SlaStatus.BREACHED : SlaStatus.MET);
            t.setOverdueMinutes(breached ? (int) Duration.between(t.getSlaDeadline(), now).toMinutes() : null);
        }
        return transition(t, TicketStatus.RESOLVED, actor, "Đã xử lý xong, chờ Employee xác nhận");
    }

    // ------------------------------------------------------------------ Employee
    /** Employee xác nhận: resolved=true => Đóng; resolved=false => Mở lại (reopen). Chỉ người tạo ticket được xác nhận. */
    @Transactional
    public Ticket confirmResolution(Long ticketId, User actor, boolean resolved) {
        Ticket t = load(ticketId);
        if (!Objects.equals(t.getRequester().getId(), actor.getId()))
            throw new BusinessRuleException("Chỉ người tạo ticket mới được xác nhận kết quả xử lý");
        if (t.getStatus() != TicketStatus.RESOLVED)
            throw new BusinessRuleException("Ticket chưa ở trạng thái Đã xử lý xong");

        LocalDateTime now = LocalDateTime.now();
        if (resolved) {
            t.setIsResolutionConfirmed(true);
            t.setResolutionConfirmedAt(now);
            t.setIsAutoClosed(false);
            return transition(t, TicketStatus.CLOSED, actor, "Employee xác nhận đã xử lý xong");
        }
        t.setReopenCount(t.getReopenCount() + 1);
        t.setIsResolutionConfirmed(null);            // ghi lịch sử "chưa xong", rồi reset cho chu kỳ sau
        t.setResolutionConfirmedAt(null);
        return transition(t, TicketStatus.IN_PROGRESS, actor, "Employee xác nhận CHƯA xong: mở lại lần " + t.getReopenCount());
    }

    /** Hệ thống tự đóng sau N ngày không phản hồi (gọi từ AutoCloseJob). */
    @Transactional
    public Ticket autoClose(Ticket t) {
        LocalDateTime now = LocalDateTime.now();
        t.setIsResolutionConfirmed(true);            // mặc định coi như đã xác nhận
        t.setResolutionConfirmedAt(now);
        t.setIsAutoClosed(true);
        return transition(t, TicketStatus.CLOSED, null, "Tự động đóng do Employee không phản hồi");
    }

    // ------------------------------------------------------------------ Hủy
    @Transactional
    public Ticket cancel(Long ticketId, User actor, String reason) {
        Ticket t = load(ticketId);
        boolean isOwnerCancellingNew = Objects.equals(t.getRequester().getId(), actor.getId())
                && t.getStatus() == TicketStatus.NEW;
        boolean isManager = actor.getRole() == Role.IT_LEADER || actor.getRole() == Role.ADMIN;
        if (!isOwnerCancellingNew && !(isManager && t.getStatus().isOpen()))
            throw new BusinessRuleException("Không có quyền hủy ticket ở trạng thái " + t.getStatus());
        return transition(t, TicketStatus.CANCELLED, actor, reason);
    }

    // ------------------------------------------------------------------ nội bộ
    /** Cửa DUY NHẤT để đổi trạng thái: kiểm tra hợp lệ + ràng buộc đóng + ghi lịch sử. */
    private Ticket transition(Ticket t, TicketStatus to, User actor, String note) {
        TicketStatus from = t.getStatus();
        if (!from.canMoveTo(to))
            throw new BusinessRuleException("Không thể chuyển " + from + " -> " + to);
        // Ràng buộc bắt buộc: không đóng nếu chưa xác nhận (DB CHECK chỉ là lớp bảo vệ thứ 2)
        if (to == TicketStatus.CLOSED && !Boolean.TRUE.equals(t.getIsResolutionConfirmed()))
            throw new BusinessRuleException("Chỉ được đóng ticket khi đã có xác nhận xử lý xong");

        t.setStatus(to);
        if (to == TicketStatus.CLOSED) t.setClosedAt(LocalDateTime.now());
        Ticket saved = ticketRepository.save(t);
        historyRepository.save(TicketStatusHistory.of(saved, from, to, actor, note));
        return saved;
    }

    private Ticket load(Long id) {
        return ticketRepository.findById(id).orElseThrow(() -> new BusinessRuleException("Ticket không tồn tại"));
    }

    private void requireRole(User u, Role... roles) {
        for (Role r : roles) if (u.getRole() == r) return;
        throw new BusinessRuleException("Không đủ quyền thực hiện thao tác này");
    }

    private void requireAssignee(Ticket t, User actor) {
        if (t.getAssignee() == null || !Objects.equals(t.getAssignee().getId(), actor.getId()))
            throw new BusinessRuleException("Chỉ IT Support được phân công mới thực hiện được thao tác này");
    }
}
