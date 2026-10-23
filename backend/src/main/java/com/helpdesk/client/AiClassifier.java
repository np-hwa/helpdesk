package com.helpdesk.client;

import com.helpdesk.enums.Level;

import java.math.BigDecimal;

/**
 * Hợp đồng gọi AI Service (FastAPI). Bản cài đặt thật dùng WebClient/RestTemplate với timeout ~2 giây.
 * Ném exception khi lỗi/timeout để TicketCreationService chuyển sang fallback.
 */
public interface AiClassifier {

    AiSuggestion classify(String text);

    /**
     * @param source "AI" (PhoBERT) hoặc "RULE_BASED" (dự phòng)
     */
    record AiSuggestion(Long categoryId, BigDecimal confidence, Level impact, Level urgency,
                        String modelVersion, String source, int latencyMs) {}
}
