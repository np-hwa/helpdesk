-- =====================================================================
-- kpi_queries.sql | Truy vấn KPI cho Dashboard/Báo cáo (không phải migration)
-- Đổi khoảng thời gian bằng @from / @to
-- =====================================================================
SET @from = '2026-01-01', @to = '2026-12-31 23:59:59';

-- 1. Số ticket theo trạng thái
SELECT status, COUNT(*) AS total FROM tickets GROUP BY status;

-- 2. Số ticket theo tháng x danh mục
SELECT DATE_FORMAT(t.created_at,'%Y-%m') AS month, c.name AS category, COUNT(*) AS total
FROM tickets t LEFT JOIN categories c ON c.id = t.category_id
WHERE t.created_at BETWEEN @from AND @to
GROUP BY month, c.name ORDER BY month, total DESC;

-- 3. First Response Time & Resolution Time trung bình (phút) theo priority
SELECT priority,
       ROUND(AVG(TIMESTAMPDIFF(MINUTE, created_at, first_response_at)),1) AS avg_frt_min,
       ROUND(AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at)),1)       AS avg_rt_min
FROM tickets
WHERE resolved_at IS NOT NULL AND created_at BETWEEN @from AND @to
GROUP BY priority;

-- 4. SLA Compliance (%)
SELECT ROUND(100 * SUM(sla_status='MET') / NULLIF(SUM(sla_status IN ('MET','BREACHED')),0), 2) AS sla_compliance_pct
FROM tickets WHERE created_at BETWEEN @from AND @to;

-- 5. Reopen Rate (%) = ticket từng bị mở lại / ticket từng ở trạng thái "Đã xử lý xong"
SELECT ROUND(100 * SUM(reopen_count > 0) / NULLIF(SUM(resolved_at IS NOT NULL),0), 2) AS reopen_rate_pct
FROM tickets WHERE created_at BETWEEN @from AND @to;

-- 6. Resolution Confirmation Rate (%) = đóng do Employee chủ động xác nhận / tổng ticket đã đóng
SELECT ROUND(100 * SUM(is_auto_closed = FALSE) / NULLIF(COUNT(*),0), 2) AS manual_confirm_pct,
       ROUND(100 * SUM(is_auto_closed = TRUE)  / NULLIF(COUNT(*),0), 2) AS auto_close_pct
FROM tickets WHERE status = 'CLOSED' AND created_at BETWEEN @from AND @to;

-- 7. AI Suggestion Acceptance Rate (%) - toàn ticket (giữ nguyên cả category + priority)
SELECT ROUND(100 * SUM(is_ai_suggestion_accepted = TRUE) / NULLIF(COUNT(*),0), 2) AS ai_acceptance_pct
FROM tickets
WHERE ai_suggested_category_id IS NOT NULL AND is_ai_suggestion_accepted IS NOT NULL
  AND created_at BETWEEN @from AND @to;

-- 8. Acceptance tách riêng category/priority theo phiên bản mô hình (chỉ tính lần gọi AI thật)
SELECT model_version,
       COUNT(*) AS reviewed,
       ROUND(100 * AVG(is_category_accepted), 2) AS category_acceptance_pct,
       ROUND(100 * AVG(is_priority_accepted), 2) AS priority_acceptance_pct,
       ROUND(AVG(predicted_confidence), 3)       AS avg_confidence,
       ROUND(AVG(latency_ms))                    AS avg_latency_ms
FROM ai_classification_logs
WHERE source = 'AI' AND reviewed_at IS NOT NULL
GROUP BY model_version;

-- 9. Confusion (AI dự đoán vs người chốt) theo category
SELECT pc.name AS predicted, fc.name AS final, COUNT(*) AS total
FROM ai_classification_logs l
JOIN categories pc ON pc.id = l.predicted_category_id
JOIN categories fc ON fc.id = l.final_category_id
WHERE l.source = 'AI'
GROUP BY pc.name, fc.name ORDER BY total DESC;

-- 10. Ticket sắp/đã vi phạm SLA (chưa xử lý xong): dùng cho job cảnh báo
--     (chưa trừ giờ nghỉ/ON_HOLD - phần này tính ở tầng Service bằng business_hours_config + sla_pause_log)
SELECT id, title, priority, assignee_id, sla_deadline,
       TIMESTAMPDIFF(MINUTE, NOW(), sla_deadline) AS minutes_left
FROM tickets
WHERE status IN ('ASSIGNED','IN_PROGRESS') AND sla_deadline IS NOT NULL
ORDER BY sla_deadline;

-- 11. Ứng viên auto-close: RESOLVED quá N ngày mà Employee chưa phản hồi (N = 3)
SELECT id, resolved_at FROM tickets
WHERE status = 'RESOLVED' AND is_resolution_confirmed IS NULL
  AND resolved_at < NOW() - INTERVAL 3 DAY;

-- 12. Export dữ liệu retrain PhoBERT (nhãn cuối do con người xác nhận)
SELECT input_text, fc.name AS label_category, final_impact, final_urgency, final_priority
FROM ai_classification_logs l
JOIN categories fc ON fc.id = l.final_category_id
WHERE l.reviewed_at IS NOT NULL;
