# Phân tích cơ sở dữ liệu – Helpdesk System

Tài liệu này giải thích **từng bảng, từng cột, từng khóa** trong CSDL của hệ thống Helpdesk (MySQL 8.0, InnoDB, utf8mb4): bảng được tạo như thế nào, và **vì sao chọn** kiểu dữ liệu, khóa, ràng buộc đó.

- Schema gốc: `V1__create_schema.sql` (13 bảng). Bổ sung: `V4__review_fixes_and_email.sql` (4 bảng + một số cột).
- Tổng cộng **17 bảng**. Trong Docker sẽ có thêm bảng `flyway_schema_history` do Flyway tự tạo (không thuộc thiết kế).

---

## 1. Quy ước chung (áp dụng cho mọi bảng)

| Quy ước | Cách làm | Vì sao chọn |
|---|---|---|
| Khóa chính | `id BIGINT AUTO_INCREMENT` | Khóa đại diện (surrogate key) ngắn, không đổi, JOIN nhanh. `BIGINT` ↔ `Long` trong Java, không lo tràn số như `INT` khi dữ liệu lớn |
| Enum | `VARCHAR` + `CHECK (... IN (...))` thay vì kiểu `ENUM` của MySQL | `@Enumerated(EnumType.STRING)` của Hibernate làm việc ổn với `VARCHAR`; thêm giá trị mới chỉ cần sửa CHECK, không phải `ALTER` kiểu cột; vẫn được DB chặn giá trị sai |
| Thời gian | `DATETIME` (múi giờ Asia/Ho_Chi_Minh) | Đồ án chạy một múi giờ, `DATETIME` không bị đổi theo timezone như `TIMESTAMP` và không giới hạn năm 2038 |
| Boolean | `BOOLEAN` (thực chất `TINYINT(1)`) | Đủ cho cờ đúng/sai; riêng cờ ba trạng thái (chưa xác nhận / đã xong / chưa xong) dùng `BOOLEAN NULL` |
| Không xóa vật lý | Không dùng `ON DELETE CASCADE`; ticket chỉ chuyển sang `CANCELLED` | Giữ nguyên lịch sử và số liệu KPI (Reopen Rate, Auto-close Rate). Xóa nhầm 1 user/ticket không kéo theo mất dây chuyền |
| Snapshot | Lưu lại giá trị tại thời điểm xảy ra (VD `tickets.department_id`, `email_outbox.to_email`) | Sau này user đổi phòng ban/đổi email, báo cáo cũ vẫn đúng |
| Mã hiển thị | `TCK-000123` sinh ở tầng ứng dụng từ `id` | Không cần thêm cột, không bao giờ lệch với `id` |
| Tên ràng buộc | `pk_`/`uk_`/`idx_`/`fk_`/`chk_` + tên bảng | Đọc thông báo lỗi biết ngay ràng buộc nào vi phạm |

### Các loại khóa/ràng buộc dùng trong dự án

| Loại | Dùng để | Ví dụ |
|---|---|---|
| `PRIMARY KEY` | Định danh duy nhất một dòng | `tickets.id` |
| `UNIQUE KEY` | Chống trùng dữ liệu nghiệp vụ | `users.email`, `categories.name` |
| `FOREIGN KEY` | Giữ toàn vẹn tham chiếu (không thể trỏ tới dòng không tồn tại) | `tickets.requester_id → users.id` |
| `KEY` (index) | Tăng tốc truy vấn hay dùng | `idx_tickets_assignee_status` |
| `CHECK` | Chặn giá trị sai và luật nghiệp vụ ngay tại DB | `chk_tickets_close_needs_confirm` |

---

## 2. Thứ tự tạo bảng (và lý do)

Bảng chứa khóa ngoại phải tạo **sau** bảng mà nó tham chiếu. Vì vậy `V1` tạo theo thứ tự:

