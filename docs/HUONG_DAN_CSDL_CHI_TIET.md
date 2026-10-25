# Hướng dẫn chi tiết xây dựng CSDL – Helpdesk System

Tài liệu đi cùng bộ file trong thư mục `db/` và `backend/`. Đọc theo thứ tự các phần 1 → 10.

> **Trạng thái kiểm chứng (nói rõ để bạn biết chỗ nào cần tự chạy thử):**
> - `enums/*`, `sla/BusinessCalendar.java`: đã biên dịch và chạy 16 test (ma trận Priority, máy trạng thái, cộng giờ làm việc qua cuối tuần/nghỉ trưa/ngày lễ) – **tất cả đạt**.
> - Các file SQL (`V1`–`V3`) và code Spring/JPA (`entity`, `repository`, `service`, `job`): **chưa chạy** vì môi trường của mình không có MySQL, Maven, Spring. Bạn hãy chạy theo phần 9 để kiểm tra.

---

## 1. Cấu trúc file bạn nhận được

```
db/
├── migration/
│   ├── V1__create_schema.sql        13 bảng + ràng buộc + index
│   ├── V2__seed_reference_data.sql  danh mục, ma trận priority, SLA, giờ làm việc
│   └── V3__seed_demo_data.sql       dữ liệu demo (chỉ dev)
├── kpi_queries.sql                  12 truy vấn KPI cho Dashboard
├── erd.mermaid                      sơ đồ quan hệ
└── docker-compose.db.yml            MySQL 8 chạy bằng Docker


---

## 2. Quy tắc chọn kiểu dữ liệu (vì sao mỗi trường có kiểu như vậy)

| Loại dữ liệu | Chọn | Lý do |
|---|---|---|
| Khóa chính | `BIGINT AUTO_INCREMENT` | Khớp `Long` trong Java; không lo tràn; chi phí lưu trữ thêm không đáng kể so với `INT`. |
| Khóa ngoại | `BIGINT` cùng kiểu khóa chính | MySQL yêu cầu **cùng kiểu và cùng dấu** (signed/unsigned), lệch là báo lỗi tạo FK. |
| Email | `VARCHAR(150)` + `UNIQUE` | Email doanh nghiệp hiếm khi dài quá 100 ký tự. `UNIQUE` chống trùng tài khoản. |
| Họ tên | `VARCHAR(100)` | Đủ cho tên tiếng Việt đầy đủ. |
| SĐT | `VARCHAR(20)` | **Không dùng số**: mất số 0 đầu, không lưu được `+84`. |
| Mật khẩu | `VARCHAR(255)` | BCrypt chỉ 60 ký tự nhưng chừa chỗ nếu đổi sang thuật toán khác. Lưu **hash**, không lưu mật khẩu. |
| Tên ngắn (danh mục, phòng ban) | `VARCHAR(50/100)` + `UNIQUE` | Có giới hạn rõ, có thể đánh index. |
| Tiêu đề ticket | `VARCHAR(255)` | Tiêu đề phải ngắn gọn; 255 là mức chuẩn, index được. |
| Mô tả / cách xử lý / nội dung bình luận | `TEXT` (tối đa 64 KB) | Nội dung dài, không đánh index thường; 64 KB dư cho mô tả sự cố. |
| Ghi chú, thông báo | `VARCHAR(500)` | Ngắn, hiển thị trên danh sách. |
| Tên file | `VARCHAR(255)` | Giới hạn tên file của hệ điều hành. |
| Đường dẫn lưu file | `VARCHAR(500)` | Đủ cho đường dẫn/khóa UUID. **Không lưu nhị phân (BLOB) trong DB**, chỉ lưu metadata. |
| Trạng thái, vai trò, mức độ | `VARCHAR(10–30)` + `CHECK (... IN (...))` | Thay cho `ENUM` của MySQL để `@Enumerated(STRING)` và `ddl-auto=validate` hoạt động ổn định. Thêm giá trị mới chỉ cần migration đổi CHECK. |
| Cờ đúng/sai | `BOOLEAN` (= `TINYINT(1)`) | Java dùng `Boolean`. |
| Cờ **3 trạng thái** (`is_resolution_confirmed`, `is_ai_suggestion_accepted`) | `BOOLEAN NULL` | `NULL` = chưa có quyết định, `TRUE`/`FALSE` = đã quyết định. Nếu đặt `NOT NULL DEFAULT FALSE` thì không phân biệt được "chưa xác nhận" và "đã từ chối". |
| Độ tin cậy AI | `DECIMAL(4,3)` + `CHECK BETWEEN 0 AND 1` | Chính xác tuyệt đối, không sai số như `FLOAT`. Ví dụ `0.930`. |
| Điểm ma trận, thứ trong tuần | `TINYINT` | Giá trị nhỏ (2–6, 1–7). |
| Thời gian | `DATETIME` | `TIMESTAMP` bị giới hạn năm 2038 và tự đổi múi giờ gây khó hiểu. Thống nhất giờ Việt Nam ở cả JVM và JDBC. |
| Số phút SLA | `INT` (phút) | Lưu đơn vị nhỏ nhất, tránh nhầm giờ/phút; tính toán bằng số nguyên. |
| Kích thước file | `BIGINT` (byte) | File có thể lớn hơn 2 GB về lý thuyết; luôn dùng byte. |

Charset toàn bộ `utf8mb4` để lưu tiếng Việt có dấu.

---

## 3. Quy tắc NULL, DEFAULT, ràng buộc và index

### 3.1 Khi nào `NOT NULL`, khi nào `NULL`

- **`NOT NULL`**: dữ liệu luôn có ngay khi tạo bản ghi (`title`, `description`, `requester_id`, `status`, `created_at`).
- **`NULL`**: dữ liệu **chưa tồn tại ở thời điểm tạo** và chỉ xuất hiện sau một bước nghiệp vụ. Ví dụ `assignee_id`, `category_id`, `priority` (chỉ có sau khi IT Leader xác nhận), `first_response_at`, `resolved_at`, `closed_at`, `sla_deadline`.
- Nguyên tắc: **`NULL` phải có nghĩa nghiệp vụ**. `resolved_at IS NULL` nghĩa là "chưa xử lý xong", nên không đặt giá trị giả như `1970-01-01`.

### 3.2 `DEFAULT`

- `created_at DEFAULT CURRENT_TIMESTAMP`, `status DEFAULT 'NEW'`, `reopen_count DEFAULT 0`, `is_auto_closed DEFAULT FALSE`, `sla_status DEFAULT 'ON_TRACK'`, `users.status DEFAULT 'ACTIVE'`.
- `updated_at ... ON UPDATE CURRENT_TIMESTAMP`: DB tự cập nhật. Vì vậy trong entity khai báo `@Column(insertable = false, updatable = false)` để Hibernate không ghi đè.

### 3.3 Ràng buộc `CHECK` – chỗ nào đáng đặt

| Ràng buộc | Mục đích |
|---|---|
| `role IN (...)`, `status IN (...)`, `impact IN (...)` | Chặn giá trị rác nhập trực tiếp vào DB. |
| `ai_category_confidence BETWEEN 0 AND 1` | Độ tin cậy hợp lệ. |
| `reopen_count >= 0` | Không âm. |
| `resumed_at >= paused_at` | Thời gian tạm dừng không đảo ngược. |
| `chk_tickets_close_needs_confirm` | **Không cho `CLOSED` nếu `is_resolution_confirmed` khác `TRUE`** – yêu cầu bắt buộc của đề tài. |
| `chk_tickets_prio_required` | Ticket đã đi qua giai đoạn NEW (trừ CANCELLED) phải có priority chính thức. |
| `chk_bh_one_kind` | Mỗi dòng giờ làm việc là **hoặc** lịch tuần **hoặc** ngày lễ, không lẫn. |

> **Bẫy của `CHECK` trong MySQL:** điều kiện chỉ bị coi là vi phạm khi kết quả là `FALSE`. Kết quả `NULL` vẫn **được chấp nhận**. Vì vậy ràng buộc đóng ticket phải viết `COALESCE(is_resolution_confirmed, FALSE) = TRUE`; nếu viết `is_resolution_confirmed = TRUE` thì ticket có cờ `NULL` vẫn đóng được.

`CHECK` chỉ là **lớp bảo vệ thứ hai**. Quy tắc nghiệp vụ thật nằm ở tầng Service (đề tài yêu cầu rõ), vì Service trả về thông báo lỗi dễ hiểu còn DB chỉ báo lỗi vi phạm ràng buộc.

### 3.4 Khóa ngoại

- Mọi FK dùng mặc định `RESTRICT` (không `CASCADE`): ticket không bị xóa vật lý, chỉ chuyển `CANCELLED`, nên lịch sử/bình luận/log AI không mất.
- Người dùng nghỉ việc → đổi `users.status = 'INACTIVE'`, không xóa (nếu xóa sẽ mất khóa ngoại của mọi ticket họ từng xử lý).

### 3.5 Index – nguyên tắc "đánh theo truy vấn thật"

| Index | Phục vụ màn hình/tác vụ |
|---|---|
| `idx_tickets_status` | Dashboard đếm theo trạng thái |
| `idx_tickets_assignee_status (assignee_id, status)` | Danh sách việc của IT Support |
| `idx_tickets_requester (requester_id, created_at)` | "Ticket của tôi" sắp theo thời gian |
| `idx_tickets_sla (sla_status, sla_deadline)` | Job cảnh báo SLA |
| `idx_tickets_autoclose (status, resolved_at)` | Job auto-close |
| `idx_tsh_ticket (ticket_id, changed_at)` | Timeline lịch sử của 1 ticket |
| `idx_notif_user_unread (user_id, is_read, created_at)` | Chuông thông báo chưa đọc |

Index composite: cột lọc bằng `=` đặt trước, cột sắp xếp/khoảng đặt sau. Đừng đánh index mọi cột: mỗi index làm chậm `INSERT/UPDATE` và tốn dung lượng.

---

## 4. Thứ tự tạo bảng và quy trình cho từng bảng

### 4.1 Thứ tự (theo phụ thuộc khóa ngoại)

```
Tầng 1 (không phụ thuộc):  departments, categories, priority_matrix, sla_config, business_hours_config
Tầng 2:                     users                      (→ departments)
Tầng 3:                     tickets                    (→ users, departments, categories)
Tầng 4:                     ticket_status_history, ticket_comments, sla_pause_log,
                            notifications, ai_classification_logs   (→ tickets, users, categories)
