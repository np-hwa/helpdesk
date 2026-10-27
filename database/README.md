# Cơ sở dữ liệu Helpdesk

Database sử dụng **MySQL 8.0.16+**, InnoDB và `utf8mb4`. Flyway được chạy riêng qua Docker Compose; backend chỉ kết nối và kiểm tra schema.

## Cấu trúc hiện tại

| File | Môi trường | Nội dung |
|---|---|---|
| `migration/V1__create_schema.sql` | Dev và production | Tạo schema mới gồm 16 bảng, khóa ngoại, chỉ mục, full-text search và các ràng buộc dữ liệu |
| `migration/V2__seed_reference_data.sql` | Dev và production | Dữ liệu cấu hình bắt buộc: 7 danh mục, ma trận ưu tiên, SLA và lịch làm việc |
| `seed-dev/V3__seed_demo_data.sql` | Chỉ dev/demo | Dữ liệu mẫu: 5 phòng ban, 7 tài khoản, 6 ticket, lịch sử trạng thái/phân công, log AI và thông báo/email mẫu |

Các file có trong workspace hiện tại là V1, V2 và V3. Profile production chỉ chạy thư mục `migration`, nên không nạp dữ liệu demo.

## Schema

V1 tạo 16 bảng, chia theo nhóm:

- **Danh mục và cấu hình:** `departments`, `users`, `categories`, `priority_matrix`, `sla_config`, `business_hours_config`.
- **Phiên đăng nhập và ticket:** `refresh_tokens`, `tickets`, `ticket_status_history`, `ticket_assignments`, `ticket_comments`, `ticket_attachments`.
- **SLA và phân loại AI:** `sla_pause_log`, `ai_classification_logs`.
- **Thông báo:** `notifications`, `email_outbox`.

Một số điểm chính của schema hiện tại:

- Vai trò người dùng gồm `ADMIN`, `IT_LEADER`, `IT_SUPPORT`, `EMPLOYEE`; trạng thái tài khoản gồm `ACTIVE`, `INACTIVE`, `LOCKED`.
- `tickets` lưu riêng thông tin phân loại/ưu tiên chính thức và gợi ý AI (`ai_suggested_*`), có `needs_manual_classification` để đánh dấu ticket cần kiểm tra thủ công.
- Ticket hỗ trợ lý do hủy (`cancel_reason`), xác nhận kết quả xử lý, tự đóng và mở lại; ticket `CANCELLED` cần có lý do, còn ticket `CLOSED` cần được xác nhận hoàn tất.
- `ticket_assignments` lưu lịch sử phân công; `ticket_status_history` lưu lịch sử thay đổi trạng thái.
- `sla_pause_log` ghi nhận các khoảng tạm dừng SLA; SLA và giờ làm việc được cấu hình trong `sla_config` và `business_hours_config`.
- `tickets` có full-text index trên `title` và `description`. MySQL được cấu hình `innodb_ft_min_token_size=2` trong `docker-compose.yml`.
- `refresh_tokens` chỉ lưu hash token. `ticket_attachments` lưu metadata và đường dẫn tệp; nội dung tệp nằm ngoài database.
- `email_outbox` là hàng đợi email với trạng thái `PENDING`, `SENT`, `FAILED`; ứng dụng chịu trách nhiệm gửi và cập nhật kết quả.
- Schema **không có** bảng `knowledge_base`.

V2 thêm 7 danh mục (`Hardware`, `Software`, `Network & Connectivity`, `Email & Collaboration`, `User Services`, `Access`, `General`), 9 tổ hợp Impact/Urgency, cấu hình SLA cho 4 mức ưu tiên và lịch làm việc từ thứ Hai đến thứ Sáu, 08:00–17:00 (nghỉ trưa 12:00–13:00). Ngày nghỉ lễ có thể được bổ sung vào `business_hours_config`.

`docker-compose.yml` cấu hình MySQL dùng collation `utf8mb4_0900_ai_ci`, múi giờ `+07:00` và `innodb_ft_min_token_size=2` trước khi tạo full-text index.

## Khởi tạo database

1. Tạo file cấu hình môi trường từ mẫu và đặt mật khẩu MySQL:

   ```bash
   cp .env.example .env
   ```

   `MYSQL_ROOT_PASSWORD` là bắt buộc; `MYSQL_DATABASE` không bắt buộc và mặc định là `helpdesk`.

2. Khởi động MySQL:

   ```bash
   docker compose up -d mysql
   ```

3. Chạy migration theo môi trường:

   ```bash
   # Dev/demo: V1, V2 và dữ liệu mẫu V3
   docker compose --profile dev run --rm flyway

   # Production: chỉ V1 và V2, không nạp dữ liệu demo
   docker compose --profile prod run --rm flyway-prod
   ```

Flyway sẽ tạo schema history và chỉ áp dụng các migration chưa chạy. Không chạy đồng thời hai profile trên cùng database.

## Dữ liệu demo

Khi chạy profile `dev`, tất cả tài khoản mẫu dùng mật khẩu `Demo@12345`. Các tài khoản được tạo gồm:

| Vai trò | Email |
|---|---|
| Admin | `admin@helpdesk.local` |
| IT Leader | `leader@helpdesk.local` |
| IT Support | `support1@helpdesk.local`, `support2@helpdesk.local` |
| Employee | `ketoan1@helpdesk.local`, `nhansu1@helpdesk.local`, `kinhdoanh1@helpdesk.local` |

Không dùng tài khoản hoặc dữ liệu mẫu cho production.

## Database cũ và đặt lại môi trường dev

V1 hiện tại là schema được viết lại; nếu database đã chạy một phiên bản V1 cũ, checksum Flyway có thể không còn khớp. Không sửa migration đã được áp dụng và không xóa dữ liệu production để xử lý lỗi này. Hãy sao lưu trước; với database dev có thể tạo lại database trống rồi chạy migration hiện tại.

Đặt lại **toàn bộ** database dev (lệnh `down -v` sẽ xóa volume MySQL và dữ liệu trong đó):

```bash
docker compose down -v
docker compose up -d mysql
docker compose --profile dev run --rm flyway
```

## Tích hợp backend

Khi migration được quản lý bởi Docker Compose, cấu hình backend không chạy Flyway và chỉ kiểm tra schema bằng Hibernate:

```properties
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=validate
```

Không commit file `.env` hoặc thông tin xác thực vào repository. Mọi thay đổi schema sau này cần được thêm bằng migration phiên bản mới, không sửa migration đã chạy trên database dùng chung.
