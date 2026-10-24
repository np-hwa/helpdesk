-- =====================================================================
-- V4__enhancements.sql | Bổ sung Fulltext, Refresh Token, CSAT, Assignments, KB
-- =====================================================================

-- 1. Fulltext search cho tickets
ALTER TABLE tickets ADD FULLTEXT INDEX ft_tickets_search (title, description);

-- 2. Đánh giá độ hài lòng (CSAT) tùy chọn & Lý do hủy vé
ALTER TABLE tickets
    ADD COLUMN cancel_reason VARCHAR(255) NULL AFTER resolution,
    ADD COLUMN rating TINYINT NULL AFTER is_resolution_confirmed,
    ADD COLUMN feedback VARCHAR(255) NULL AFTER rating,
    ADD CONSTRAINT chk_tickets_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5);

-- 3. Bảng LỊCH SỬ PHÂN CÔNG (Reassign & chuyển giao ticket)
CREATE TABLE ticket_assignments (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id      BIGINT       NOT NULL,
    assigned_by    BIGINT       NOT NULL,
    assigned_to    BIGINT       NOT NULL,
    assigned_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason         VARCHAR(255) NULL,
    PRIMARY KEY (id),
    KEY idx_ta_ticket (ticket_id, assigned_at),
    CONSTRAINT fk_tassign_ticket FOREIGN KEY (ticket_id)   REFERENCES tickets (id),
    CONSTRAINT fk_tassign_by     FOREIGN KEY (assigned_by) REFERENCES users (id),
    CONSTRAINT fk_tassign_to     FOREIGN KEY (assigned_to) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Bảng KNOWLEDGE_BASE (Bài viết hướng dẫn / FAQ)
CREATE TABLE knowledge_base (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    category_id  BIGINT       NOT NULL,
    author_id    BIGINT       NOT NULL,
    is_published BOOLEAN      NOT NULL DEFAULT TRUE,
    view_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_category (category_id),
    FULLTEXT INDEX ft_kb_search (title, content),
    CONSTRAINT fk_kb_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_kb_author   FOREIGN KEY (author_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Bảng REFRESH_TOKENS (Bảo mật JWT Auth & Logout)
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(255) NOT NULL,
    expires_at  DATETIME     NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token (token),
    KEY idx_rt_user (user_id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