Tầng 5:                     ticket_attachments         (→ tickets, ticket_comments, users)
```

Tạo sai thứ tự sẽ lỗi `Failed to open the referenced table`. Đây là thứ tự đã dùng trong `V1__create_schema.sql`.

### 4.2 Quy trình 5 bước khi thiết kế một bảng mới

1. **Liệt kê thông tin** cần lưu từ use case/màn hình (không phải từ trí nhớ).
2. **Chọn khóa chính, khóa ngoại**, quyết định quan hệ 1–N hay N–N.
3. **Với từng cột** trả lời 4 câu: kiểu gì? có bắt buộc ngay khi tạo không? mặc định là gì? giá trị hợp lệ là gì (→ `CHECK`)?
4. **Đặt index** cho truy vấn thường gặp và cho khóa ngoại.
5. **Viết migration mới** (`V4__...`), chạy thử, rồi mới viết entity + repository.

Không sửa file migration đã chạy (Flyway kiểm tra checksum và sẽ báo lỗi). Muốn đổi cấu trúc: tạo `V4__alter_...sql`.

---

## 5. Chi tiết từng bảng

### 5.1 `departments`
| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| id | BIGINT | ✗ | auto | PK |
| name | VARCHAR(100) | ✗ | | UNIQUE |
| description | VARCHAR(255) | ✓ | | |
| created_at | DATETIME | ✗ | now | |

### 5.2 `users`
| Cột | Kiểu | Null | Mặc định | Ghi chú |
|---|---|---|---|---|
| email | VARCHAR(150) | ✗ | | UNIQUE, dùng làm tên đăng nhập |
| password_hash | VARCHAR(255) | ✗ | | BCrypt |
| full_name | VARCHAR(100) | ✗ | | |
| phone | VARCHAR(20) | ✓ | | |
| department_id | BIGINT | ✓ | | FK; IT/Admin có thể thuộc phòng IT |
| role | VARCHAR(20) | ✗ | | ADMIN / IT_LEADER / IT_SUPPORT / EMPLOYEE |
| status | VARCHAR(20) | ✗ | ACTIVE | ACTIVE / INACTIVE / LOCKED |
| created_at, updated_at | DATETIME | ✗ | now / auto | |

Vì sao là cột `role` mà không tách bảng `roles`: đề tài chỉ có 4 vai trò cố định, mỗi người 1 vai trò. Nếu sau này cần "1 người nhiều vai trò" hoặc gán quyền chi tiết, tạo `V4` thêm bảng `roles` và `user_roles`.

### 5.3 `categories`
`name VARCHAR(50) UNIQUE`, `description`, `is_active BOOLEAN DEFAULT TRUE`. Dùng `is_active` thay vì xóa: danh mục cũ vẫn còn trên ticket lịch sử.

### 5.4 `priority_matrix`
| Cột | Ghi chú |
|---|---|
| impact, urgency | LOW/MEDIUM/HIGH, `UNIQUE(impact, urgency)` → đúng 9 dòng |
| score | = trọng số impact + trọng số urgency (2–6). Lưu để giải thích được kết quả |
| priority | Kết quả tra bảng |

Tra cứu nằm trong bảng thay vì hard-code để Admin chỉnh được mà không sửa code (đúng yêu cầu "cấu hình ma trận mức ưu tiên").

### 5.5 `sla_config`
PK là chính `priority` (mỗi mức 1 dòng). `response_minutes`, `resolution_minutes` (phút làm việc), `is_24x7` (Critical = TRUE), `warn_level1_percent` = 50, `warn_level2_percent` = 80 cho cơ chế cảnh báo sớm. Low = 1440 phút = 24 giờ **làm việc** ≈ 3 ngày làm việc.

### 5.6 `business_hours_config`
Hai loại dòng trong cùng bảng:
- Lịch tuần: `day_of_week` 1–7 (1 = Thứ Hai), `start_time`, `end_time`, `break_start`, `break_end`.
- Ngày lễ: `holiday_date`, `holiday_name`, `is_working_day = FALSE`.

Nghỉ trưa 12:00–13:00 giúp 08:00–17:00 đúng bằng **8 giờ làm việc/ngày** như README mô tả.

### 5.7 `tickets` (bảng trung tâm) – các nhóm cột

| Nhóm | Cột | Ghi chú |
|---|---|---|
| Nội dung | title, description | Người dùng nhập |
| Con người | requester_id, department_id, assignee_id | `department_id` là **snapshot** phòng ban lúc tạo: nếu nhân viên chuyển phòng, thống kê cũ không đổi theo |
| Chính thức | category_id, impact, urgency, priority | `NULL` đến khi IT Leader xác nhận |
| Gợi ý AI | ai_suggested_category_id, ai_category_confidence, ai_suggested_impact/urgency/priority, is_ai_suggestion_accepted | Tách riêng với giá trị chính thức để đo độ chính xác AI |
| Trạng thái | status | 7 giá trị |
| Mốc thời gian | created_at, first_response_at, resolved_at, closed_at | |
| Xác nhận | resolution, is_resolution_confirmed, resolution_confirmed_at, is_auto_closed, reopen_count | |
| SLA | sla_deadline, sla_status, overdue_minutes | |

Vì sao tách `impact`/`urgency` ra khỏi `priority`: lưu cả hai để giải thích được vì sao ticket có priority đó và để retrain AI (dự đoán impact/urgency, không dự đoán priority trực tiếp).

Vì sao thêm `is_auto_closed` (README không có): KPI "Resolution Confirmation Rate" cần phân biệt Employee tự xác nhận với hệ thống tự đóng sau N ngày. Chỉ dựa vào `is_resolution_confirmed` thì hai trường hợp giống hệt nhau.

Vì sao **không** có cột `ticket_code`: mã hiển thị `TCK-000123` sinh từ `id` ở tầng ứng dụng (`String.format("TCK-%06d", id)`). MySQL không cho generated column tham chiếu cột `AUTO_INCREMENT`.

### 5.8 `ticket_status_history`
`from_status` cho phép `NULL` (dòng đầu tiên khi tạo). `changed_by` cho phép `NULL` = **hệ thống** (auto-close). `note` ghi lý do. Đây là nguồn dữ liệu audit và có thể dùng tính thời gian ở từng trạng thái.

### 5.9 `ticket_comments`
`is_internal` (mặc định FALSE): ghi chú nội bộ của IT, Employee không nhìn thấy. Backend phải lọc `is_internal = FALSE` khi Employee xem.

### 5.10 `ticket_attachments`
Lưu **metadata** (`file_name` gốc, `stored_path` là tên UUID trên đĩa để tránh trùng và tránh path traversal, `content_type`, `file_size`). `comment_id` cho phép `NULL`: `NULL` nghĩa là đính kèm lúc tạo ticket.

### 5.11 `notifications`
`type` là mã (TICKET_ASSIGNED, CONFIRM_REQUEST, SLA_WARNING...) để Frontend chọn icon/đường dẫn; `ticket_id` cho phép `NULL` cho thông báo hệ thống.

### 5.12 `ai_classification_logs`
Một dòng cho **mỗi lần gọi AI**. Nhóm `predicted_*` do AI điền lúc tạo ticket; nhóm `final_*`, `is_*_accepted`, `reviewed_*` điền khi IT Leader xác nhận. `source` phân biệt `AI` và `RULE_BASED` (fallback) để khi tính độ chính xác của PhoBERT không lẫn dữ liệu của luật. Đây là bảng xuất ra để retrain.

### 5.13 `sla_pause_log`
Mỗi lần vào `ON_HOLD` thêm 1 dòng với `resumed_at = NULL`; khi tiếp tục thì điền `resumed_at`. Một ticket có thể tạm chờ nhiều lần nên là bảng riêng, không phải cột trong `tickets`.

---

## 6. Ma trận: trường nào có giá trị ở trạng thái nào

Đây là cách kiểm tra tính hợp lý của các trường `NULL`. `✓` = bắt buộc có, `–` = phải là `NULL`, `~` = có thể có.

| Trường | NEW | ASSIGNED | IN_PROGRESS | ON_HOLD | RESOLVED | CLOSED | CANCELLED |
|---|---|---|---|---|---|---|---|
| ai_suggested_* | ~ | ~ | ~ | ~ | ~ | ~ | ~ |
| category / impact / urgency / priority | – | ✓ | ✓ | ✓ | ✓ | ✓ | ~ |
| assignee_id | – | ✓ | ✓ | ✓ | ✓ | ✓ | ~ |
| sla_deadline | – | ✓ | ✓ | ✓ | ✓ | ✓ | ~ |
| first_response_at | – | – | ✓ | ✓ | ✓ | ✓ | ~ |
| resolved_at, resolution | – | – | ~ (sau reopen) | ~ | ✓ | ✓ | – |
| is_resolution_confirmed | NULL | NULL | NULL | NULL | NULL (chờ) | **TRUE** | NULL |
| closed_at | – | – | – | – | – | ✓ | – |

Ghi chú: `~` ở IN_PROGRESS cho `resolved_at` vì ticket vừa bị reopen vẫn còn `resolved_at` của lần xử lý trước.

---

## 7. Cấu hình dự án

### 7.1 Dependency (Maven)
`spring-boot-starter-data-jpa`, `mysql-connector-j`, `flyway-core`, `flyway-mysql`, `lombok`, `spring-boot-starter-validation`. Với MySQL 8 bắt buộc có `flyway-mysql`.

### 7.2 `application.yml`
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/helpdesk_db?serverTimezone=Asia/Ho_Chi_Minh&useUnicode=true&characterEncoding=UTF-8
    username: helpdesk
    password: helpdesk123
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate          # Flyway tạo bảng; Hibernate chỉ kiểm tra khớp
    properties:
      hibernate.jdbc.time_zone: Asia/Ho_Chi_Minh
  flyway:
    enabled: true
    locations: classpath:db/migration   # dev thêm: ,classpath:db/seed-dev
helpdesk:
  auto-close-days: 3
  ai:
    confidence-threshold: 0.6
```
Thêm `@EnableScheduling` lên lớp `@SpringBootApplication` để `AutoCloseJob` chạy. Đặt JVM `-Duser.timezone=Asia/Ho_Chi_Minh` để `LocalDateTime.now()` khớp DB.

