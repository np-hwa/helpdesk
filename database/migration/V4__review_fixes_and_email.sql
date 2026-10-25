-- =====================================================================
-- V4__review_fixes_and_email.sql  |  MySQL 8.0.16+, InnoDB, utf8mb4
-- Mục đích: khắc phục các điểm thiếu/lệch đã nêu trong đánh giá CSDL (Doc1)
--           + bổ sung hạ tầng gửi thông báo qua Gmail (SMTP) cho nhân viên.
-- Đặt cạnh V1, V2 trong db/migration (bắt buộc cho mọi môi trường).
-- Không sửa V1 để tránh lỗi checksum của Flyway.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. LỊCH SỬ PHÂN CÔNG / CHUYỂN TICKET (ticket_assignments)
--    Mỗi lần gán / đổi người xử lý = 1 dòng. assigned_by NULL = hệ thống.
--    assigned_from NULL = lần phân công đầu tiên.
-- ---------------------------------------------------------------------
CREATE TABLE ticket_assignments (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id      BIGINT       NOT NULL,
    assigned_from  BIGINT       NULL,
    assigned_to    BIGINT       NOT NULL,
    assigned_by    BIGINT       NULL,
    reason         VARCHAR(255) NULL,
    assigned_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tasg_ticket (ticket_id, assigned_at),
    KEY idx_tasg_to     (assigned_to, assigned_at),
    CONSTRAINT fk_tasg_ticket FOREIGN KEY (ticket_id)     REFERENCES tickets (id),
    CONSTRAINT fk_tasg_from   FOREIGN KEY (assigned_from) REFERENCES users (id),
    CONSTRAINT fk_tasg_to     FOREIGN KEY (assigned_to)   REFERENCES users (id),
    CONSTRAINT fk_tasg_by     FOREIGN KEY (assigned_by)   REFERENCES users (id),
    CONSTRAINT chk_tasg_diff  CHECK (assigned_from IS NULL OR assigned_from <> assigned_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Backfill: mỗi ticket đã có assignee nhưng chưa có lịch sử -> 1 dòng khởi tạo
INSERT INTO ticket_assignments (ticket_id, assigned_from, assigned_to, assigned_by, reason, assigned_at)
SELECT t.id, NULL, t.assignee_id, NULL, 'Backfill từ dữ liệu cũ', t.created_at
FROM tickets t
WHERE t.assignee_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. TÌM KIẾM TOÀN VĂN CHO TICKET
--    Dùng: WHERE MATCH(title, description) AGAINST ('máy in kẹt giấy' IN NATURAL LANGUAGE MODE)
--    Lưu ý: InnoDB mặc định bỏ từ < 3 ký tự (innodb_ft_min_token_size=3) => từ "in", "ra"
--    bị bỏ qua. Nên đặt innodb_ft_min_token_size=2 trong my.cnf / docker compose
--    (command: --innodb-ft-min-token-size=2) TRƯỚC khi chạy migration này.
-- ---------------------------------------------------------------------
ALTER TABLE tickets ADD FULLTEXT INDEX ft_tickets_search (title, description);

-- ---------------------------------------------------------------------
-- 3. LÝ DO HỦY TICKET (cancel_reason) nằm ngay trên tickets
-- ---------------------------------------------------------------------
ALTER TABLE tickets
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER resolution;

-- Backfill ticket đã CANCELLED từ note của dòng lịch sử cuối cùng
UPDATE tickets t
SET t.cancel_reason = COALESCE(
        (SELECT h.note FROM ticket_status_history h
          WHERE h.ticket_id = t.id AND h.to_status = 'CANCELLED'
          ORDER BY h.changed_at DESC, h.id DESC LIMIT 1),
        '(Không rõ lý do - dữ liệu cũ)')
WHERE t.status = 'CANCELLED' AND t.cancel_reason IS NULL;

-- Ticket CANCELLED bắt buộc có lý do (lớp bảo vệ thứ 2 sau tầng Service)
ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_cancel_reason
    CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL);

-- ---------------------------------------------------------------------
-- 4. REFRESH TOKEN (JWT) - lưu HASH, không lưu token gốc
--    Hỗ trợ logout (revoked_at) và rotation (replaced_by_id).
--    Job dọn dẹp định kỳ: DELETE FROM refresh_tokens WHERE expires_at < NOW() - INTERVAL 7 DAY;
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      CHAR(64)     NOT NULL,           -- SHA-256 hex của refresh token
    expires_at      DATETIME     NOT NULL,
    revoked_at      DATETIME     NULL,               -- NULL = còn hiệu lực
    replaced_by_id  BIGINT       NULL,               -- token mới khi rotation
    user_agent      VARCHAR(255) NULL,
    ip_address      VARCHAR(45)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rt_hash (token_hash),
    KEY idx_rt_user    (user_id, revoked_at),
    KEY idx_rt_expires (expires_at),
    CONSTRAINT fk_rt_user     FOREIGN KEY (user_id)        REFERENCES users (id),
    CONSTRAINT fk_rt_replaced FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. KNOWLEDGE BASE (bài viết tự xử lý)
-- ---------------------------------------------------------------------
CREATE TABLE knowledge_base (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    title         VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    category_id   BIGINT       NULL,
    author_id     BIGINT       NOT NULL,
    is_published  BOOLEAN      NOT NULL DEFAULT TRUE,
    view_count    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_category (category_id, is_published),
    FULLTEXT KEY ft_kb_search (title, content),
    CONSTRAINT fk_kb_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_kb_author   FOREIGN KEY (author_id)   REFERENCES users (id),
    CONSTRAINT chk_kb_views   CHECK (view_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 6. THÔNG BÁO QUA GMAIL
--    Luồng: Service tạo dòng notifications (chuông trong app)
--           -> nếu user bật email_notify thì tạo thêm 1 dòng email_outbox (CÙNG transaction)
--           -> job @Scheduled đọc PENDING, gửi qua smtp.gmail.com:587 (STARTTLS), cập nhật SENT/FAILED.
--    Dùng outbox để: không mất mail khi Gmail lỗi/hết quota, có retry, không chặn request người dùng.
--    Mật khẩu ứng dụng Gmail (App Password) đặt ở biến môi trường, KHÔNG lưu trong DB.
-- ---------------------------------------------------------------------

-- 6a. Cho phép từng user tắt/bật mail
ALTER TABLE users
    ADD COLUMN email_notify BOOLEAN NOT NULL DEFAULT TRUE AFTER status;

-- 6b. Chống tạo trùng thông báo khi scheduler quét SLA mỗi phút
--     dedup_key ví dụ: 'SLA_WARN1:123', 'SLA_BREACH:123', 'CONFIRM_REMIND:123:2'
--     (NULL được phép lặp lại nhiều lần trong UNIQUE của MySQL)
ALTER TABLE notifications
    ADD COLUMN dedup_key VARCHAR(100) NULL AFTER message,
    ADD UNIQUE KEY uk_notif_dedup (user_id, dedup_key);

-- 6c. Hàng đợi email
CREATE TABLE email_outbox (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    notification_id  BIGINT        NULL,               -- NULL = mail hệ thống không gắn thông báo
    user_id          BIGINT        NULL,
    to_email         VARCHAR(150)  NOT NULL,            -- snapshot địa chỉ lúc tạo
    subject          VARCHAR(255)  NOT NULL,
    body             MEDIUMTEXT    NOT NULL,            -- HTML đã render (Thymeleaf)
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    attempts         TINYINT       NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error       VARCHAR(500)  NULL,
    sent_at          DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_notification (notification_id),
    KEY idx_outbox_pick (status, next_attempt_at),
    CONSTRAINT fk_outbox_notif FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT fk_outbox_user  FOREIGN KEY (user_id)         REFERENCES users (id),
    CONSTRAINT chk_outbox_status   CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_sent     CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Job gửi mail (gợi ý, chạy mỗi 30-60 giây, dùng SELECT ... FOR UPDATE SKIP LOCKED):
--   SELECT * FROM email_outbox
--    WHERE status = 'PENDING' AND next_attempt_at <= NOW()
--    ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED;
-- Gửi lỗi: attempts = attempts + 1, next_attempt_at = NOW() + INTERVAL (attempts*attempts*2) MINUTE;
--          attempts >= 5 -> status = 'FAILED'.
