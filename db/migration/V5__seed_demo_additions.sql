-- =====================================================================
-- V5__seed_demo_additions.sql  |  Helpdesk System (DEV ONLY)
-- Bổ sung dữ liệu demo cho các bảng thêm ở V4.
-- Mật khẩu các tài khoản demo: Helpdesk@2024
-- BCrypt hash ($2a$12$...): tạo từ Spring Security BCryptPasswordEncoder(12)
-- =====================================================================

-- Cập nhật password_hash bằng BCrypt thật cho 7 tài khoản demo
-- (password gốc: Helpdesk@2024)
-- Hash dưới đây phải được tạo thực tế bằng PasswordEncoder trong code
-- Placeholder – thay bằng hash thật khi chạy lần đầu

UPDATE users SET password_hash = '$2a$12$LgBSQVvFhc7.YiNxCPpqveE5LqRDjCkEZPuMp4MScLlxe.4nHalGG'
WHERE email IN (
    'admin@company.com',
    'itleader@company.com',
    'support1@company.com',
    'support2@company.com',
    'emp1@company.com',
    'emp2@company.com',
    'emp3@company.com'
);

-- email_notify mặc định TRUE cho tất cả user
UPDATE users SET email_notify = TRUE WHERE email_notify IS NULL;

-- Dữ liệu mẫu knowledge_base
INSERT INTO knowledge_base (title, content, category_id, author_id, is_published, view_count)
SELECT
    'Hướng dẫn đặt lại mật khẩu Windows',
    '<h2>Cách đặt lại mật khẩu Windows</h2><p>1. Nhấn Ctrl+Alt+Delete...</p>',
    c.id,
    u.id,
    TRUE,
    42
FROM categories c, users u
WHERE c.name = 'Account' AND u.email = 'support1@company.com'
LIMIT 1;

INSERT INTO knowledge_base (title, content, category_id, author_id, is_published, view_count)
SELECT
    'Khắc phục lỗi không kết nối WiFi',
    '<h2>Không kết nối được WiFi</h2><p>1. Kiểm tra trạng thái WiFi adapter...</p>',
    c.id,
    u.id,
    TRUE,
    28
FROM categories c, users u
WHERE c.name = 'Network' AND u.email = 'support1@company.com'
LIMIT 1;