### 7.3 Chạy
```bash
docker compose -f db/docker-compose.db.yml up -d      # bật MySQL
mvn spring-boot:run                                    # Flyway tự chạy V1, V2, V3
```

---

## 8. Cách code: entity ↔ bảng và các quyết định quan trọng

1. **`@Enumerated(EnumType.STRING)`** cho mọi enum. Không dùng `ORDINAL`: thêm/đổi thứ tự enum sẽ làm dữ liệu cũ sai nghĩa.
2. **`Boolean` (wrapper), không phải `boolean`**, cho cờ 3 trạng thái (`isResolutionConfirmed`, `isAiSuggestionAccepted`). `boolean` không có giá trị `null`.
3. **`FetchType.LAZY`** cho mọi `@ManyToOne` và `open-in-view: false`: tránh tải dây chuyền và tránh truy vấn ngầm ở tầng View. Khi cần dữ liệu liên quan, viết `@Query ... join fetch` hoặc DTO projection.
4. **`updated_at` khai báo `insertable=false, updatable=false`**: DB đã tự cập nhật.
5. **`@PrePersist` gán `createdAt`**: đảm bảo có giá trị ngay trong đối tượng sau `save()`.
6. **`getReferenceById`** khi chỉ cần gắn FK (ví dụ category cho gợi ý AI): không tốn thêm truy vấn `SELECT`.
7. **Tên field → tên cột:** Spring Boot tự đổi `camelCase` → `snake_case`. Chỉ ghi `@Column(name=...)` với tên có tiền tố `is_` hoặc `is_24x7` để rõ ràng.
8. **Truy vấn có field bắt đầu bằng `is`:** derived query như `findBy...IsResolutionConfirmedIsNull` dễ bị hiểu sai, nên dùng `@Query` (xem `findAutoCloseCandidates`).
9. **Mọi thay đổi trạng thái đi qua một hàm `transition()`** trong `TicketWorkflowService`: kiểm tra hợp lệ bằng `TicketStatus.canMoveTo`, chặn đóng khi chưa xác nhận, rồi ghi `ticket_status_history`. Không gọi `ticket.setStatus(...)` ở nơi nào khác.
10. **Phân quyền nghiệp vụ trong Service** (`requireRole`, `requireAssignee`), ngoài `@PreAuthorize` ở Controller: người gọi API đúng vai trò nhưng không phải chủ ticket vẫn bị chặn.

