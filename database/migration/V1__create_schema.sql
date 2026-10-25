-- =====================================================================
-- V1__create_schema.sql  |  Helpdesk System (MySQL 8.0.16+, InnoDB, utf8mb4)
-- Đặt tại: backend/src/main/resources/db/migration/
--
-- Schema GỘP MỚI, viết lại từ đầu theo báo cáo BC_16_V2 (mục 2.3, 16 bảng).
-- Thay thế toàn bộ V1..V6 cũ => phải tạo lại DB trống (hoặc flyway clean) rồi chạy lại.
--
-- Quy ước:
--   * Enum lưu dạng VARCHAR + CHECK (không dùng kiểu ENUM của MySQL) để
--     Hibernate @Enumerated(EnumType.STRING) + ddl-auto=validate hoạt động ổn định.
--   * Không xóa vật lý ticket (chỉ chuyển CANCELLED) => không dùng ON DELETE CASCADE.
--   * Thời gian lưu DATETIME theo múi giờ Asia/Ho_Chi_Minh (cấu hình ở JDBC/Hibernate).
--
-- So với báo cáo, schema này BỔ SUNG 1 trường mà nội dung báo cáo cần nhưng bảng chưa có:
--   [+] tickets.needs_manual_classification : "đánh dấu ticket cần phân loại thủ công"
--                                             khi AI lỗi/timeout (mục 1.2.1, 1.2.2, hình 2.51)
--       Đặt FALSE khi IT Leader xác nhận/điều chỉnh phân loại.
-- Đã BỎ bảng knowledge_base (không có trong báo cáo).
-- =====================================================================

