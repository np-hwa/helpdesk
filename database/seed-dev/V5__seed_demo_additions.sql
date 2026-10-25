-- =====================================================================
-- V5__seed_demo_additions.sql  |  DỮ LIỆU DEMO (chỉ dev/demo, KHÔNG dùng production)
-- Đặt cùng thư mục với V3: db/seed-dev  (spring.flyway.locations=classpath:db/migration,classpath:db/seed-dev)
-- Phải chạy SAU V3 và V4.
-- Mật khẩu demo của MỌI tài khoản: Demo@12345
-- =====================================================================

-- 1. Thay placeholder trong V3 bằng BCrypt hash thật ($2a$10, Spring Security đọc được)
UPDATE users
SET password_hash = '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm'
WHERE password_hash = '{BCRYPT_HASH}';

-- 2. Lịch sử phân công: V4 đã backfill 1 dòng/ticket. Dựng tình huống "chuyển ticket" cho ticket máy in:
--    Leader gán cho Support 2 trước, sau đó chuyển sang Support 1.
UPDATE ticket_assignments a
JOIN tickets t ON t.id = a.ticket_id
SET a.assigned_by = (SELECT id FROM users WHERE email = 'leader@helpdesk.local')
WHERE a.reason = 'Backfill từ dữ liệu cũ';

INSERT INTO ticket_assignments (ticket_id, assigned_from, assigned_to, assigned_by, reason, assigned_at)
SELECT t.id, NULL, (SELECT id FROM users WHERE email = 'support2@helpdesk.local'),
       (SELECT id FROM users WHERE email = 'leader@helpdesk.local'),
       'Phân công lần đầu', t.created_at + INTERVAL 10 MINUTE
FROM tickets t WHERE t.title = 'Máy in tầng 2 không in được';

UPDATE ticket_assignments a
JOIN tickets t ON t.id = a.ticket_id
SET a.assigned_from = (SELECT id FROM users WHERE email = 'support2@helpdesk.local'),
    a.reason        = 'Chuyển cho người có chuyên môn máy in',
    a.assigned_at   = t.created_at + INTERVAL 40 MINUTE
WHERE t.title = 'Máy in tầng 2 không in được' AND a.assigned_from IS NULL
  AND a.assigned_to = (SELECT id FROM users WHERE email = 'support1@helpdesk.local');

-- 3. Ticket bị hủy (minh họa cancel_reason)
INSERT INTO tickets (title, description, requester_id, department_id, status, cancel_reason, created_at)
VALUES ('Xin cấp thêm màn hình thứ 2',
        'Cần màn hình thứ hai để làm việc.',
        (SELECT id FROM users WHERE email = 'kinhdoanh1@helpdesk.local'),
        (SELECT id FROM departments WHERE name = 'Kinh doanh'),
        'CANCELLED', 'Trùng với yêu cầu mua sắm thiết bị đã gửi phòng Hành chính', NOW() - INTERVAL 1 DAY);

INSERT INTO ticket_status_history (ticket_id, from_status, to_status, changed_by, note, changed_at)
SELECT t.id, f.fs, f.ts, u.id, f.n, NOW() - INTERVAL f.h HOUR
FROM tickets t
JOIN users u ON u.email = 'kinhdoanh1@helpdesk.local'
JOIN (SELECT NULL AS fs, 'NEW' AS ts, 'Tạo ticket' AS n, 25 AS h
      UNION ALL SELECT 'NEW', 'CANCELLED', 'Trùng với yêu cầu mua sắm thiết bị đã gửi phòng Hành chính', 24) f
WHERE t.title = 'Xin cấp thêm màn hình thứ 2';

-- 4. Lịch sử trạng thái cho ticket 1-3 (V3 chỉ có cho ticket 4)
INSERT INTO ticket_status_history (ticket_id, from_status, to_status, changed_by, note, changed_at)
SELECT t.id, NULL, 'NEW', t.requester_id, 'Tạo ticket', t.created_at
FROM tickets t
WHERE t.title IN ('Mất kết nối mạng toàn phòng Kế toán', 'Máy in tầng 2 không in được', 'Quên mật khẩu email công ty');

-- 5. Log AI cho các ticket demo (phục vụ dashboard độ chính xác AI)
INSERT INTO ai_classification_logs
    (ticket_id, input_text, source, model_version, latency_ms,
     predicted_category_id, predicted_confidence, predicted_impact, predicted_urgency, predicted_priority,
     final_category_id, final_impact, final_urgency, final_priority,
     is_category_accepted, is_priority_accepted, reviewed_by, reviewed_at)
SELECT t.id, CONCAT(t.title, '. ', t.description), 'AI', 'tfidf-lr-v1', 120,
       t.ai_suggested_category_id, t.ai_category_confidence,
       t.ai_suggested_impact, t.ai_suggested_urgency, t.ai_suggested_priority,
       t.category_id, t.impact, t.urgency, t.priority,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE t.category_id = t.ai_suggested_category_id END,
       CASE WHEN t.priority    IS NULL THEN NULL ELSE t.priority    = t.ai_suggested_priority    END,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE (SELECT id FROM users WHERE email = 'leader@helpdesk.local') END,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE t.created_at + INTERVAL 15 MINUTE END
FROM tickets t
WHERE t.ai_suggested_category_id IS NOT NULL;

-- 6. Knowledge base mẫu
INSERT INTO knowledge_base (title, content, category_id, author_id) VALUES
 ('Máy in báo kẹt giấy nhưng không thấy giấy',
  '1) Tắt máy in, rút điện 30 giây. 2) Mở khay sau, kiểm tra cảm biến giấy. 3) Bật lại và in trang thử. Nếu vẫn lỗi, tạo ticket kèm ảnh màn hình báo lỗi.',
  (SELECT id FROM categories WHERE name = 'Printer'), (SELECT id FROM users WHERE email = 'support1@helpdesk.local')),
 ('Quên mật khẩu email công ty',
  'Vào trang cấp lại mật khẩu hoặc tạo ticket loại Account. IT sẽ reset và gửi hướng dẫn đăng nhập lại.',
  (SELECT id FROM categories WHERE name = 'Account'), (SELECT id FROM users WHERE email = 'support2@helpdesk.local'));

-- 7. Thông báo + hàng đợi email mẫu: IT Leader nhận mail khi có ticket mới (ticket 1)
INSERT INTO notifications (user_id, ticket_id, type, title, message, dedup_key)
SELECT u.id, t.id, 'TICKET_CREATED', 'Có ticket mới cần phân loại',
       CONCAT('Ticket #', t.id, ': ', t.title), CONCAT('TICKET_CREATED:', t.id)
FROM tickets t JOIN users u ON u.email = 'leader@helpdesk.local'
WHERE t.title = 'Mất kết nối mạng toàn phòng Kế toán';

INSERT INTO email_outbox (notification_id, user_id, to_email, subject, body)
SELECT n.id, u.id, u.email, CONCAT('[Helpdesk] ', n.title),
       CONCAT('<p>Xin chào ', u.full_name, ',</p><p>', n.message, '</p>')
FROM notifications n JOIN users u ON u.id = n.user_id
WHERE n.dedup_key LIKE 'TICKET_CREATED:%' AND u.email_notify = TRUE;