---

## 9. Luồng dữ liệu theo từng thao tác (để kiểm tra bạn code đúng)

| Thao tác | Hàm | Trường thay đổi | Dòng thêm |
|---|---|---|---|
| Employee tạo ticket | `TicketCreationService.create` | status=NEW; nếu AI đủ tin cậy (≥ 0.6): `ai_suggested_*` | history (null→NEW); `ai_classification_logs` (kể cả khi AI dưới ngưỡng hoặc fallback) |
| IT Leader xác nhận | `confirmClassification` | category, impact, urgency, priority, `is_ai_suggestion_accepted` | cập nhật `final_*`, `reviewed_*` trong log AI |
| Phân công | `assign` | assignee_id, sla_deadline, status=ASSIGNED | history |
| Tiếp nhận | `start` | first_response_at (lần đầu), status=IN_PROGRESS | history |
| Tạm chờ | `hold` | status=ON_HOLD | `sla_pause_log` (resumed_at = NULL), history |
| Tiếp tục | `resume` | sla_deadline dời ra, status=IN_PROGRESS | điền `resumed_at`, history |
| Xử lý xong | `resolve` | resolution, resolved_at, sla_status, overdue_minutes, status=RESOLVED | history |
| Employee: "Đã xong" | `confirmResolution(true)` | is_resolution_confirmed=TRUE, confirmed_at, closed_at, status=CLOSED | history |
| Employee: "Chưa xong" | `confirmResolution(false)` | reopen_count+1, status=IN_PROGRESS, cờ xác nhận reset về NULL | history |
| Quá N ngày không phản hồi | `AutoCloseJob` → `autoClose` | cờ xác nhận=TRUE, `is_auto_closed`=TRUE, CLOSED | history (changed_by = NULL) |

