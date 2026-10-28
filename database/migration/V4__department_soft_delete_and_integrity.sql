-- =====================================================================
-- V4__department_soft_delete_and_integrity.sql
-- Soft-delete phòng ban và bổ sung các ràng buộc toàn vẹn dữ liệu.
-- =====================================================================

-- Phòng ban được ngừng sử dụng bằng cách cập nhật is_active = FALSE.
-- Giữ UNIQUE(name) để phòng ban đã ngừng hoạt động có thể được kích hoạt lại,
-- tránh tạo nhiều bản ghi cùng tên và làm mơ hồ dữ liệu lịch sử.
ALTER TABLE departments
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER description;

-- Điểm và mức ưu tiên phải nhất quán với Impact/Urgency đã chọn.
ALTER TABLE priority_matrix
    ADD CONSTRAINT chk_pm_score_matches_dimensions CHECK (
        score =
            CASE impact
                WHEN 'LOW' THEN 1
                WHEN 'MEDIUM' THEN 2
                WHEN 'HIGH' THEN 3
            END
            +
            CASE urgency
                WHEN 'LOW' THEN 1
                WHEN 'MEDIUM' THEN 2
                WHEN 'HIGH' THEN 3
            END
    ),
    ADD CONSTRAINT chk_pm_priority_matches_score CHECK (
        (score BETWEEN 2 AND 3 AND priority = 'LOW')
        OR (score = 4 AND priority = 'MEDIUM')
        OR (score = 5 AND priority = 'HIGH')
        OR (score = 6 AND priority = 'CRITICAL')
    );

-- Ngày làm việc phải có giờ hợp lệ; giờ nghỉ phải đủ cặp và nằm trong ca.
-- Ngày không làm việc/ngày lễ không nhận thời gian làm việc hoặc nghỉ.
ALTER TABLE business_hours_config
    DROP CHECK chk_bh_times,
    ADD CONSTRAINT chk_bh_schedule CHECK (
        (
            day_of_week IS NOT NULL
            AND (
                (
                    is_working_day = FALSE
                    AND start_time IS NULL
                    AND end_time IS NULL
                    AND break_start IS NULL
                    AND break_end IS NULL
                )
                OR (
                    is_working_day = TRUE
                    AND start_time IS NOT NULL
                    AND end_time IS NOT NULL
                    AND start_time < end_time
                    AND (
                        (break_start IS NULL AND break_end IS NULL)
                        OR (
                            break_start IS NOT NULL
                            AND break_end IS NOT NULL
                            AND break_start < break_end
                            AND break_start >= start_time
                            AND break_end <= end_time
                        )
                    )
                )
            )
        )
        OR (
            holiday_date IS NOT NULL
            AND day_of_week IS NULL
            AND is_working_day = FALSE
            AND start_time IS NULL
            AND end_time IS NULL
            AND break_start IS NULL
            AND break_end IS NULL
        )
    );

-- Một attachment gắn với comment phải thuộc cùng ticket.
ALTER TABLE ticket_comments
    ADD UNIQUE KEY uk_tc_ticket_comment (ticket_id, id);

ALTER TABLE ticket_attachments
    DROP FOREIGN KEY fk_ta_comment,
    ADD KEY idx_ta_ticket_comment (ticket_id, comment_id),
    ADD CONSTRAINT fk_ta_comment_ticket
        FOREIGN KEY (ticket_id, comment_id)
        REFERENCES ticket_comments (ticket_id, id);

-- Các mốc hoàn tất phải có mặt khi ticket đã đạt trạng thái tương ứng.
ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_resolved_at
        CHECK (status NOT IN ('RESOLVED', 'CLOSED') OR resolved_at IS NOT NULL),
    ADD CONSTRAINT chk_tickets_closed_at
        CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL),
    ADD CONSTRAINT chk_tickets_cancel_reason_nonblank
        CHECK (status <> 'CANCELLED' OR CHAR_LENGTH(TRIM(cancel_reason)) > 0);
