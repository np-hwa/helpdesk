# Database – Helpdesk System

MySQL 8.0 (InnoDB, utf8mb4). Migration do **Flyway** chạy độc lập với backend.

## Cấu trúc
| Thư mục/File | Chạy ở | Nội dung |
|---|---|---|
| `migration/V1__create_schema.sql` | mọi môi trường | Tạo schema (13 bảng) |
| `migration/V2__seed_reference_data.sql` | mọi môi trường | Category, ma trận Priority, SLA, giờ làm việc |
| `migration/V4__review_fixes_and_email.sql` | mọi môi trường | ticket_assignments, FULLTEXT, cancel_reason, refresh_tokens, knowledge_base, email_outbox |
| `seed-dev/V3__seed_demo_data.sql` | dev/demo | Phòng ban, 7 tài khoản, 4 ticket mẫu |
| `seed-dev/V5__seed_demo_additions.sql` | dev/demo | Hash BCrypt thật, dữ liệu mẫu cho bảng mới |
| (cấu hình MySQL) | docker-compose.yml | utf8mb4, `innodb_ft_min_token_size=2`, múi giờ +07:00 truyền qua `command:` |

Số phiên bản liên tục giữa 2 thư mục (V3, V5 nằm ở seed-dev nhưng vẫn nằm trong dãy V1..V5). Prod nhảy V2 -> V4 là hợp lệ.

## Chạy
```bash
cp .env.example .env            # điền MYSQL_ROOT_PASSWORD, thông tin Gmail
docker compose up -d mysql
docker compose --profile dev run --rm flyway      # dev: V1..V5
# prod: docker compose --profile prod run --rm flyway-prod   # V1, V2, V4
```
Tài khoản demo (chỉ dev): `admin@helpdesk.local`, `leader@helpdesk.local`, `support1@helpdesk.local`, ... mật khẩu `Demo@12345`.

## Làm lại từ đầu (dev)
```bash
docker compose down -v && docker compose up -d mysql && docker compose --profile dev run --rm flyway
```

## Backend
Đặt `spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=validate`.
Gửi mail: đọc hàng đợi `email_outbox` (PENDING) và gửi qua `smtp.gmail.com:587` STARTTLS bằng App Password.

## Quy tắc
- Không sửa file migration đã chạy (Flyway kiểm tra checksum). Thay đổi mới = file `V6__...`.
- Không commit `.env`.
