-- =====================================================================
-- V3__seed_demo_data.sql  |  Dữ liệu DEMO (chỉ dev/demo, KHÔNG dùng production)
-- Gợi ý: đặt file này ở thư mục riêng db/seed-dev và chỉ bật bằng profile dev:
--   spring.flyway.locations=classpath:db/migration,classpath:db/seed-dev
--
-- !!! THAY {BCRYPT_HASH} bằng hash BCrypt thật của mật khẩu demo (xem hướng dẫn).
-- =====================================================================

INSERT INTO departments (name, description) VALUES
 ('Phòng IT',        'Bộ phận hỗ trợ kỹ thuật'),
 ('Kế toán',         NULL),
 ('Nhân sự',         NULL),
 ('Kinh doanh',      NULL),
 ('Ban Giám đốc',    NULL);

INSERT INTO users (email, password_hash, full_name, phone, department_id, role) VALUES
 ('admin@helpdesk.local',   '{BCRYPT_HASH}', 'Quản trị viên',   '0900000001', (SELECT id FROM departments WHERE name='Phòng IT'),  'ADMIN'),
 ('leader@helpdesk.local',  '{BCRYPT_HASH}', 'Trưởng nhóm IT',  '0900000002', (SELECT id FROM departments WHERE name='Phòng IT'),  'IT_LEADER'),
 ('support1@helpdesk.local','{BCRYPT_HASH}', 'IT Support 1',    '0900000003', (SELECT id FROM departments WHERE name='Phòng IT'),  'IT_SUPPORT'),
 ('support2@helpdesk.local','{BCRYPT_HASH}', 'IT Support 2',    '0900000004', (SELECT id FROM departments WHERE name='Phòng IT'),  'IT_SUPPORT'),
 ('ketoan1@helpdesk.local', '{BCRYPT_HASH}', 'Nhân viên Kế toán','0900000005',(SELECT id FROM departments WHERE name='Kế toán'),   'EMPLOYEE'),
 ('nhansu1@helpdesk.local', '{BCRYPT_HASH}', 'Nhân viên Nhân sự','0900000006',(SELECT id FROM departments WHERE name='Nhân sự'),   'EMPLOYEE'),
 ('kinhdoanh1@helpdesk.local','{BCRYPT_HASH}','Nhân viên Kinh doanh','0900000007',(SELECT id FROM departments WHERE name='Kinh doanh'),'EMPLOYEE');

-- Ticket 1: NEW - AI vừa gợi ý, chờ IT Leader xác nhận
INSERT INTO tickets (title, description, requester_id, department_id, status,
                     ai_suggested_category_id, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority, created_at)
VALUES ('Mất kết nối mạng toàn phòng Kế toán',
        'Từ 8h sáng cả phòng không vào được Internet và ERP, sắp đến hạn nộp báo cáo.',
        (SELECT id FROM users WHERE email='ketoan1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kế toán'), 'NEW',
        (SELECT id FROM categories WHERE name='Network'), 0.930, 'HIGH', 'HIGH', 'CRITICAL', NOW());

-- Ticket 2: IN_PROGRESS - Leader chấp nhận gợi ý AI, đã phân công
INSERT INTO tickets (title, description, requester_id, department_id, assignee_id, status,
                     category_id, impact, urgency, priority,
                     ai_suggested_category_id, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     is_ai_suggestion_accepted, first_response_at, sla_deadline, created_at)
VALUES ('Máy in tầng 2 không in được',
        'Máy in HP báo lỗi kẹt giấy nhưng đã kiểm tra không thấy giấy kẹt.',
        (SELECT id FROM users WHERE email='nhansu1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Nhân sự'),
        (SELECT id FROM users WHERE email='support1@helpdesk.local'), 'IN_PROGRESS',
        (SELECT id FROM categories WHERE name='Printer'), 'MEDIUM', 'MEDIUM', 'MEDIUM',
        (SELECT id FROM categories WHERE name='Printer'), 0.880, 'MEDIUM', 'MEDIUM', 'MEDIUM',
        TRUE, NOW() - INTERVAL 1 HOUR, NOW() + INTERVAL 6 HOUR, NOW() - INTERVAL 2 HOUR);

