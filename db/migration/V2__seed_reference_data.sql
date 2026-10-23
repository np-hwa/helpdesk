-- =====================================================================
-- V2__seed_reference_data.sql  |  Dữ liệu cấu hình BẮT BUỘC (mọi môi trường)
-- =====================================================================

-- Danh mục sự cố
INSERT INTO categories (name, description) VALUES
 ('Hardware', 'Máy tính, màn hình, thiết bị ngoại vi'),
 ('Software', 'Cài đặt, lỗi phần mềm nghiệp vụ và văn phòng'),
 ('Network',  'Mạng LAN, Wi-Fi, VPN, mất kết nối'),
 ('Email',    'Hộp thư, gửi/nhận mail, lịch'),
 ('Printer',  'Máy in, máy scan'),
 ('Account',  'Cấp/khóa tài khoản, quên mật khẩu, cấp quyền'),
 ('ERP',      'Hệ thống ERP/CRM nội bộ'),
 ('Website',  'Website/cổng thông tin nội bộ');

-- Ma trận Impact x Urgency  (score = trọng số Impact + trọng số Urgency)
--   score 2,3 -> LOW | 4 -> MEDIUM | 5 -> HIGH | 6 -> CRITICAL
INSERT INTO priority_matrix (impact, urgency, score, priority) VALUES
 ('LOW',    'LOW',    2, 'LOW'),
 ('LOW',    'MEDIUM', 3, 'LOW'),
 ('LOW',    'HIGH',   4, 'MEDIUM'),
 ('MEDIUM', 'LOW',    3, 'LOW'),
 ('MEDIUM', 'MEDIUM', 4, 'MEDIUM'),
 ('MEDIUM', 'HIGH',   5, 'HIGH'),
 ('HIGH',   'LOW',    4, 'MEDIUM'),
 ('HIGH',   'MEDIUM', 5, 'HIGH'),
 ('HIGH',   'HIGH',   6, 'CRITICAL');

-- SLA (phút). Low: 24 giờ làm việc = 1440 phút ~ 3 ngày làm việc (8h/ngày)
INSERT INTO sla_config (priority, response_minutes, resolution_minutes, is_24x7) VALUES
 ('CRITICAL',  15,  120, TRUE),
 ('HIGH',      30,  240, FALSE),
 ('MEDIUM',   120,  480, FALSE),
 ('LOW',      240, 1440, FALSE);

-- Giờ làm việc: Thứ 2-6, 08:00-17:00, nghỉ trưa 12:00-13:00 (= 8 giờ/ngày)
INSERT INTO business_hours_config (day_of_week, is_working_day, start_time, end_time, break_start, break_end) VALUES
 (1, TRUE,  '08:00:00', '17:00:00', '12:00:00', '13:00:00'),
 (2, TRUE,  '08:00:00', '17:00:00', '12:00:00', '13:00:00'),
 (3, TRUE,  '08:00:00', '17:00:00', '12:00:00', '13:00:00'),
 (4, TRUE,  '08:00:00', '17:00:00', '12:00:00', '13:00:00'),
 (5, TRUE,  '08:00:00', '17:00:00', '12:00:00', '13:00:00'),
 (6, FALSE, NULL, NULL, NULL, NULL),
 (7, FALSE, NULL, NULL, NULL, NULL);

-- Ngày nghỉ lễ: thêm theo năm bằng migration mới hoặc qua màn hình Admin, ví dụ:
-- INSERT INTO business_hours_config (holiday_date, holiday_name, is_working_day) VALUES ('2027-01-01', 'Tết Dương lịch', FALSE);
