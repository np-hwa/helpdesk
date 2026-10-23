package com.helpdesk.job;

import com.helpdesk.entity.Ticket;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.service.TicketWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Cần @EnableScheduling trên lớp @SpringBootApplication (hoặc một @Configuration). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCloseJob {

    private final TicketRepository ticketRepository;
    private final TicketWorkflowService workflowService;

    @Value("${helpdesk.auto-close-days:3}")
    private int autoCloseDays;

    @Scheduled(cron = "0 0 * * * *")     // đầu mỗi giờ
    public void run() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(autoCloseDays);
        for (Ticket t : ticketRepository.findAutoCloseCandidates(threshold)) {
            try {
                workflowService.autoClose(t);
            } catch (Exception e) {
                log.error("Auto-close lỗi ticket {}: {}", t.getId(), e.getMessage());
            }
        }
    }
}
