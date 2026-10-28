-- =====================================================================
-- V2__seed_reference_data.sql  |  Dữ liệu cấu hình BẮT BUỘC (mọi môi trường)
-- Danh mục lấy theo báo cáo BC_16_V2 (mục 1.2.1 và bảng 2.2).
-- =====================================================================

-- Danh mục sự cố (7 nhóm theo báo cáo)
INSERT INTO categories (name, description) VALUES
 ('Hardware',                'Máy tính, màn hình, máy in, thiết bị ngoại vi'),
 ('Software',                'Cài đặt, lỗi phần mềm nghiệp vụ và văn phòng'),
 ('Network & Connectivity',  'Mạng LAN, Wi-Fi, VPN, mất kết nối'),
 ('Email & Collaboration',   'Hộp thư, gửi/nhận mail, lịch, công cụ cộng tác'),
 ('User Services',           'Hỗ trợ người dùng, yêu cầu dịch vụ, hướng dẫn sử dụng'),
 ('Access',                  'Cấp/khóa tài khoản, quên mật khẩu, cấp quyền truy cập'),
 ('General',                 'Các yêu cầu chung chưa thuộc nhóm nào');

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

-- SLA (phút). LOW: 1440 phút = 24 giờ làm việc = 3 ngày làm việc (8h/ngày).
-- warn_level1/2_percent dùng mặc định 50 / 80 (cảnh báo IT Support / IT Leader).
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