-- Ticket 3: RESOLVED - chờ Employee xác nhận (is_resolution_confirmed = NULL)
INSERT INTO tickets (title, description, requester_id, department_id, assignee_id, status,
                     category_id, impact, urgency, priority,
                     ai_suggested_category_id, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     is_ai_suggestion_accepted, first_response_at, resolved_at, resolution,
                     sla_deadline, sla_status, created_at)
VALUES ('Quên mật khẩu email công ty',
        'Tôi không đăng nhập được email từ hôm qua, cần gấp để gửi hợp đồng.',
        (SELECT id FROM users WHERE email='kinhdoanh1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kinh doanh'),
        (SELECT id FROM users WHERE email='support2@helpdesk.local'), 'RESOLVED',
        (SELECT id FROM categories WHERE name='Account'), 'LOW', 'HIGH', 'MEDIUM',
        (SELECT id FROM categories WHERE name='Email'), 0.610, 'LOW', 'MEDIUM', 'LOW',
        FALSE, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 1 HOUR, 'Đã reset mật khẩu và hướng dẫn đăng nhập lại.',
        NOW() + INTERVAL 2 HOUR, 'MET', NOW() - INTERVAL 6 HOUR);

-- Ticket 4: CLOSED - đã được Employee xác nhận, từng reopen 1 lần
INSERT INTO tickets (title, description, requester_id, department_id, assignee_id, status,
                     category_id, impact, urgency, priority,
                     ai_suggested_category_id, ai_category_confidence,
                     ai_suggested_impact, ai_suggested_urgency, ai_suggested_priority,
                     is_ai_suggestion_accepted, first_response_at, resolved_at, resolution,
                     is_resolution_confirmed, resolution_confirmed_at, closed_at, reopen_count,
                     sla_deadline, sla_status, created_at)
VALUES ('Không cài được phần mềm kế toán',
        'Cài đặt báo thiếu .NET Framework.',
        (SELECT id FROM users WHERE email='ketoan1@helpdesk.local'),
        (SELECT id FROM departments WHERE name='Kế toán'),
        (SELECT id FROM users WHERE email='support1@helpdesk.local'), 'CLOSED',
        (SELECT id FROM categories WHERE name='Software'), 'LOW', 'MEDIUM', 'LOW',
        (SELECT id FROM categories WHERE name='Software'), 0.950, 'LOW', 'MEDIUM', 'LOW',
        TRUE, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY, 'Cài .NET Framework 4.8 và cài lại phần mềm.',
        TRUE, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 1,
        NOW() - INTERVAL 1 DAY, 'MET', NOW() - INTERVAL 4 DAY);

-- Lịch sử trạng thái mẫu cho ticket 4 (chu trình có reopen)
INSERT INTO ticket_status_history (ticket_id, from_status, to_status, changed_by, note, changed_at)
SELECT t.id, h.f, h.s, u.id, h.n, NOW() - INTERVAL h.d HOUR
FROM tickets t
JOIN users u ON u.email = 'support1@helpdesk.local'
JOIN (
  SELECT NULL AS f, 'NEW' AS s, 'Tạo ticket' AS n, 96 AS d
  UNION ALL SELECT 'NEW','ASSIGNED','Leader phân công',95
  UNION ALL SELECT 'ASSIGNED','IN_PROGRESS','Tiếp nhận',94
  UNION ALL SELECT 'IN_PROGRESS','RESOLVED','Đã xử lý xong lần 1',80
  UNION ALL SELECT 'RESOLVED','IN_PROGRESS','Employee xác nhận CHƯA xong -> mở lại',78
  UNION ALL SELECT 'IN_PROGRESS','RESOLVED','Đã xử lý xong lần 2',50
  UNION ALL SELECT 'RESOLVED','CLOSED','Employee xác nhận đã xong',48
) h
WHERE t.title = 'Không cài được phần mềm kế toán';
