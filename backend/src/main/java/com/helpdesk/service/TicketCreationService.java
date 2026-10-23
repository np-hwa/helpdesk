package com.helpdesk.service;

import com.helpdesk.client.AiClassifier;
import com.helpdesk.client.AiClassifier.AiSuggestion;
import com.helpdesk.entity.AiClassificationLog;
import com.helpdesk.entity.Category;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatusHistory;
import com.helpdesk.entity.User;
import com.helpdesk.enums.TicketStatus;
import com.helpdesk.repository.AiClassificationLogRepository;
import com.helpdesk.repository.CategoryRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.TicketStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketCreationService {

    private final TicketRepository ticketRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final AiClassificationLogRepository aiLogRepository;
    private final CategoryRepository categoryRepository;
    private final PriorityService priorityService;
    private final AiClassifier aiClassifier;

    @Value("${helpdesk.ai.confidence-threshold:0.6}")
    private BigDecimal confidenceThreshold;

    @Transactional
    public Ticket create(User requester, String title, String description) {
        Ticket t = new Ticket();
        t.setTitle(title.trim());
        t.setDescription(description.trim());
        t.setRequester(requester);
        t.setDepartment(requester.getDepartment());     // snapshot phòng ban tại thời điểm tạo
        t.setStatus(TicketStatus.NEW);

        // 1) Gọi AI. Lỗi/timeout => KHÔNG chặn việc tạo ticket (yêu cầu phi chức năng).
        String input = title + ". " + description;
        AiSuggestion s = null;
        try {
            s = aiClassifier.classify(input);
        } catch (Exception ex) {
            log.warn("AI Service lỗi/timeout, bỏ qua gợi ý: {}", ex.getMessage());
        }

        // 2) Chỉ gán gợi ý lên ticket khi đủ tin cậy; log thì luôn lưu để đo chất lượng mô hình.
        boolean confident = s != null && s.confidence() != null
                && s.confidence().compareTo(confidenceThreshold) >= 0;
        if (confident) {
            Category cat = categoryRepository.getReferenceById(s.categoryId());
            t.setAiSuggestedCategory(cat);
            t.setAiCategoryConfidence(s.confidence());
            t.setAiSuggestedImpact(s.impact());
            t.setAiSuggestedUrgency(s.urgency());
            t.setAiSuggestedPriority(priorityService.resolve(s.impact(), s.urgency()));
        }
        Ticket saved = ticketRepository.save(t);

        historyRepository.save(TicketStatusHistory.of(saved, null, TicketStatus.NEW, requester, "Tạo ticket"));

        if (s != null) {
            AiClassificationLog l = new AiClassificationLog();
            l.setTicket(saved);
            l.setInputText(input);
            l.setSource(s.source());
            l.setModelVersion(s.modelVersion());
            l.setLatencyMs(s.latencyMs());
            l.setPredictedCategory(categoryRepository.getReferenceById(s.categoryId()));
            l.setPredictedConfidence(s.confidence());
            l.setPredictedImpact(s.impact());
            l.setPredictedUrgency(s.urgency());
            l.setPredictedPriority(priorityService.resolve(s.impact(), s.urgency()));
            aiLogRepository.save(l);
        }
        return saved;
    }
}