```
Tầng 1 (không phụ thuộc):  departments, categories, priority_matrix, sla_config, business_hours_config
Tầng 2:                    users                          (cần departments)
Tầng 3:                    tickets                        (cần users, departments, categories)
Tầng 4:                    ticket_status_history, ticket_comments, notifications,
                           ai_classification_logs, sla_pause_log      (cần tickets, users)
Tầng 5:                    ticket_attachments             (cần ticket_comments)
```
`V4` bổ sung: `ticket_assignments`, `refresh_tokens`, `knowledge_base` (cần tickets/users/categories) và `email_outbox` (cần notifications).

Dữ liệu cấu hình (V2) phải nạp **trước** dữ liệu demo (V3) vì demo dùng `categories` và `departments`.

---

## 3. Nhóm bảng danh mục & cấu hình

### 3.1 `departments` – phòng ban
```sql
CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
| Cột | Vì sao |
|---|---|
| `id` | PK đại diện, để `users`/`tickets` tham chiếu |
| `name` `NOT NULL` + `UNIQUE` | Hai phòng không được trùng tên; là thông tin bắt buộc. Dùng UNIQUE để dữ liệu demo tra cứu `WHERE name='Kế toán'` luôn ra đúng một dòng |
| `description` `NULL` | Mô tả không bắt buộc |
| `created_at` | Dấu thời gian tạo, mặc định do DB điền |

### 3.2 `categories` – danh mục sự cố
Cùng khuôn với `departments`, thêm `is_active BOOLEAN DEFAULT TRUE`.
- **Vì sao `is_active`:** category đã gắn vào ticket cũ thì không được xóa; muốn ngừng dùng chỉ cần tắt cờ. Đúng nguyên tắc "không xóa vật lý".
- **Vì sao `name VARCHAR(50) UNIQUE`:** tên ngắn (Hardware, Network...), không trùng.

### 3.3 `priority_matrix` – ma trận Impact × Urgency
| Cột | Kiểu | Vì sao |
|---|---|---|
| `id` | BIGINT PK | Khóa đại diện, thống nhất với các bảng khác |
| `impact`, `urgency` | VARCHAR(10), CHECK ∈ {LOW, MEDIUM, HIGH} | Hai chiều của ma trận ITIL |
| `score` | TINYINT, CHECK 2..6 | Tổng trọng số (LOW=1, MEDIUM=2, HIGH=3) nên nhỏ nhất 1+1=2, lớn nhất 3+3=6; `TINYINT` là đủ |
| `priority` | VARCHAR(10), CHECK ∈ {LOW, MEDIUM, HIGH, CRITICAL} | Kết quả tra ma trận |

**Khóa:** `UNIQUE (impact, urgency)` – mỗi cặp chỉ có đúng một kết quả, nên 3×3 = 9 dòng, không thể nhập trùng hay mâu thuẫn. Đây là **khóa nghiệp vụ** thật sự, còn `id` chỉ là khóa kỹ thuật.

### 3.4 `sla_config` – chính sách SLA
```sql
CREATE TABLE sla_config (
    priority            VARCHAR(10) NOT NULL,
    response_minutes    INT         NOT NULL,
    resolution_minutes  INT         NOT NULL,
    is_24x7             BOOLEAN     NOT NULL DEFAULT FALSE,
    warn_level1_percent TINYINT     NOT NULL DEFAULT 50,
    warn_level2_percent TINYINT     NOT NULL DEFAULT 80,
    PRIMARY KEY (priority),
    ...
);
```
| Điểm đáng chú ý | Vì sao |
|---|---|
| **PK là `priority`** (khóa tự nhiên, không có `id`) | Mỗi mức ưu tiên đúng 1 dòng cấu hình; `tickets.priority` tra thẳng sang đây không cần JOIN qua `id` |
| Đơn vị **phút** | Thống nhất một đơn vị, tính toán bằng số nguyên, không phải đổi giờ/ngày |
| `is_24x7` | CRITICAL chạy 24x7 (không trừ giờ nghỉ); các mức khác tính theo giờ làm việc |
| `warn_level1_percent` (50) / `warn_level2_percent` (80) | Cảnh báo IT Support ở 50% thời hạn, IT Leader ở 80% – chỉnh được không cần sửa code |
| `CHECK resolution_minutes >= response_minutes` | Thời gian xử lý không thể ngắn hơn thời gian phản hồi |
| `CHECK warn1 < warn2 < 100` | Cảnh báo cấp 1 phải đến trước cấp 2 và trước khi quá hạn |

### 3.5 `business_hours_config` – giờ làm việc & ngày lễ
Một bảng chứa **hai loại dòng**:
- Dòng "tuần": `day_of_week` 1..7 (ISO, 1 = Thứ Hai), `holiday_date = NULL`.
- Dòng "ngày lễ": `holiday_date` có giá trị, `day_of_week = NULL`, `is_working_day = FALSE`.

| Ràng buộc | Vì sao |
|---|---|
| `UNIQUE (day_of_week)` và `UNIQUE (holiday_date)` | Mỗi thứ/mỗi ngày lễ chỉ một dòng. MySQL cho phép nhiều `NULL` trong UNIQUE nên hai loại dòng không cản nhau |
| `CHECK ((day_of_week IS NULL) <> (holiday_date IS NULL))` | Mỗi dòng phải thuộc **đúng một** loại, không được vừa tuần vừa lễ, cũng không được trống cả hai |
| `CHECK is_working_day = FALSE OR (start_time < end_time ...)` | Ngày làm việc bắt buộc có giờ bắt đầu < giờ kết thúc |
| `break_start`, `break_end` `NULL` | Nghỉ trưa 12:00–13:00; tính SLA phải trừ khoảng này |

**Vì sao gộp một bảng thay vì hai:** cùng là "lịch làm việc", khi tính SLA chỉ cần một truy vấn; ngày lễ ghi đè lịch tuần.

---

## 4. Nhóm bảng người dùng

### 4.1 `users`
```sql
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NULL,
    department_id BIGINT       NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role_status (role, status),
    CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_users_role   CHECK (role   IN ('ADMIN','IT_LEADER','IT_SUPPORT','EMPLOYEE')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED'))
);
-- V4 bổ sung: email_notify BOOLEAN NOT NULL DEFAULT TRUE
```
| Cột | Vì sao |
|---|---|
| `email` UNIQUE | Là tên đăng nhập; không được trùng. Collation `utf8mb4_0900_ai_ci` không phân biệt hoa/thường nên `A@x.com` và `a@x.com` được coi là một |
| `password_hash VARCHAR(255)` | Chỉ lưu **hash BCrypt** (60 ký tự), tuyệt đối không lưu mật khẩu gốc. 255 chừa chỗ nếu đổi thuật toán |
| `role` + CHECK 4 giá trị | Chỉ có 4 vai trò **cố định**, nên một cột là đủ; không cần bảng `roles`/`permissions`. Phân quyền chi tiết do Spring Security (RBAC) xử lý |
| `status` ACTIVE / INACTIVE / LOCKED | Vô hiệu hóa hoặc khóa tài khoản thay vì xóa (giữ lịch sử ticket) |
| `department_id` `NULL` + FK | Có thể chưa gán phòng ban; FK đảm bảo phòng ban tồn tại |
| `updated_at ... ON UPDATE` | DB tự cập nhật khi sửa dòng, code không phải nhớ |
| `email_notify` (V4) | Cho từng người bật/tắt nhận thông báo qua Gmail |
| Index `(role, status)` | Truy vấn "lấy danh sách IT Support đang ACTIVE" để phân công rất hay dùng |

### 4.2 `refresh_tokens` (V4) – phục vụ đăng nhập JWT
| Cột | Vì sao |
|---|---|
| `token_hash CHAR(64)` **UNIQUE** | Lưu **SHA-256 (64 ký tự hex)** của token, không lưu token gốc: nếu DB lộ thì kẻ xấu không dùng được. `CHAR` vì độ dài cố định; UNIQUE để tra token nhanh và không trùng |
| `expires_at` | Hạn dùng; có index để job dọn token hết hạn |
| `revoked_at` `NULL` | NULL = còn hiệu lực; có giá trị = đã đăng xuất/thu hồi |
| `replaced_by_id` FK **tự tham chiếu** | Khi làm mới (rotation), token cũ trỏ sang token mới; phát hiện được việc dùng lại token đã bị thay thế (dấu hiệu bị đánh cắp) |
| `user_agent`, `ip_address VARCHAR(45)` | Dấu vết thiết bị; 45 ký tự đủ cho cả IPv6 |

---

## 5. Bảng trung tâm `tickets`

Đây là bảng lớn nhất (≈ 30 cột), chia thành các nhóm:

| Nhóm | Cột | Vì sao thiết kế như vậy |
|---|---|---|
| Nội dung | `title VARCHAR(255)`, `description TEXT` | Tiêu đề ngắn để hiển thị danh sách; mô tả dài nên dùng `TEXT` |
| Con người | `requester_id` (NOT NULL), `assignee_id` (NULL), `department_id` (NULL) | Ticket luôn có người gửi, chưa chắc đã có người xử lý. `department_id` là **snapshot** phòng ban của người gửi lúc tạo, để báo cáo theo phòng ban không đổi khi họ chuyển phòng |
| Giá trị **chính thức** | `category_id`, `impact`, `urgency`, `priority` (đều NULL) | Chỉ có sau khi **IT Leader xác nhận**; lúc mới tạo thì còn trống |
| Gợi ý của **AI** | `ai_suggested_category_id`, `ai_category_confidence DECIMAL(4,3)`, `ai_suggested_impact/urgency/priority`, `is_ai_suggestion_accepted` | **Tách riêng** khỏi giá trị chính thức để so sánh AI đúng/sai. `DECIMAL(4,3)` biểu diễn chính xác 0.000–1.000 (không sai số như `FLOAT`). `is_ai_suggestion_accepted`: NULL = chưa xem xét, TRUE/FALSE = chấp nhận/sửa |
| Trạng thái | `status VARCHAR(20) DEFAULT 'NEW'` | 7 trạng thái: NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, CANCELLED |
| Mốc thời gian | `created_at`, `updated_at`, `first_response_at`, `resolved_at`, `closed_at` | Mỗi mốc phục vụ một KPI: thời gian phản hồi đầu, thời gian xử lý, thời gian đóng. Mốc chưa xảy ra để NULL |
| Xử lý & xác nhận | `resolution TEXT`, `is_resolution_confirmed`, `resolution_confirmed_at`, `is_auto_closed`, `reopen_count` | Cơ chế đóng ticket **do người gửi xác nhận**. `is_resolution_confirmed` ba trạng thái: NULL = chờ, TRUE = xong, FALSE = chưa xong (mở lại). `is_auto_closed` = hệ thống tự đóng sau N ngày không phản hồi. `reopen_count` đếm số lần mở lại → chỉ số Reopen Rate |
| SLA | `sla_deadline`, `sla_status`, `overdue_minutes` | Hạn chót, tình trạng (ON_TRACK / MET / BREACHED), số phút trễ |
| Hủy (V4) | `cancel_reason VARCHAR(255)` | Lý do hủy, bắt buộc khi `CANCELLED` |

### 5.1 Khóa ngoại của `tickets`
| FK | Trỏ tới | Vì sao |
|---|---|---|
| `requester_id` | `users.id` | Người tạo ticket |
| `assignee_id` | `users.id` | Người đang xử lý (cùng bảng `users`, hai vai trò khác nhau nên cần hai cột) |
| `department_id` | `departments.id` | Phòng ban của người gửi |
| `category_id` | `categories.id` | Danh mục chính thức |
| `ai_suggested_category_id` | `categories.id` | Danh mục AI gợi ý (cũng phải hợp lệ) |

Mặc định `ON DELETE RESTRICT`: không cho xóa user/phòng ban/category đang được ticket dùng.

### 5.2 Index của `tickets` – mỗi index phục vụ một truy vấn cụ thể
| Index | Truy vấn nó phục vụ |
|---|---|
| `idx_tickets_status (status)` | Lọc danh sách theo trạng thái; lọc ticket NEW cần phân loại |
| `idx_tickets_assignee_status (assignee_id, status)` | "Việc của tôi": ticket của một IT Support đang mở. Cột `assignee_id` đứng trước vì luôn có điều kiện bằng |
| `idx_tickets_requester (requester_id, created_at)` | "Ticket tôi đã gửi", sắp xếp theo thời gian mới nhất |
| `idx_tickets_category`, `idx_tickets_priority` | Thống kê/lọc theo danh mục và mức ưu tiên |
| `idx_tickets_created_at` | Báo cáo theo khoảng ngày |
| `idx_tickets_sla (sla_status, sla_deadline)` | Job quét SLA mỗi phút: lấy ticket ON_TRACK sắp đến hạn |
| `idx_tickets_autoclose (status, resolved_at)` | Job tự đóng: ticket RESOLVED quá N ngày chưa xác nhận |
| `ft_tickets_search (title, description)` FULLTEXT (V4) | Tìm kiếm toàn văn, dùng `MATCH ... AGAINST` thay cho `LIKE '%...%'` (không dùng được index) |

> Đánh index quá nhiều làm chậm thao tác ghi, nên chỉ tạo index cho truy vấn thực sự có trong hệ thống.

### 5.3 Ràng buộc CHECK của `tickets` (DB tự chặn dữ liệu sai)
| CHECK | Ý nghĩa |
|---|---|
| `chk_tickets_status`, `_impact`, `_urgency`, `_prio`, `_ai_*`, `_sla` | Chỉ nhận đúng tập giá trị cho phép |
| `chk_tickets_conf` | `ai_category_confidence` trong [0, 1] |
| `chk_tickets_reopen` | `reopen_count >= 0` |
| **`chk_tickets_close_needs_confirm`** | `status <> 'CLOSED' OR COALESCE(is_resolution_confirmed, FALSE) = TRUE` → **không thể đóng ticket khi chưa có xác nhận**. Dùng `COALESCE` vì trong SQL, `NULL = TRUE` cho kết quả `NULL` (không phải FALSE) và CHECK sẽ để lọt; `COALESCE` biến NULL thành FALSE nên bị chặn đúng |
| **`chk_tickets_prio_required`** | Trừ NEW và CANCELLED, mọi ticket đang xử lý phải có `priority` |
| `chk_tickets_cancel_reason` (V4) | `CANCELLED` bắt buộc có `cancel_reason` |

Đây là **lớp bảo vệ thứ hai**: tầng Service cũng kiểm tra, nhưng DB vẫn chặn nếu có lỗi code hoặc ai đó sửa dữ liệu trực tiếp.

---

## 6. Các bảng con của `tickets`

### 6.1 `ticket_status_history` – nhật ký trạng thái
| Cột | Vì sao |
|---|---|
| `from_status` `NULL` | NULL khi tạo mới (chưa có trạng thái trước) |
| `to_status` NOT NULL | Luôn có trạng thái đích |
| `changed_by` `NULL` | NULL = do **hệ thống** thay đổi (tự đóng, cảnh báo SLA) |
| `note VARCHAR(500)` | Lý do/ghi chú (VD: "Employee xác nhận CHƯA xong → mở lại") |
| Index `(ticket_id, changed_at)` | Xem dòng thời gian của một ticket theo thứ tự |

**Vì sao cần bảng riêng:** `tickets.status` chỉ giữ trạng thái *hiện tại*. Muốn biết ticket đã đi qua những bước nào, ai đổi, lúc nào (kiểm toán, tính thời gian ở từng bước, đếm reopen) thì phải có lịch sử.

### 6.2 `ticket_assignments` (V4) – lịch sử phân công
| Cột | Vì sao |
|---|---|
| `assigned_from` `NULL` | NULL ở lần phân công đầu tiên |
| `assigned_to` NOT NULL | Người nhận việc |
| `assigned_by` `NULL` | NULL = hệ thống/backfill |
| `reason` | Lý do chuyển ("chuyển cho người chuyên máy in") |
| `CHECK (assigned_from IS NULL OR assigned_from <> assigned_to)` | Không thể "chuyển" ticket cho chính người đang giữ |

**Vì sao cần:** `tickets.assignee_id` chỉ lưu người *hiện tại*. Khi chuyển ticket thì mất dấu ai từng xử lý, nên không đếm được số lần chuyển hay đánh giá tải công việc.

### 6.3 `ticket_comments`
| Cột | Vì sao |
|---|---|
| `author_id` NOT NULL | Ai viết |
| `content TEXT` | Nội dung có thể dài |
| `is_internal` DEFAULT FALSE | TRUE = ghi chú nội bộ IT, **Employee không được thấy**. Dùng một bảng với cờ thay vì hai bảng để dòng thời gian trao đổi luôn đúng thứ tự |
| Index `(ticket_id, created_at)` | Tải toàn bộ bình luận một ticket theo thứ tự thời gian |

### 6.4 `ticket_attachments` – chỉ lưu **metadata**
| Cột | Vì sao |
|---|---|
| `file_name` | Tên gốc để hiển thị khi tải về |
| `stored_path VARCHAR(500)` | Đường dẫn/khóa lưu thực (dùng UUID, tránh trùng tên và lộ tên gốc) |
| `content_type`, `file_size BIGINT` | Kiểm tra loại và dung lượng; `CHECK file_size > 0` |
| `comment_id` `NULL` | NULL = đính kèm lúc tạo ticket; có giá trị = đính kèm trong một bình luận |

**Vì sao không lưu file trong DB (BLOB):** làm DB phình to, chậm sao lưu. File để trên đĩa/volume, DB chỉ giữ đường dẫn.

### 6.5 `sla_pause_log` – tạm dừng SLA
| Cột | Vì sao |
|---|---|
| `paused_at` NOT NULL, `resumed_at` NULL | NULL = đang tạm dừng. Mỗi lần vào `ON_HOLD` là một dòng |
| `reason` ∈ {WAITING_USER, WAITING_VENDOR} | Phân biệt chờ người dùng hay chờ nhà cung cấp |
| `CHECK resumed_at IS NULL OR resumed_at >= paused_at` | Không thể tiếp tục trước khi tạm dừng |

**Vì sao cần:** thời gian chờ người dùng không được tính vào SLA của IT; khi tính hạn chót phải cộng bù tổng thời gian đã tạm dừng.

---

## 7. Nhóm bảng AI và thông báo

### 7.1 `ai_classification_logs` – mỗi lần gọi AI một dòng
| Nhóm cột | Vì sao |
|---|---|
| `input_text TEXT` | Văn bản đã đưa cho AI (tiêu đề + mô tả); cần để huấn luyện lại sau này |
| `source` ∈ {AI, RULE_BASED} | Phân biệt kết quả từ mô hình hay từ luật dự phòng khi AI lỗi/quá thời gian |
| `model_version`, `latency_ms` | So sánh các phiên bản mô hình và đo tốc độ |
| `predicted_*` | Điều AI **dự đoán** (category, confidence, impact, urgency, priority) |
| `final_*` | Điều IT Leader **chốt cuối cùng** |
| `is_category_accepted`, `is_priority_accepted` | So sánh `predicted_*` và `final_*`: AI đúng hay sai → tính độ chính xác |
| `reviewed_by`, `reviewed_at` | Ai xác nhận, lúc nào (NULL = chưa xem) |

**Vì sao tách khỏi `tickets`:** một ticket có thể được phân loại nhiều lần (AI, rồi luật, rồi người sửa); log giữ đủ từng lần. Đây cũng là **bộ dữ liệu có nhãn** (`input_text` + `final_category_id`) để retrain mô hình. Hai FK tới `categories` (`predicted_category_id`, `final_category_id`) vì hai giá trị này có thể khác nhau.

### 7.2 `notifications` – thông báo trong ứng dụng
| Cột | Vì sao |
|---|---|
| `user_id` NOT NULL | Người nhận |
| `ticket_id` NULL | Ticket liên quan (có thể không gắn ticket) |
| `type VARCHAR(30)` | TICKET_CREATED, TICKET_ASSIGNED, STATUS_CHANGED, NEW_COMMENT, CONFIRM_REQUEST, TICKET_REOPENED, TICKET_CLOSED, SLA_WARNING, SLA_BREACHED. Không đặt CHECK cứng để dễ thêm loại mới |
| `title`, `message` | Nội dung hiển thị |
| `is_read` | Đã đọc chưa |
| `dedup_key` (V4) + `UNIQUE (user_id, dedup_key)` | Chống tạo trùng thông báo khi job quét SLA chạy mỗi phút. VD khóa `SLA_WARN1:123` chỉ chèn được **một lần** cho mỗi người. NULL được phép lặp |
| Index `(user_id, is_read, created_at)` | Truy vấn "thông báo chưa đọc của tôi, mới nhất trước" |

### 7.3 `email_outbox` (V4) – hàng đợi gửi mail qua Gmail
Mô hình **Outbox**: ghi mail cần gửi vào DB cùng transaction với sự kiện, một job riêng gửi sau.

| Cột | Vì sao |
|---|---|
| `notification_id` + `UNIQUE` | Mỗi thông báo tối đa **một** email, tránh gửi trùng |
| `to_email` (snapshot) | Địa chỉ lúc tạo, đổi email sau này không ảnh hưởng mail đã xếp hàng |
| `subject`, `body MEDIUMTEXT` | Nội dung HTML đã dựng sẵn; `MEDIUMTEXT` cho nội dung dài |
| `status` ∈ {PENDING, SENT, FAILED} | Trạng thái gửi |
| `attempts`, `next_attempt_at`, `last_error` | Thử lại với thời gian chờ tăng dần; ghi lỗi để điều tra; quá 5 lần thì FAILED |
| `sent_at` + `CHECK (status <> 'SENT' OR sent_at IS NOT NULL)` | Đã gửi thì phải có thời điểm gửi |
| Index `(status, next_attempt_at)` | Job lấy nhanh các mail PENDING đến hạn gửi |

**Vì sao không gửi mail thẳng trong request:** Gmail chậm hoặc hết hạn mức thì thao tác tạo ticket không bị treo; mail không bị mất vì còn nằm trong hàng đợi để thử lại.

### 7.4 `knowledge_base` (V4) – bài viết tự xử lý
Gồm `title`, `content TEXT`, `category_id` (NULL được), `author_id`, `is_published`, `view_count` (CHECK ≥ 0). `FULLTEXT (title, content)` để tìm bài hướng dẫn trước khi tạo ticket, giúp giảm số ticket lặp.

---

## 8. Tổng hợp quan hệ giữa các bảng

| Quan hệ | Loại | Giải thích |
|---|---|---|
| `departments` — `users` | 1 – N | Một phòng có nhiều người |
| `users` — `tickets` (requester) | 1 – N | Một người gửi nhiều ticket |
| `users` — `tickets` (assignee) | 1 – N | Một IT Support xử lý nhiều ticket |
| `categories` — `tickets` | 1 – N | Một danh mục có nhiều ticket |
| `tickets` — `ticket_status_history` | 1 – N | Một ticket có nhiều bước đổi trạng thái |
| `tickets` — `ticket_assignments` | 1 – N | Một ticket có thể chuyển nhiều lần |
| `tickets` — `ticket_comments` | 1 – N | Nhiều bình luận |
| `ticket_comments` — `ticket_attachments` | 1 – N (tùy chọn) | Một bình luận có thể kèm nhiều tệp |
| `tickets` — `sla_pause_log` | 1 – N | Nhiều lần tạm dừng SLA |
| `tickets` — `ai_classification_logs` | 1 – N | Nhiều lần phân loại |
| `notifications` — `email_outbox` | 1 – 0..1 | Mỗi thông báo tối đa một email |
| `users` — `refresh_tokens` | 1 – N | Nhiều phiên đăng nhập |
| `priority_matrix`, `sla_config`, `business_hours_config` | không có FK | Liên kết với `tickets` qua **giá trị** `impact/urgency/priority`, vì đó là bảng cấu hình tra cứu |

> Không có quan hệ N–N nên không cần bảng trung gian. Bảng `ticket_assignments` trông giống bảng trung gian nhưng thực chất là bảng **lịch sử** (có thêm lý do, thời điểm, người thực hiện).

---

## 9. Vì sao chọn khóa này (tóm tắt để trả lời khi bảo vệ)

| Câu hỏi | Trả lời ngắn |
|---|---|
| Sao dùng `id BIGINT AUTO_INCREMENT` làm PK mà không dùng `email`/`name`? | Giá trị nghiệp vụ có thể đổi (đổi email, đổi tên phòng). Khóa đại diện không đổi, ngắn nên JOIN và index nhanh. Giá trị nghiệp vụ vẫn được bảo vệ bằng `UNIQUE` |
| Sao `sla_config` lại dùng `priority` làm PK? | Mỗi mức ưu tiên đúng một dòng và tập giá trị cố định (4 mức), nên khóa tự nhiên gọn hơn |
| Sao không dùng kiểu `ENUM` của MySQL? | Khó thay đổi giá trị, làm Hibernate `validate` lệch kiểu; `VARCHAR + CHECK` linh hoạt mà vẫn có ràng buộc |
| Sao không có bảng `roles`? | Chỉ 4 vai trò cố định; phân quyền do Spring Security xử lý, thêm bảng chỉ làm phức tạp |
| Sao không `ON DELETE CASCADE`? | Không xóa vật lý, cần giữ lịch sử cho KPI; CASCADE dễ làm mất dữ liệu dây chuyền |
| Sao tách `ai_suggested_*` và giá trị chính thức? | Để đo độ chính xác của AI và có dữ liệu có nhãn để huấn luyện lại |
| Sao `is_resolution_confirmed` là `BOOLEAN NULL`? | Cần ba trạng thái: chờ xác nhận (NULL), đã xong (TRUE), chưa xong (FALSE) |
| Sao có `CHECK` trong khi code đã kiểm tra? | Phòng thủ nhiều lớp: dữ liệu vẫn đúng khi code lỗi hoặc sửa tay trong DB |
| Sao bỏ đánh giá sao (rating)? | Thay bằng xác nhận đóng ticket, Reopen Rate và Auto-close Rate: khách quan hơn, ít bị thiên lệch |
| Sao cần `email_outbox`? | Tách việc gửi mail khỏi request, có thử lại, không mất mail khi Gmail lỗi hoặc hết hạn mức |
| Sao snapshot `department_id` vào ticket? | Báo cáo theo phòng ban không đổi khi nhân viên chuyển phòng |

---

## 10. Cách tạo cơ sở dữ liệu

Chạy theo đúng thứ tự phiên bản bằng Flyway (xem `database/README.md`):

| File | Nội dung |
|---|---|
| `V1__create_schema.sql` | Tạo 13 bảng, khóa, index, CHECK |
| `V2__seed_reference_data.sql` | Nạp category, ma trận 3×3, SLA 4 mức, giờ làm việc T2–T6 |
| `V3__seed_demo_data.sql` | (dev) Phòng ban, 7 tài khoản, 4 ticket mẫu |
| `V4__review_fixes_and_email.sql` | Thêm 4 bảng, `cancel_reason`, `email_notify`, `dedup_key`, FULLTEXT |
| `V5__seed_demo_additions.sql` | (dev) BCrypt thật, dữ liệu mẫu cho bảng mới |

Quy tắc: **không sửa file đã chạy**, mọi thay đổi mới là một file `V6__...` để Flyway kiểm soát được phiên bản.

### Kiểm tra nhanh bằng các câu lệnh
```sql
SHOW TABLES;
DESCRIBE tickets;
SHOW CREATE TABLE tickets\G
SHOW INDEX FROM tickets;

-- Thử ràng buộc: các lệnh dưới đây phải bị DB từ chối
UPDATE tickets SET status = 'CLOSED' WHERE id = 1;       -- chưa có xác nhận
UPDATE tickets SET status = 'CANCELLED' WHERE id = 1;    -- thiếu lý do hủy
INSERT INTO ticket_assignments (ticket_id, assigned_from, assigned_to) VALUES (2, 3, 3);  -- chuyển cho chính mình
```
