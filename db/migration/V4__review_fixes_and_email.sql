-- =====================================================================
-- V4__review_fixes_and_email.sql  |  Helpdesk System
-- Bổ sung: bảng refresh_tokens, ticket_assignments, email_outbox,
--           knowledge_base, và một số cột vào bảng hiện có.
-- =====================================================================

-- ── email_notify vào users ─────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN email_notify BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT 'Bật/tắt nhận thông báo qua Gmail';

-- ── cancel_reason vào tickets ──────────────────────────────────────
ALTER TABLE tickets
    ADD COLUMN cancel_reason VARCHAR(255) NULL
        COMMENT 'Lý do hủy; bắt buộc khi status = CANCELLED';

-- ── dedup_key vào notifications ────────────────────────────────────
ALTER TABLE notifications
    ADD COLUMN dedup_key VARCHAR(100) NULL
        COMMENT 'Khóa chống tạo trùng thông báo (VD: SLA_WARN1:123)',
    ADD UNIQUE KEY uk_notif_dedup (user_id, dedup_key);

-- ── FULLTEXT index cho tickets ─────────────────────────────────────
ALTER TABLE tickets
    ADD FULLTEXT INDEX ft_tickets_search (title, description);

-- ── REFRESH_TOKENS ─────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      CHAR(64)     NOT NULL COMMENT 'SHA-256 hex của token gốc',
    expires_at      DATETIME     NOT NULL,
    revoked_at      DATETIME     NULL     COMMENT 'NULL = còn hiệu lực',
    replaced_by_id  BIGINT       NULL     COMMENT 'Token kế tiếp sau rotation',
    user_agent      VARCHAR(500) NULL,
    ip_address      VARCHAR(45)  NULL     COMMENT 'IPv4 hoặc IPv6',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rt_token_hash (token_hash),
    KEY idx_rt_user_id    (user_id),
    KEY idx_rt_expires_at (expires_at),
    CONSTRAINT fk_rt_user         FOREIGN KEY (user_id)        REFERENCES users (id),
    CONSTRAINT fk_rt_replaced_by  FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Lưu refresh token (chỉ lưu SHA-256 hash, không lưu token gốc)';

-- ── TICKET_ASSIGNMENTS ─────────────────────────────────────────────
CREATE TABLE ticket_assignments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT       NOT NULL,
    assigned_from BIGINT       NULL     COMMENT 'NULL ở lần phân công đầu tiên',
    assigned_to   BIGINT       NOT NULL,
    assigned_by   BIGINT       NULL     COMMENT 'NULL = hệ thống',
    reason        VARCHAR(255) NULL,
    assigned_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ta_ticket (ticket_id),
    CONSTRAINT fk_ta_ticket        FOREIGN KEY (ticket_id)     REFERENCES tickets (id),
    CONSTRAINT fk_ta_assigned_from FOREIGN KEY (assigned_from) REFERENCES users (id),
    CONSTRAINT fk_ta_assigned_to   FOREIGN KEY (assigned_to)   REFERENCES users (id),
    CONSTRAINT fk_ta_assigned_by   FOREIGN KEY (assigned_by)   REFERENCES users (id),
    CONSTRAINT chk_ta_no_self_assign
        CHECK (assigned_from IS NULL OR assigned_from <> assigned_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── KNOWLEDGE_BASE ─────────────────────────────────────────────────
CREATE TABLE knowledge_base (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    category_id  BIGINT       NULL,
    author_id    BIGINT       NOT NULL,
    is_published BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_category (category_id),
    FULLTEXT INDEX ft_kb_search (title, content),
    CONSTRAINT fk_kb_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_kb_author   FOREIGN KEY (author_id)   REFERENCES users (id),
    CONSTRAINT chk_kb_views   CHECK (view_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── EMAIL_OUTBOX ───────────────────────────────────────────────────
CREATE TABLE email_outbox (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    notification_id BIGINT        NOT NULL UNIQUE,
    to_email        VARCHAR(150)  NOT NULL COMMENT 'Snapshot địa chỉ lúc tạo',
    subject         VARCHAR(255)  NOT NULL,
    body            MEDIUMTEXT    NOT NULL COMMENT 'HTML đã dựng sẵn',
    status          VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    attempts        TINYINT       NOT NULL DEFAULT 0,
    next_attempt_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         DATETIME      NULL,
    last_error      TEXT          NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_email_outbox_status (status, next_attempt_at),
    CONSTRAINT fk_eo_notification FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT chk_eo_status      CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT chk_eo_sent        CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Outbox pattern: ghi email vào DB, job riêng gửi qua Gmail';

-- ── CHECK bổ sung cho tickets ──────────────────────────────────────
ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_cancel_reason
        CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL);