Vì sao chỉ gán `ai_suggested_*` khi confidence ≥ 0.6: theo README, dưới ngưỡng thì hiển thị "Không chắc chắn, vui lòng chọn thủ công". Nếu vẫn lưu gợi ý dưới ngưỡng lên ticket, IT Leader chọn khác sẽ bị tính là "từ chối AI", làm tỷ lệ chấp nhận sai lệch. Dự đoán vẫn được lưu trong `ai_classification_logs` để phân tích.

**Quyết định bạn cần chốt và ghi vào báo cáo:**
- SLA tính từ `created_at` hay từ lúc IT Leader xác nhận priority (mã hiện tại: từ `created_at`, đổi ở hàm `assign`).
- Sau khi bị reopen, SLA **không** bắt đầu lại (deadline giữ nguyên), nên `sla_status` có thể chuyển từ `MET` sang `BREACHED` ở lần xử lý xong thứ hai. Nếu muốn chính sách khác, sửa ở `resolve`.

---

## 10. Kiểm thử CSDL

### 10.1 Kiểm tra sau khi Flyway chạy
```sql
SELECT version, description, success FROM flyway_schema_history;   -- 3 dòng, success = 1
SELECT COUNT(*) FROM information_schema.tables
 WHERE table_schema = 'helpdesk_db' AND table_name <> 'flyway_schema_history';   -- 13
SELECT COUNT(*) FROM priority_matrix;    -- 9
SELECT COUNT(*) FROM sla_config;         -- 4
```

