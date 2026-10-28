-- =====================================================================
-- V3__seed_demo_data.sql  |  DỮ LIỆU DEMO (chỉ dev/demo, KHÔNG dùng production)
-- Gộp V3 + V5 cũ, cập nhật theo schema mới (ticket_type, detail, needs_manual_classification)
-- và bỏ knowledge_base.
-- Đặt ở thư mục riêng db/seed-dev, chỉ bật bằng profile dev:
--   spring.flyway.locations=classpath:db/migration,classpath:db/seed-dev
-- Mật khẩu demo của MỌI tài khoản: Demo@12345  (BCrypt $2a$10, Spring Security đọc được)
-- Giá trị "detail" bên dưới chỉ là ví dụ minh họa; danh sách thật do AI Service quản lý.
-- =====================================================================

-- 1. Phòng ban ---------------------------------------------------------
INSERT INTO departments (name, description) VALUES
 ('Phòng IT',     'Bộ phận hỗ trợ kỹ thuật'),
 ('Kế toán',      NULL),
 ('Nhân sự',      NULL),
 ('Kinh doanh',   NULL),
 ('Ban Giám đốc', NULL);

-- 2. Người dùng --------------------------------------------------------
INSERT INTO users (email, password_hash, full_name, phone, department_id, role) VALUES
 ('admin@helpdesk.local',      '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'Quản trị viên',        '0900000001', (SELECT id FROM departments WHERE name='Phòng IT'),   'ADMIN'),
 ('leader@helpdesk.local',     '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'Trưởng nhóm IT',       '0900000002', (SELECT id FROM departments WHERE name='Phòng IT'),   'IT_LEADER'),
 ('support1@helpdesk.local',   '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'IT Support 1',         '0900000003', (SELECT id FROM departments WHERE name='Phòng IT'),   'IT_SUPPORT'),
 ('support2@helpdesk.local',   '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'IT Support 2',         '0900000004', (SELECT id FROM departments WHERE name='Phòng IT'),   'IT_SUPPORT'),
 ('ketoan1@helpdesk.local',    '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'Nhân viên Kế toán',    '0900000005', (SELECT id FROM departments WHERE name='Kế toán'),    'EMPLOYEE'),
 ('nhansu1@helpdesk.local',    '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'Nhân viên Nhân sự',    '0900000006', (SELECT id FROM departments WHERE name='Nhân sự'),    'EMPLOYEE'),
 ('kinhdoanh1@helpdesk.local', '$2a$10$.eEpOq6k5HptfoX5bHoTEOCPWm2UXkRKpVA/e9EpafrPjldvQ/1jm', 'Nhân viên Kinh doanh', '0900000007', (SELECT id FROM departments WHERE name='Kinh doanh'), 'EMPLOYEE');

-- 3. Tickets -----------------------------------------------------------

-- Ticket 1: NEW - AI vừa gợi ý, chờ IT Leader xác nhận (chưa có giá trị chính thức)
INSERT INTO tickets (title, description, requester_id, department_id, status,
                     ai_suggested_type, ai_suggested_category_id, ai_suggested_detail, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority, created_at)
VALUES ('Mất kết nối mạng toàn phòng Kế toán',
        'Từ 8h sáng cả phòng không vào được Internet và ERP, sắp đến hạn nộp báo cáo.',
        (SELECT id FROM users WHERE email='ketoan1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kế toán'), 'NEW',
        'INCIDENT', (SELECT id FROM categories WHERE name='Network & Connectivity'), 'Mạng LAN/Wi-Fi', 0.930,
        'HIGH', 'HIGH', 'CRITICAL', NOW());

-- Ticket 2: IN_PROGRESS - Leader chấp nhận gợi ý AI, đã phân công (chuyển từ Support 2 sang Support 1)
INSERT INTO tickets (title, description, requester_id, department_id, assignee_id, status,
                     ticket_type, category_id, detail, impact, urgency, priority,
                     ai_suggested_type, ai_suggested_category_id, ai_suggested_detail, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     is_ai_suggestion_accepted, first_response_at, sla_deadline, created_at)
VALUES ('Máy in tầng 2 không in được',
        'Máy in HP báo lỗi kẹt giấy nhưng đã kiểm tra không thấy giấy kẹt.',
        (SELECT id FROM users WHERE email='nhansu1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Nhân sự'),
        (SELECT id FROM users WHERE email='support1@helpdesk.local'), 'IN_PROGRESS',
        'INCIDENT', (SELECT id FROM categories WHERE name='Hardware'), 'Máy in/Thiết bị ngoại vi', 'MEDIUM', 'MEDIUM', 'MEDIUM',
        'INCIDENT', (SELECT id FROM categories WHERE name='Hardware'), 'Máy in/Thiết bị ngoại vi', 0.880,
        'MEDIUM', 'MEDIUM', 'MEDIUM',
        TRUE, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 6 HOUR, NOW() - INTERVAL 2 HOUR);

-- Ticket 3: RESOLVED - chờ Employee xác nhận; Leader ĐÃ ĐIỀU CHỈNH gợi ý AI
--           (loại phiếu, danh mục, nhóm chi tiết, priority đều khác gợi ý)
INSERT INTO tickets (title, description, requester_id, department_id, assignee_id, status,
                     ticket_type, category_id, detail, impact, urgency, priority,
                     ai_suggested_type, ai_suggested_category_id, ai_suggested_detail, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     is_ai_suggestion_accepted, first_response_at, resolved_at, resolution,
                     sla_deadline, sla_status, created_at)
VALUES ('Quên mật khẩu email công ty',
        'Tôi không đăng nhập được email từ hôm qua, cần gấp để gửi hợp đồng.',
        (SELECT id FROM users WHERE email='kinhdoanh1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kinh doanh'),
        (SELECT id FROM users WHERE email='support2@helpdesk.local'), 'RESOLVED',
        'REQUEST',  (SELECT id FROM categories WHERE name='Access'), 'Tài khoản/Mật khẩu', 'LOW', 'HIGH', 'MEDIUM',
        'INCIDENT', (SELECT id FROM categories WHERE name='Email & Collaboration'), 'Hộp thư/Email', 0.610,
        'LOW', 'MEDIUM', 'LOW',
        FALSE, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 1 HOUR, 'Đã reset mật khẩu và hướng dẫn đăng nhập lại.',
        NOW() + INTERVAL 2 HOUR, 'MET', NOW() - INTERVAL 6 HOUR);

-- Ticket 4: CLOSED - Employee đã xác nhận, từng mở lại 1 lần
INSERT INTO tickets (title, description, requester_id, department_id, assignee_id, status,
                     ticket_type, category_id, detail, impact, urgency, priority,
                     ai_suggested_type, ai_suggested_category_id, ai_suggested_detail, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     is_ai_suggestion_accepted, first_response_at, resolved_at, resolution,
                     is_resolution_confirmed, resolution_confirmed_at, closed_at, reopen_count,
                     sla_deadline, sla_status, created_at)
VALUES ('Không cài được phần mềm kế toán',
        'Cài đặt báo thiếu .NET Framework.',
        (SELECT id FROM users WHERE email='ketoan1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kế toán'),
        (SELECT id FROM users WHERE email='support1@helpdesk.local'), 'CLOSED',
        'INCIDENT', (SELECT id FROM categories WHERE name='Software'), 'Cài đặt/Lỗi phần mềm', 'LOW', 'MEDIUM', 'LOW',
        'INCIDENT', (SELECT id FROM categories WHERE name='Software'), 'Cài đặt/Lỗi phần mềm', 0.950,
        'LOW', 'MEDIUM', 'LOW',
        TRUE, NOW() - INTERVAL 94 HOUR, NOW() - INTERVAL 50 HOUR, 'Cài .NET Framework 4.8 và cài lại phần mềm.',
        TRUE, NOW() - INTERVAL 48 HOUR, NOW() - INTERVAL 48 HOUR, 1,
        NOW() - INTERVAL 24 HOUR, 'MET', NOW() - INTERVAL 96 HOUR);

-- Ticket 5: CANCELLED - minh họa cancel_reason (chưa phân loại nên chưa có ticket_type chính thức)
INSERT INTO tickets (title, description, requester_id, department_id, status, cancel_reason, created_at)
VALUES ('Xin cấp thêm màn hình thứ 2',
        'Cần màn hình thứ hai để làm việc.',
        (SELECT id FROM users WHERE email='kinhdoanh1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kinh doanh'),
        'CANCELLED', 'Trùng với yêu cầu mua sắm thiết bị đã gửi phòng Hành chính', NOW() - INTERVAL 25 HOUR);

-- Ticket 6: NEW - AI Service lỗi/timeout => rule-based gợi ý, needs_manual_classification = TRUE
INSERT INTO tickets (title, description, requester_id, department_id, status,
                     ai_suggested_type, ai_suggested_category_id, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     needs_manual_classification, created_at)
VALUES ('Không truy cập được cổng thông tin nội bộ',
        'Vào trang cổng thông tin nội bộ báo lỗi trắng trang từ sáng nay.',
        (SELECT id FROM users WHERE email='nhansu1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Nhân sự'), 'NEW',
        'INCIDENT', (SELECT id FROM categories WHERE name='General'), 0.300,
        'MEDIUM', 'MEDIUM', 'MEDIUM',
        TRUE, NOW() - INTERVAL 30 MINUTE);

-- 4. Lịch sử trạng thái (changed_at = created_at + offset phút) ---------
INSERT INTO ticket_status_history (ticket_id, from_status, to_status, changed_by, note, changed_at)
SELECT t.id, h.f, h.s, u.id, h.n, t.created_at + INTERVAL h.m MINUTE
FROM (
  SELECT 'Mất kết nối mạng toàn phòng Kế toán' AS title, NULL AS f, 'NEW' AS s, 'ketoan1@helpdesk.local' AS who, 'Tạo ticket' AS n, 0 AS m
  UNION ALL SELECT 'Máy in tầng 2 không in được', NULL, 'NEW', 'nhansu1@helpdesk.local', 'Tạo ticket', 0
  UNION ALL SELECT 'Máy in tầng 2 không in được', 'NEW', 'ASSIGNED', 'leader@helpdesk.local', 'Leader phân công', 10
  UNION ALL SELECT 'Máy in tầng 2 không in được', 'ASSIGNED', 'IN_PROGRESS', 'support1@helpdesk.local', 'Tiếp nhận', 60
  UNION ALL SELECT 'Quên mật khẩu email công ty', NULL, 'NEW', 'kinhdoanh1@helpdesk.local', 'Tạo ticket', 0
  UNION ALL SELECT 'Quên mật khẩu email công ty', 'NEW', 'ASSIGNED', 'leader@helpdesk.local', 'Leader phân công', 10
  UNION ALL SELECT 'Quên mật khẩu email công ty', 'ASSIGNED', 'IN_PROGRESS', 'support2@helpdesk.local', 'Tiếp nhận', 60
  UNION ALL SELECT 'Quên mật khẩu email công ty', 'IN_PROGRESS', 'RESOLVED', 'support2@helpdesk.local', 'Đã reset mật khẩu', 300
  UNION ALL SELECT 'Không cài được phần mềm kế toán', NULL, 'NEW', 'ketoan1@helpdesk.local', 'Tạo ticket', 0
  UNION ALL SELECT 'Không cài được phần mềm kế toán', 'NEW', 'ASSIGNED', 'leader@helpdesk.local', 'Leader phân công', 60
  UNION ALL SELECT 'Không cài được phần mềm kế toán', 'ASSIGNED', 'IN_PROGRESS', 'support1@helpdesk.local', 'Tiếp nhận', 120
  UNION ALL SELECT 'Không cài được phần mềm kế toán', 'IN_PROGRESS', 'RESOLVED', 'support1@helpdesk.local', 'Đã xử lý xong lần 1', 960
  UNION ALL SELECT 'Không cài được phần mềm kế toán', 'RESOLVED', 'IN_PROGRESS', 'ketoan1@helpdesk.local', 'Employee xác nhận CHƯA xong -> mở lại', 1080
  UNION ALL SELECT 'Không cài được phần mềm kế toán', 'IN_PROGRESS', 'RESOLVED', 'support1@helpdesk.local', 'Đã xử lý xong lần 2', 2760
  UNION ALL SELECT 'Không cài được phần mềm kế toán', 'RESOLVED', 'CLOSED', 'ketoan1@helpdesk.local', 'Employee xác nhận đã xong', 2880
  UNION ALL SELECT 'Xin cấp thêm màn hình thứ 2', NULL, 'NEW', 'kinhdoanh1@helpdesk.local', 'Tạo ticket', 0
  UNION ALL SELECT 'Xin cấp thêm màn hình thứ 2', 'NEW', 'CANCELLED', 'kinhdoanh1@helpdesk.local', 'Trùng với yêu cầu mua sắm thiết bị đã gửi phòng Hành chính', 60
  UNION ALL SELECT 'Không truy cập được cổng thông tin nội bộ', NULL, 'NEW', 'nhansu1@helpdesk.local', 'Tạo ticket', 0
) h
JOIN tickets t ON t.title = h.title
JOIN users   u ON u.email = h.who;

-- 5. Lịch sử phân công (ticket máy in: Leader gán Support 2 rồi chuyển sang Support 1) ------
INSERT INTO ticket_assignments (ticket_id, assigned_from, assigned_to, assigned_by, reason, assigned_at)
SELECT t.id, uf.id, ut.id, ul.id, a.reason, t.created_at + INTERVAL a.m MINUTE
FROM (
  SELECT 'Máy in tầng 2 không in được' AS title, NULL AS from_mail, 'support2@helpdesk.local' AS to_mail,
         'Phân công lần đầu' AS reason, 10 AS m
  UNION ALL SELECT 'Máy in tầng 2 không in được', 'support2@helpdesk.local', 'support1@helpdesk.local',
         'Chuyển cho người có chuyên môn máy in', 40
  UNION ALL SELECT 'Quên mật khẩu email công ty', NULL, 'support2@helpdesk.local', 'Phân công lần đầu', 10
  UNION ALL SELECT 'Không cài được phần mềm kế toán', NULL, 'support1@helpdesk.local', 'Phân công lần đầu', 60
) a
JOIN tickets t       ON t.title  = a.title
LEFT JOIN users uf   ON uf.email = a.from_mail
JOIN users ut        ON ut.email = a.to_mail
JOIN users ul        ON ul.email = 'leader@helpdesk.local';

-- 6. Log AI (phục vụ dashboard độ chính xác AI) -------------------------
--    Ticket 6 dùng nguồn RULE_BASED (AI lỗi/timeout). Ticket chưa được Leader xem xét => final_*, is_*_accepted = NULL.
INSERT INTO ai_classification_logs
    (ticket_id, input_text, source, model_version, latency_ms,
     predicted_category_id, predicted_confidence, predicted_type, predicted_detail,
     predicted_impact, predicted_urgency, predicted_priority,
     final_category_id, final_impact, final_urgency, final_priority, final_type, final_detail,
     is_category_accepted, is_priority_accepted, is_type_accepted, is_detail_accepted,
     reviewed_by, reviewed_at)
SELECT t.id, CONCAT(t.title, '. ', t.description),
       IF(t.needs_manual_classification, 'RULE_BASED', 'AI'),
       IF(t.needs_manual_classification, NULL, 'phobert-finetuned-v1'),
       IF(t.needs_manual_classification, 2000, 120),
       t.ai_suggested_category_id, t.ai_category_confidence, t.ai_suggested_type, t.ai_suggested_detail,
       t.ai_suggested_impact, t.ai_suggested_urgency, t.ai_suggested_priority,
       t.category_id, t.impact, t.urgency, t.priority, t.ticket_type, t.detail,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE t.category_id = t.ai_suggested_category_id END,
       CASE WHEN t.priority    IS NULL THEN NULL ELSE t.priority    = t.ai_suggested_priority    END,
       CASE WHEN t.ticket_type IS NULL THEN NULL ELSE t.ticket_type = t.ai_suggested_type        END,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE t.detail <=> t.ai_suggested_detail         END,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE (SELECT id FROM users WHERE email = 'leader@helpdesk.local') END,
       CASE WHEN t.category_id IS NULL THEN NULL ELSE t.created_at + INTERVAL 15 MINUTE END
FROM tickets t
WHERE t.ai_suggested_category_id IS NOT NULL;

-- 7. Thông báo + hàng đợi email mẫu: IT Leader nhận thông báo cho ticket đang chờ xem xét ----
INSERT INTO notifications (user_id, ticket_id, type, title, message, dedup_key)
SELECT u.id, t.id, 'TICKET_NEEDS_REVIEW',
       IF(t.needs_manual_classification, 'Ticket cần phân loại thủ công', 'Có ticket mới cần xem xét gợi ý AI'),
       CONCAT('Ticket #', t.id, ': ', t.title),
       CONCAT('TICKET_NEEDS_REVIEW:', t.id)
FROM tickets t JOIN users u ON u.email = 'leader@helpdesk.local'
WHERE t.status = 'NEW' AND t.ai_suggested_category_id IS NOT NULL;

INSERT INTO email_outbox (notification_id, user_id, to_email, subject, body)
SELECT n.id, u.id, u.email, CONCAT('[Helpdesk] ', n.title),
       CONCAT('<p>Xin chào ', u.full_name, ',</p><p>', n.message, '</p>')
FROM notifications n JOIN users u ON u.id = n.user_id
WHERE n.dedup_key LIKE 'TICKET_NEEDS_REVIEW:%' AND u.email_notify = TRUE;