-- 1. DEPARTMENTS -------------------------------------------------------
CREATE TABLE departments (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. USERS (4 vai trò cố định => cột role, RBAC xử lý ở Spring Security) --
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,              -- BCrypt, KHÔNG lưu plaintext
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NULL,
    department_id  BIGINT       NULL,
    role           VARCHAR(20)  NOT NULL,              -- thu hồi quyền => EMPLOYEE, không để trống
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_notify   BOOLEAN      NOT NULL DEFAULT TRUE, -- cho phép gửi thông báo qua email
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role_status (role, status),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_users_role   CHECK (role   IN ('ADMIN','IT_LEADER','IT_SUPPORT','EMPLOYEE')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. CATEGORIES --------------------------------------------------------
--    Danh mục đã gắn ticket thì không xóa, chỉ tắt is_active.
CREATE TABLE categories (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(50)  NOT NULL,
    description  VARCHAR(255) NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. PRIORITY_MATRIX (Impact x Urgency -> Priority, 9 dòng) -------------
--    score = weight(impact) + weight(urgency), weight: LOW=1, MEDIUM=2, HIGH=3
CREATE TABLE priority_matrix (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    impact    VARCHAR(10) NOT NULL,
    urgency   VARCHAR(10) NOT NULL,
    score     TINYINT     NOT NULL,
    priority  VARCHAR(10) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_priority_matrix_pair (impact, urgency),
    CONSTRAINT chk_pm_impact   CHECK (impact   IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_pm_urgency  CHECK (urgency  IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_pm_score    CHECK (score BETWEEN 2 AND 6),
    CONSTRAINT chk_pm_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. SLA_CONFIG (mỗi priority 1 dòng; đơn vị: PHÚT) --------------------
--    resolution_minutes tính theo GIỜ LÀM VIỆC (trừ is_24x7 = TRUE)
CREATE TABLE sla_config (
    priority            VARCHAR(10) NOT NULL,
    response_minutes    INT         NOT NULL,
    resolution_minutes  INT         NOT NULL,
    is_24x7             BOOLEAN     NOT NULL DEFAULT FALSE,
    warn_level1_percent TINYINT     NOT NULL DEFAULT 50,   -- cảnh báo cấp 1: IT Support
    warn_level2_percent TINYINT     NOT NULL DEFAULT 80,   -- cảnh báo cấp 2: IT Leader
    PRIMARY KEY (priority),
    CONSTRAINT chk_sla_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_sla_minutes  CHECK (response_minutes > 0 AND resolution_minutes >= response_minutes),
    CONSTRAINT chk_sla_warn     CHECK (warn_level1_percent < warn_level2_percent AND warn_level2_percent < 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. BUSINESS_HOURS_CONFIG (lịch tuần + ngày nghỉ lễ) -------------------
--    Dòng "tuần":    day_of_week = 1..7 (1 = Thứ Hai, ISO), holiday_date = NULL
--    Dòng "ngày lễ": holiday_date = 'YYYY-MM-DD', day_of_week = NULL, is_working_day = FALSE
CREATE TABLE business_hours_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    day_of_week     TINYINT      NULL,
    holiday_date    DATE         NULL,
    holiday_name    VARCHAR(100) NULL,
    is_working_day  BOOLEAN      NOT NULL DEFAULT TRUE,
    start_time      TIME         NULL,
    end_time        TIME         NULL,
    break_start     TIME         NULL,
    break_end       TIME         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bh_day (day_of_week),
    UNIQUE KEY uk_bh_holiday (holiday_date),
    CONSTRAINT chk_bh_dow      CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_bh_one_kind CHECK ((day_of_week IS NULL) <> (holiday_date IS NULL)),
    CONSTRAINT chk_bh_times    CHECK (is_working_day = FALSE OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. REFRESH_TOKENS (JWT; chỉ lưu HASH, hỗ trợ logout + rotation) ---------
--    Dọn định kỳ: DELETE FROM refresh_tokens WHERE expires_at < NOW() - INTERVAL 7 DAY;
CREATE TABLE refresh_tokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      CHAR(64)     NOT NULL,           -- SHA-256 hex
    expires_at      DATETIME     NOT NULL,
    revoked_at      DATETIME     NULL,               -- NULL = còn hiệu lực
    replaced_by_id  BIGINT       NULL,               -- token mới khi rotation
    user_agent      VARCHAR(255) NULL,
    ip_address      VARCHAR(45)  NULL,               -- IPv4 hoặc IPv6
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rt_hash (token_hash),
    KEY idx_rt_user    (user_id, revoked_at),
    KEY idx_rt_expires (expires_at),
    CONSTRAINT fk_rt_user     FOREIGN KEY (user_id)        REFERENCES users (id),
    CONSTRAINT fk_rt_replaced FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. TICKETS (bảng trung tâm) ------------------------------------------
--    Mã hiển thị TCK-xxxxxx sinh ở tầng ứng dụng từ id.
--    Giá trị CHÍNH THỨC (sau khi IT Leader xác nhận) tách riêng khỏi gợi ý AI (tiền tố ai_).
--    Phân loại 3 tầng: ticket_type -> category -> detail.
--      detail = tên nhóm chi tiết (VD 'Máy in/Thiết bị ngoại vi'); danh sách cố định,
--      do AI Service quản lý nên chỉ lưu chuỗi, không có bảng riêng.
CREATE TABLE tickets (
    id                           BIGINT        NOT NULL AUTO_INCREMENT,
    title                        VARCHAR(255)  NOT NULL,
    description                  TEXT          NOT NULL,
    requester_id                 BIGINT        NOT NULL,
    department_id                BIGINT        NULL,   -- phòng ban của người tạo tại thời điểm tạo (snapshot)
    assignee_id                  BIGINT        NULL,

    -- giá trị CHÍNH THỨC
    ticket_type                  VARCHAR(10)   NULL,   -- INCIDENT | REQUEST | OTHER
    category_id                  BIGINT        NULL,
    detail                       VARCHAR(100)  NULL,
    impact                       VARCHAR(10)   NULL,
    urgency                      VARCHAR(10)   NULL,
    priority                     VARCHAR(10)   NULL,

    -- gợi ý của AI (hoặc của rule-based khi AI lỗi)
    ai_suggested_type            VARCHAR(10)   NULL,
    ai_suggested_category_id     BIGINT        NULL,
    ai_suggested_detail          VARCHAR(100)  NULL,
    ai_category_confidence       DECIMAL(4,3)  NULL,   -- 0.000 - 1.000
    ai_suggested_impact          VARCHAR(10)   NULL,
    ai_suggested_urgency         VARCHAR(10)   NULL,
    ai_suggested_priority        VARCHAR(10)   NULL,
    is_ai_suggestion_accepted    BOOLEAN       NULL,   -- NULL = chưa xem xét / không có gợi ý
    needs_manual_classification  BOOLEAN       NOT NULL DEFAULT FALSE,   -- [+] TRUE: AI lỗi/timeout, dùng rule-based, IT Leader phải phân loại tay

    status                       VARCHAR(20)   NOT NULL DEFAULT 'NEW',

    -- mốc thời gian
    created_at                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    first_response_at            DATETIME      NULL,
    resolved_at                  DATETIME      NULL,   -- IT Support bấm "Đã xử lý xong"
    closed_at                    DATETIME      NULL,

    -- xử lý & xác nhận
    resolution                   TEXT          NULL,
    cancel_reason                VARCHAR(255)  NULL,   -- bắt buộc khi CANCELLED
    is_resolution_confirmed      BOOLEAN       NULL,   -- NULL=chờ xác nhận, TRUE=đã xong, FALSE=chưa xong
    resolution_confirmed_at      DATETIME      NULL,
    is_auto_closed               BOOLEAN       NOT NULL DEFAULT FALSE,  -- TRUE: hệ thống tự đóng sau 3 ngày không xác nhận
    reopen_count                 INT           NOT NULL DEFAULT 0,

    -- SLA
    sla_deadline                 DATETIME      NULL,   -- hạn xử lý (đã cộng bù thời gian ON_HOLD)
    sla_status                   VARCHAR(10)   NOT NULL DEFAULT 'ON_TRACK',
    overdue_minutes              INT           NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_tickets_requester  FOREIGN KEY (requester_id)             REFERENCES users (id),
    CONSTRAINT fk_tickets_department FOREIGN KEY (department_id)            REFERENCES departments (id),
    CONSTRAINT fk_tickets_assignee   FOREIGN KEY (assignee_id)              REFERENCES users (id),
    CONSTRAINT fk_tickets_category   FOREIGN KEY (category_id)              REFERENCES categories (id),
    CONSTRAINT fk_tickets_ai_cat     FOREIGN KEY (ai_suggested_category_id) REFERENCES categories (id),

    KEY idx_tickets_status          (status),
    KEY idx_tickets_assignee_status (assignee_id, status),
    KEY idx_tickets_requester       (requester_id, created_at),
    KEY idx_tickets_type            (ticket_type),
    KEY idx_tickets_category        (category_id),
    KEY idx_tickets_priority        (priority),
    KEY idx_tickets_created_at      (created_at),
    KEY idx_tickets_sla             (sla_status, sla_deadline),
    KEY idx_tickets_autoclose       (status, resolved_at),
    KEY idx_tickets_manual_review   (needs_manual_classification, status),

    -- Tìm kiếm toàn văn: MATCH(title, description) AGAINST ('máy in kẹt giấy' IN NATURAL LANGUAGE MODE)
    -- Nên đặt innodb_ft_min_token_size=2 (docker compose: --innodb-ft-min-token-size=2)
    -- TRƯỚC khi chạy migration, nếu không từ < 3 ký tự bị bỏ qua.
    FULLTEXT KEY ft_tickets_search (title, description),

    CONSTRAINT chk_tickets_status  CHECK (status IN ('NEW','ASSIGNED','IN_PROGRESS','ON_HOLD','RESOLVED','CLOSED','CANCELLED')),
    CONSTRAINT chk_tickets_type    CHECK (ticket_type IS NULL OR ticket_type IN ('INCIDENT','REQUEST','OTHER')),
    CONSTRAINT chk_tickets_ai_type CHECK (ai_suggested_type IS NULL OR ai_suggested_type IN ('INCIDENT','REQUEST','OTHER')),
    CONSTRAINT chk_tickets_impact  CHECK (impact  IS NULL OR impact  IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_tickets_urgency CHECK (urgency IS NULL OR urgency IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_tickets_prio    CHECK (priority IS NULL OR priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_tickets_ai_imp  CHECK (ai_suggested_impact  IS NULL OR ai_suggested_impact  IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_tickets_ai_urg  CHECK (ai_suggested_urgency IS NULL OR ai_suggested_urgency IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_tickets_ai_prio CHECK (ai_suggested_priority IS NULL OR ai_suggested_priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_tickets_conf    CHECK (ai_category_confidence IS NULL OR ai_category_confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_tickets_sla     CHECK (sla_status IN ('ON_TRACK','MET','BREACHED')),
    CONSTRAINT chk_tickets_reopen  CHECK (reopen_count >= 0),

    -- Ràng buộc nghiệp vụ (lớp bảo vệ thứ 2 sau tầng Service):
    -- KHÔNG được CLOSED nếu chưa có xác nhận. COALESCE để NULL không "lọt" qua CHECK.
    CONSTRAINT chk_tickets_close_needs_confirm
        CHECK (status <> 'CLOSED' OR COALESCE(is_resolution_confirmed, FALSE) = TRUE),
    -- Ngoài NEW/CANCELLED thì ticket phải có priority và loại phiếu chính thức
    CONSTRAINT chk_tickets_prio_required
        CHECK (status IN ('NEW','CANCELLED') OR priority IS NOT NULL),
    CONSTRAINT chk_tickets_type_required
        CHECK (status IN ('NEW','CANCELLED') OR ticket_type IS NOT NULL),
    -- Ticket CANCELLED bắt buộc có lý do
    CONSTRAINT chk_tickets_cancel_reason
        CHECK (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. TICKET_STATUS_HISTORY (audit trail; changed_by NULL = hệ thống) ----
CREATE TABLE ticket_status_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id    BIGINT       NOT NULL,
    from_status  VARCHAR(20)  NULL,                 -- NULL khi tạo mới
    to_status    VARCHAR(20)  NOT NULL,
    changed_by   BIGINT       NULL,
    note         VARCHAR(500) NULL,
    changed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tsh_ticket (ticket_id, changed_at),
    CONSTRAINT fk_tsh_ticket FOREIGN KEY (ticket_id)  REFERENCES tickets (id),
    CONSTRAINT fk_tsh_user   FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. TICKET_ASSIGNMENTS (lịch sử phân công / chuyển giao) --------------
--     assigned_by NULL = hệ thống; assigned_from NULL = lần phân công đầu tiên.
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

-- 11. TICKET_COMMENTS --------------------------------------------------
CREATE TABLE ticket_comments (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT   NOT NULL,
    author_id   BIGINT   NOT NULL,
    content     TEXT     NOT NULL,
    is_internal BOOLEAN  NOT NULL DEFAULT FALSE,     -- ghi chú nội bộ IT, Employee không thấy
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tc_ticket (ticket_id, created_at),
    CONSTRAINT fk_tc_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_tc_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. TICKET_ATTACHMENTS (tệp lưu trên đĩa/volume, DB chỉ lưu metadata) --
CREATE TABLE ticket_attachments (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT       NOT NULL,
    comment_id    BIGINT       NULL,                -- NULL = đính kèm lúc tạo ticket
    uploaded_by   BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,            -- tên gốc
    stored_path   VARCHAR(500) NOT NULL,            -- đường dẫn/khóa lưu thực tế (UUID)
    content_type  VARCHAR(100) NOT NULL,            -- MIME
    file_size     BIGINT       NOT NULL,            -- byte
    uploaded_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ta_ticket  (ticket_id),
    KEY idx_ta_comment (comment_id),
    CONSTRAINT fk_ta_ticket  FOREIGN KEY (ticket_id)   REFERENCES tickets (id),
    CONSTRAINT fk_ta_comment FOREIGN KEY (comment_id)  REFERENCES ticket_comments (id),
    CONSTRAINT fk_ta_user    FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT chk_ta_size   CHECK (file_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. SLA_PAUSE_LOG (mỗi lần vào trạng thái ON_HOLD) ---------------------
CREATE TABLE sla_pause_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT       NOT NULL,
    paused_at   DATETIME     NOT NULL,
    resumed_at  DATETIME     NULL,                  -- NULL = đang tạm dừng
    reason      VARCHAR(30)  NOT NULL DEFAULT 'WAITING_USER',
    paused_by   BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_spl_ticket (ticket_id, resumed_at),
    CONSTRAINT fk_spl_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id),
    CONSTRAINT fk_spl_user   FOREIGN KEY (paused_by) REFERENCES users (id),
    CONSTRAINT chk_spl_reason CHECK (reason IN ('WAITING_USER','WAITING_VENDOR')),
    CONSTRAINT chk_spl_time   CHECK (resumed_at IS NULL OR resumed_at >= paused_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. AI_CLASSIFICATION_LOGS (1 dòng / lần gọi AI; dữ liệu retrain) ------
--     source: AI = mô hình PhoBERT | RULE_BASED = fallback khi AI lỗi/timeout
--     predicted_* = dự đoán gốc; final_* = kết quả IT Leader chốt; is_*_accepted = so khớp
CREATE TABLE ai_classification_logs (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id             BIGINT        NOT NULL,
    input_text            TEXT          NOT NULL,
    source                VARCHAR(12)   NOT NULL DEFAULT 'AI',
    model_version         VARCHAR(50)   NULL,
    latency_ms            INT           NULL,

    predicted_category_id BIGINT        NULL,
    predicted_confidence  DECIMAL(4,3)  NULL,
    predicted_type        VARCHAR(10)   NULL,
    predicted_detail      VARCHAR(100)  NULL,
    predicted_impact      VARCHAR(10)   NULL,
    predicted_urgency     VARCHAR(10)   NULL,
    predicted_priority    VARCHAR(10)   NULL,

    final_category_id     BIGINT        NULL,
    final_impact          VARCHAR(10)   NULL,
    final_urgency         VARCHAR(10)   NULL,
    final_priority        VARCHAR(10)   NULL,
    final_type            VARCHAR(10)   NULL,
    final_detail          VARCHAR(100)  NULL,

    is_category_accepted  BOOLEAN       NULL,
    is_priority_accepted  BOOLEAN       NULL,
    is_type_accepted      BOOLEAN       NULL,
    is_detail_accepted    BOOLEAN       NULL,
    reviewed_by           BIGINT        NULL,
    reviewed_at           DATETIME      NULL,

    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ailog_ticket (ticket_id),
    KEY idx_ailog_model  (model_version, created_at),
    CONSTRAINT fk_ailog_ticket   FOREIGN KEY (ticket_id)             REFERENCES tickets (id),
    CONSTRAINT fk_ailog_pred_cat FOREIGN KEY (predicted_category_id) REFERENCES categories (id),
    CONSTRAINT fk_ailog_fin_cat  FOREIGN KEY (final_category_id)     REFERENCES categories (id),
    CONSTRAINT fk_ailog_reviewer FOREIGN KEY (reviewed_by)           REFERENCES users (id),
    CONSTRAINT chk_ailog_source     CHECK (source IN ('AI','RULE_BASED')),
    CONSTRAINT chk_ailog_conf       CHECK (predicted_confidence IS NULL OR predicted_confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_ailog_pred_type  CHECK (predicted_type IS NULL OR predicted_type IN ('INCIDENT','REQUEST','OTHER')),
    CONSTRAINT chk_ailog_final_type CHECK (final_type     IS NULL OR final_type     IN ('INCIDENT','REQUEST','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. NOTIFICATIONS (thông báo trong ứng dụng) ---------------------------
--     type: TICKET_CREATED, TICKET_NEEDS_REVIEW, TICKET_ASSIGNED, STATUS_CHANGED, NEW_COMMENT,
--           CONFIRM_REQUEST, TICKET_REOPENED, TICKET_CLOSED, TICKET_AUTO_CLOSED,
--           SLA_WARNING, SLA_BREACHED, REPORT_READY
--     dedup_key chống trùng khi scheduler quét SLA/báo cáo, VD 'SLA_WARN1:123', 'REPORT_READY:2026-09'
--     (NULL được phép lặp lại trong UNIQUE của MySQL)
CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    ticket_id   BIGINT       NULL,
    type        VARCHAR(30)  NOT NULL,
    title       VARCHAR(150) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    dedup_key   VARCHAR(100) NULL,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notif_dedup (user_id, dedup_key),
    KEY idx_notif_user_unread (user_id, is_read, created_at),
    CONSTRAINT fk_notif_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_notif_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. EMAIL_OUTBOX (hàng đợi gửi mail qua Gmail SMTP, mô hình Outbox) -----
--     Luồng: Service tạo notification -> nếu users.email_notify = TRUE tạo thêm 1 dòng outbox
--            (CÙNG transaction) -> job @Scheduled gửi, cập nhật SENT/FAILED.
--     App Password Gmail đặt ở biến môi trường, KHÔNG lưu trong DB.
--     Job gửi (30-60 giây, SELECT ... FOR UPDATE SKIP LOCKED):
--       SELECT * FROM email_outbox WHERE status='PENDING' AND next_attempt_at <= NOW()
--        ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED;
--     Lỗi: attempts = attempts+1, next_attempt_at = NOW() + INTERVAL (attempts*attempts*2) MINUTE;
--          attempts >= 5 -> status = 'FAILED'.
CREATE TABLE email_outbox (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    notification_id  BIGINT        NULL,               -- NULL = mail hệ thống không gắn thông báo
    user_id          BIGINT        NULL,
    to_email         VARCHAR(150)  NOT NULL,            -- snapshot địa chỉ lúc tạo
    subject          VARCHAR(255)  NOT NULL,
    body             MEDIUMTEXT    NOT NULL,            -- HTML đã render
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    attempts         TINYINT       NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error       VARCHAR(500)  NULL,
    sent_at          DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_notification (notification_id),   -- quan hệ 1 - 0..1 với notifications
    KEY idx_outbox_pick (status, next_attempt_at),
    CONSTRAINT fk_outbox_notif FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT fk_outbox_user  FOREIGN KEY (user_id)         REFERENCES users (id),
    CONSTRAINT chk_outbox_status   CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_sent     CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