### 10.2 Kiểm tra ràng buộc: mỗi câu dưới đây **phải báo lỗi**
Chạy trong một transaction rồi `ROLLBACK`; chụp màn hình các lỗi để làm minh chứng kiểm thử.
```sql
START TRANSACTION;

-- 1. Đóng ticket chưa được xác nhận  -> vi phạm chk_tickets_close_needs_confirm
UPDATE tickets SET status = 'CLOSED' WHERE title LIKE 'Máy in%';

-- 2. Vai trò không hợp lệ             -> vi phạm chk_users_role
INSERT INTO users (email, password_hash, full_name, role) VALUES ('a@b.c', 'x', 'A', 'BOSS');

-- 3. Trùng email                      -> vi phạm uk_users_email
INSERT INTO users (email, password_hash, full_name, role)
VALUES ('admin@helpdesk.local', 'x', 'Trùng', 'EMPLOYEE');

-- 4. Độ tin cậy > 1                   -> vi phạm chk_tickets_conf
UPDATE tickets SET ai_category_confidence = 1.5 WHERE id = 1;

-- 5. Ma trận trùng cặp                -> vi phạm uk_priority_matrix_pair
INSERT INTO priority_matrix (impact, urgency, score, priority) VALUES ('HIGH', 'HIGH', 6, 'CRITICAL');

-- 6. Xóa danh mục đang được dùng      -> vi phạm khóa ngoại
DELETE FROM categories WHERE name = 'Printer';

-- 7. Ticket đang xử lý mà không có priority -> vi phạm chk_tickets_prio_required
UPDATE tickets SET priority = NULL WHERE title LIKE 'Máy in%';

ROLLBACK;
```

### 10.3 Kiểm thử logic thuần Java (đã chạy trong môi trường của mình)
Các trường hợp đã kiểm chứng đạt:

| Trường hợp | Kết quả |
|---|---|
| Ma trận (High, High) / (Low, High) / (High, Low) / (Medium, High) | Critical / Medium / Medium / High |
| NEW → CLOSED | Bị chặn |
| RESOLVED → IN_PROGRESS (reopen) | Cho phép |
| RESOLVED → CANCELLED | Bị chặn |
| Thứ Sáu 16:00 + 480 phút làm việc | Thứ Hai 16:00 |
| Thứ Hai 08:00 + 1440 phút (Low) | Thứ Tư 17:00 (≈ 3 ngày làm việc) |
| Tạo Thứ Bảy 10:00 + 60 phút | Thứ Hai 09:00 |
| 11:00 + 120 phút qua giờ nghỉ trưa | 14:00 |
| Ngày lễ rơi vào thứ Hai | Chuyển sang thứ Ba |

Khi bạn có Maven, chuyển các phép kiểm tra này thành JUnit test cho `BusinessCalendar`, `Priority`, `TicketStatus`. Đây là phần không phụ thuộc Spring nên chạy nhanh và ổn định.

---

## 11. Lỗi thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `Migration checksum mismatch` | Đã sửa file `V1` sau khi chạy. Tạo `V4` thay vì sửa. Ở môi trường dev có thể xóa DB tạo lại. |
| `Schema-validation: wrong column type` | Kiểu entity lệch kiểu cột (ví dụ `Integer` với `BIGINT`). Đối chiếu bảng ở mục 5. |
| `Failed to open the referenced table` | Tạo bảng sai thứ tự hoặc FK khác kiểu/dấu với PK. |
| Chữ Việt thành `???` | DB/bảng không phải `utf8mb4` hoặc JDBC thiếu `characterEncoding=UTF-8`. |
| Giờ lệch 7 tiếng | Thiếu `serverTimezone`/`hibernate.jdbc.time_zone` hoặc JVM không đặt `Asia/Ho_Chi_Minh`. |
| `Check constraint ... is violated` khi đóng ticket | Đang gọi `setStatus(CLOSED)` mà chưa đặt `isResolutionConfirmed = true`; hãy đi qua `TicketWorkflowService`. |
| `V3` đăng nhập không được | Chưa thay `{BCRYPT_HASH}` bằng hash thật. |
| Cờ AI luôn `null` | Ticket không có gợi ý (AI lỗi hoặc confidence < 0.6). Đây là hành vi đúng. |

---

## 12. Checklist hoàn thành

- [ ] MySQL 8.0.16+ chạy, `helpdesk_db` dùng `utf8mb4`.
- [ ] 3 file migration nằm đúng `db/migration`, Flyway báo 3 dòng `success`.
- [ ] Thay `{BCRYPT_HASH}` trong V3 (chỉ dev).
- [ ] 13 bảng + `flyway_schema_history`; 9 dòng ma trận; 4 dòng SLA; 7 dòng lịch tuần.
- [ ] 7 câu kiểm tra ràng buộc ở mục 10.2 đều báo lỗi như kỳ vọng.
- [ ] Entity khớp bảng, `ddl-auto: validate` không báo lỗi khi khởi động.
- [ ] Mọi đổi trạng thái đi qua `TicketWorkflowService.transition`.
- [ ] Đã chốt và ghi vào báo cáo hai quyết định ở cuối mục 9.
- [ ] Có JUnit test cho `BusinessCalendar`, `Priority`, `TicketStatus`.
