# Helpdesk System – Hệ thống quản lý yêu cầu hỗ trợ kỹ thuật trong doanh nghiệp (tích hợp AI phân loại & đề xuất Priority)

> Đồ án Thực tập cơ sở / Đồ án tốt nghiệp – Xây dựng hệ thống Web quản lý toàn bộ vòng đời của một yêu cầu hỗ trợ kỹ thuật (Ticket) trong doanh nghiệp, từ khi tiếp nhận đến khi đóng, có ứng dụng AI hỗ trợ phân loại ticket và đề xuất mức độ ưu tiên.

---

## Mục lục

1. [Giới thiệu](#1-giới-thiệu)
2. [Lý do chọn đề tài](#2-lý-do-chọn-đề-tài)
3. [Mục tiêu đề tài](#3-mục-tiêu-đề-tài)
4. [Phạm vi đề tài](#4-phạm-vi-đề-tài)
5. [Phân tích bài toán thực tế](#5-phân-tích-bài-toán-thực-tế)
6. [Quy trình nghiệp vụ (Ticket Lifecycle)](#6-quy-trình-nghiệp-vụ-ticket-lifecycle)
7. [Module AI: Phân loại & Đề xuất Priority](#7-module-ai-phân-loại--đề-xuất-priority)
8. [Đối tượng sử dụng & phân quyền](#8-đối-tượng-sử-dụng--phân-quyền)
9. [Chức năng hệ thống](#9-chức-năng-hệ-thống)
10. [Quản lý mức ưu tiên (Priority) & SLA](#10-quản-lý-mức-ưu-tiên-priority--sla)
11. [Kiến trúc hệ thống](#11-kiến-trúc-hệ-thống)
12. [Công nghệ sử dụng](#12-công-nghệ-sử-dụng)
13. [Thiết kế cơ sở dữ liệu](#13-thiết-kế-cơ-sở-dữ-liệu)
14. [Các chỉ số KPI khai thác từ dữ liệu](#14-các-chỉ-số-kpi-khai-thác-từ-dữ-liệu)
15. [Yêu cầu phi chức năng](#15-yêu-cầu-phi-chức-năng)
16. [Điểm nổi bật của đề tài](#16-điểm-nổi-bật-của-đề-tài)
17. [Hướng dẫn cài đặt & chạy dự án](#17-hướng-dẫn-cài-đặt--chạy-dự-án)
18. [Cấu trúc thư mục dự án](#18-cấu-trúc-thư-mục-dự-án)
19. [Kế hoạch thực hiện](#19-kế-hoạch-thực-hiện)
20. [Hướng phát triển](#20-hướng-phát-triển)
21. [Kết luận](#21-kết-luận)

---

## 1. Giới thiệu

Trong quá trình chuyển đổi số, hầu hết doanh nghiệp đều phụ thuộc vào hệ thống công nghệ thông tin để vận hành. Điều này kéo theo nhu cầu hỗ trợ kỹ thuật ngày càng tăng: lỗi máy tính, lỗi phần mềm, mất kết nối mạng, cấp tài khoản, cấp quyền truy cập, cài đặt ứng dụng...

**Helpdesk System** là một hệ thống Web được xây dựng nhằm số hóa toàn bộ quy trình **tiếp nhận – phân loại – phân công – xử lý – theo dõi SLA – xác nhận hoàn tất – đóng ticket** một yêu cầu hỗ trợ kỹ thuật, thay thế cách quản lý thủ công qua điện thoại, email hoặc các ứng dụng nhắn tin (Zalo, Messenger...).

Điểm khác biệt của phiên bản này so với hệ thống Helpdesk truyền thống: hệ thống tích hợp **module AI** hỗ trợ **tự động phân loại ticket (category)** và **đề xuất mức độ ưu tiên (priority)** ngay khi ticket được tạo, giúp giảm thời gian phân loại thủ công và giảm sai lệch priority do đánh giá chủ quan của người dùng.

## 2. Lý do chọn đề tài

Ở nhiều doanh nghiệp vừa và nhỏ, yêu cầu hỗ trợ vẫn được gửi qua các kênh phi tập trung, dẫn đến các hạn chế:

- Khó theo dõi tiến độ xử lý.
- Dễ bỏ sót yêu cầu.
- Không có lịch sử xử lý.
- Khó đánh giá hiệu quả làm việc của bộ phận IT.
- Thiếu dữ liệu thống kê để cải thiện chất lượng dịch vụ.

Ngay cả khi đã có hệ thống Helpdesk truyền thống, một vấn đề khác vẫn tồn tại: **việc phân loại category và priority phụ thuộc vào người tạo ticket hoặc IT Leader**, dễ dẫn đến:

- Người dùng chọn sai danh mục (VD: chọn "Software" cho lỗi thực chất thuộc "Network").
- Priority bị đánh giá cảm tính, không nhất quán giữa các nhân viên khác nhau.
- IT Leader mất thời gian đọc và phân loại lại thủ công trước khi phân công.

Ngoài ra, một vấn đề nghiệp vụ khác thường bị bỏ qua: **ticket bị đóng khi lỗi chưa thực sự được khắc phục hoàn toàn**, khiến người dùng phải tạo lại ticket mới cho cùng một sự cố, gây trùng lặp dữ liệu và mất thời gian xử lý lại từ đầu. Vì vậy hệ thống cần một bước **xác nhận từ chính người tạo ticket** trước khi ticket được phép đóng.

Để giải quyết các vấn đề trên, doanh nghiệp thường sử dụng **Helpdesk System (IT Helpdesk)** kết hợp **AI/NLP để hỗ trợ phân loại tự động**, đây là xu hướng đã được áp dụng trong các nền tảng ITSM thương mại (Zendesk AI, Freshdesk Freddy AI, ServiceNow Now Assist). Đây là cơ sở thực tiễn cho việc lựa chọn đề tài.

## 3. Mục tiêu đề tài

### 3.1 Mục tiêu tổng quát

Xây dựng hệ thống Helpdesk trên nền tảng Web cho phép quản lý toàn bộ quy trình tiếp nhận, phân công, xử lý, xác nhận hoàn tất và theo dõi các yêu cầu hỗ trợ kỹ thuật trong doanh nghiệp, có tích hợp AI hỗ trợ phân loại ticket và đề xuất mức độ ưu tiên.

### 3.2 Mục tiêu cụ thể

| # | Mục tiêu | Mô tả |
|---|----------|-------|
| 1 | Quản lý tài khoản & phân quyền | Xác thực đăng nhập, phân quyền theo vai trò (RBAC) |
| 2 | Quản lý phòng ban | Quản lý thông tin phòng ban và nhân viên trực thuộc |
| 3 | Quản lý Ticket | Tạo, cập nhật, tìm kiếm, theo dõi toàn bộ vòng đời ticket |
| 4 | **AI phân loại & đề xuất Priority** | Tự động gợi ý category và priority dựa trên tiêu đề/mô tả ticket |
| 5 | Phân công xử lý | IT Leader phân công ticket cho IT Support phù hợp (có thể dựa trên gợi ý tải công việc) |
| 6 | Quản lý trạng thái | Theo dõi vòng đời ticket từ tạo đến đóng, có nhánh từ chối/tạm chờ/hủy |
| 7 | **Xác nhận xử lý xong trước khi đóng** | Employee bắt buộc xác nhận ticket đã được xử lý xong hay chưa; chỉ khi xác nhận "đã xong" ticket mới được phép đóng, ngược lại ticket tự động mở lại |
| 8 | Trao đổi nội bộ | Bình luận qua lại giữa người dùng và IT Support |
| 9 | Quản lý tệp đính kèm | Gửi hình ảnh/tài liệu liên quan đến sự cố |
| 10 | Thông báo | Thông báo khi ticket được tạo/phân công/cập nhật/hoàn thành |
| 11 | Dashboard & báo cáo | Thống kê số lượng, tình trạng, hiệu suất xử lý, độ chính xác AI |
| 12 | Theo dõi SLA | Xác định ticket có nguy cơ hoặc đã quá hạn xử lý |

## 4. Phạm vi đề tài

Hệ thống được xây dựng cho **doanh nghiệp quy mô nhỏ và vừa** (mô phỏng khoảng 100–500 nhân viên).

**Đối tượng sử dụng:**

- **Admin** – quản lý tài khoản, phòng ban, danh mục, mức ưu tiên, phân quyền, cấu hình hệ thống, giám sát chất lượng mô hình AI.
- **IT Leader** – quản lý đội ngũ IT Support, tiếp nhận và phân công ticket, xác nhận/điều chỉnh gợi ý AI, theo dõi tiến độ.
- **IT Support** – tiếp nhận ticket được phân công, trao đổi với người dùng, xử lý và đánh dấu "đã xử lý xong".
- **Employee** – tạo ticket, theo dõi tiến độ, trao đổi, **xác nhận ticket đã được xử lý xong hay chưa để cho phép đóng ticket**.

**Trong phạm vi thực tập cơ sở, đề tài triển khai:**

- ✅ AI phân loại category ticket (dựa trên tiêu đề + mô tả).
- ✅ AI đề xuất mức độ ưu tiên (priority) dựa trên nội dung ticket, có tham chiếu ma trận Impact × Urgency.
- ✅ Cơ chế con người xác nhận/ghi đè gợi ý AI (human-in-the-loop) — AI chỉ **đề xuất**, không tự động quyết định hoàn toàn, để đảm bảo độ tin cậy nghiệp vụ.
- ✅ Cơ chế **xác nhận xử lý xong (Resolution Confirmation)** trước khi đóng ticket.

**Trong phạm vi thực tập cơ sở, đề tài chưa triển khai:**

- SMS Notification
- Chat trực tuyến (real-time chat)
- AI gợi ý người xử lý phù hợp (auto-assignment thông minh)
- AI tóm tắt nội dung ticket
- Chatbot hỗ trợ người dùng tự giải quyết lỗi phổ biến
- Mobile Application
- Kiến trúc Microservices

*(Các nội dung trên được định hướng phát triển ở giai đoạn sau – xem [mục 20](#20-hướng-phát-triển)).*

## 5. Phân tích bài toán thực tế

Khi nhân viên gặp sự cố (máy tính lỗi, không truy cập email, mất kết nối mạng, máy in không hoạt động, xin cấp tài khoản, cài phần mềm, quên mật khẩu...), cách xử lý truyền thống qua gọi điện/nhắn tin trực tiếp dẫn đến:

- Không có lịch sử xử lý.
- Không biết ai đang xử lý ticket nào.
- Không đánh giá được thời gian xử lý.
- Không có số liệu thống kê phục vụ quản lý.
- Việc phân loại và đặt mức ưu tiên phụ thuộc hoàn toàn vào con người, dễ sai lệch.
- Ticket bị đóng khi lỗi chưa thực sự hết, không có bước xác nhận từ người báo lỗi.

Do đó, doanh nghiệp cần một **hệ thống quản lý ticket tập trung**, lấy **Ticket** làm đơn vị quản lý trung tâm, đồng thời **giảm tải công đoạn phân loại thủ công** bằng AI và **đảm bảo ticket chỉ được đóng khi người dùng xác nhận đã xử lý xong**.

### Một số tình huống nghiệp vụ tiêu biểu (dùng để xây dựng Use Case)

| Tình huống | Vấn đề đặt ra | Giải pháp trong hệ thống |
|---|---|---|
| Nhiều ticket phát sinh cùng lúc với mức độ khác nhau | Không thể xử lý theo nguyên tắc "ai gửi trước xử lý trước" | Cơ chế **Priority** (Low/Medium/High/Critical), có AI đề xuất tự động |
| Cùng một loại sự cố nhưng mức độ ảnh hưởng khác nhau | Priority không nên nhập tùy ý | Công thức **Priority = Impact × Urgency**, AI học từ dữ liệu lịch sử để đề xuất Impact/Urgency |
| Người dùng chọn sai danh mục ticket | IT Support xử lý sai chuyên môn, mất thời gian chuyển ticket | **AI phân loại category** dựa trên NLP, gợi ý ngay khi nhập tiêu đề/mô tả |
| Ticket xử lý trễ so với cam kết | Không kiểm soát được thời hạn | **SLA** theo từng mức ưu tiên, cảnh báo khi sắp/đã vi phạm SLA |
| Ticket phải chuyển qua nhiều bộ phận (Helpdesk → Network → System) | Không biết ticket đã chuyển bao nhiêu lần, ai chuyển, vì sao | Bảng **Ticket History / Assignment History** |
| Một nhân viên IT nhận quá nhiều ticket trong khi người khác rảnh | Phân công thủ công không cân bằng tải | **Auto Assignment / Load Balancing** (định hướng mở rộng bằng AI ở giai đoạn sau) |
| Một loại lỗi lặp lại nhiều lần (VD: quên mật khẩu) | Tốn thời gian xử lý thủ công lặp lại | **Knowledge Base** – hướng dẫn tự xử lý |
| Ticket Wi-Fi tập trung bất thường ở một khu vực | Chỉ thống kê chưa đủ, cần tìm nguyên nhân gốc | Chuyển từ **Incident Management** sang **Problem Management** |
| Ticket cần chờ người dùng bổ sung thông tin | Không nên tính SLA trong lúc chờ | Trạng thái **Tạm chờ** (tạm dừng đồng hồ SLA) |
| IT Support báo "đã xử lý xong" nhưng lỗi chưa thực sự hết | Ticket không được đóng ngay mà cần người dùng xác nhận | **Bước Xác nhận xử lý xong (Resolution Confirmation)**: Employee chọn "Đã xử lý xong" → ticket được **Đóng**; chọn "Chưa xử lý xong" → ticket tự động **Mở lại (Reopen)** |
| Employee không phản hồi xác nhận trong thời gian dài | Ticket bị treo mãi ở trạng thái chờ xác nhận | Sau N ngày không phản hồi, hệ thống **tự động đóng ticket** (coi như mặc định xác nhận đã xử lý xong) |
| Đánh giá hiệu suất nhân viên IT | Không nên chỉ dựa vào số lượng ticket | Kết hợp **thời gian xử lý, mức độ tuân thủ SLA, tỷ lệ ticket bị mở lại (reopen rate)** |
| AI đề xuất sai category/priority | Cần cơ chế giám sát & cải thiện mô hình | **Feedback loop**: lưu lại lựa chọn cuối cùng của con người để retrain mô hình định kỳ |

## 6. Quy trình nghiệp vụ (Ticket Lifecycle)

```
Nhân viên
   │
   ▼
Tạo Ticket (nhập tiêu đề + mô tả)
   │
   ▼
AI phân loại & đề xuất Priority  ◄── (AI)
   │
   ▼
IT Leader xác nhận hoặc điều chỉnh gợi ý AI
   │
   ▼
Hệ thống ghi nhận Category + Priority chính thức
   │
   ▼
IT Leader phân công
   │
   ▼
IT Support xử lý → đánh dấu "Đã xử lý xong"
   │
   ▼
Employee xác nhận: ticket đã được xử lý xong hay chưa?  
   │
   ├── Đã xong ──► Đóng Ticket
   │
   └── Chưa xong ──► Mở lại (Reopened) → quay về Đang xử lý
```

### Workflow trạng thái Ticket (đã hiệu chỉnh)

Workflow được thiết kế như sau:

```
                              Đã hủy (Cancelled)   ◄── trạng thái kết thúc,
                                 ▲            ▲        KHÔNG có transition đi ra
                                 │            │
                    (Employee    │            │  IT Leader/Admin hủy ticket đang ở
                     tự hủy)     │            │  bất kỳ trạng thái mở nào (Đã phân công/
                                 │            │  Đang xử lý/Tạm chờ...)
                                 │            │
   Mới tạo ────────────────────┘   Đã phân công ──────────► Đang xử lý ──────────► Đã xử lý xong
   (AI gợi ý                       (IT Leader)         (IT Support        (chờ Employee
   category+priority)                                     tiếp nhận)         xác nhận)
                                                            │  ▲                            │
                                                            │  │                            │
                                                            ▼  │                            ▼
                                                       Tạm chờ  (SLA tạm dừng)      Employee xác nhận đã
                                                       (user/vendor phản hồi)       xử lý xong chưa?
                                                                                     │
                                                                     ┌───────────────┴───────────────┐
                                                                     ▼                               ▼
                                                                 Đã xong                         Chưa xong
                                                                     │                               │
                                                                     ▼                               ▼
                                                                   Đóng                    Mở lại (Reopened)
                                                                                           quay về Đang xử lý
```

**Bảng chuyển trạng thái (state transition table):**

| Từ | Đến | Ai thực hiện | Điều kiện |
|---|---|---|---|
| Mới tạo | Đã phân công | IT Leader / Auto-assignment | AI đã đề xuất category + priority, đã được xác nhận |
| Mới tạo | Đã hủy | Employee | Người dùng tự rút yêu cầu |
| Đã phân công | Đang xử lý | IT Support | Xác nhận tiếp nhận |
| Đang xử lý | Tạm chờ | IT Support | Chờ thông tin từ user hoặc vendor — **dừng đồng hồ SLA** |
| Tạm chờ | Đang xử lý | IT Support / hệ thống tự động khi có phản hồi | Tiếp tục tính SLA |
| Đang xử lý | Đã xử lý xong | IT Support | Đã khắc phục sự cố, chờ Employee xác nhận |
| **Đã xử lý xong** | **Đóng** | **Employee xác nhận**, hoặc hệ thống tự động sau N ngày không phản hồi | **Employee xác nhận ticket đã được xử lý xong hoàn toàn (`is_resolution_confirmed = true`)** |
| **Đã xử lý xong** | **Đang xử lý (Reopened)** | **Employee** | **Employee xác nhận ticket CHƯA được xử lý xong (`is_resolution_confirmed = false`) — ghi nhận `reopen_count`** |
| Bất kỳ trạng thái đang mở | Đã hủy | IT Leader / Admin | Ticket trùng lặp hoặc không còn giá trị xử lý |

> **Quy tắc bắt buộc:** ticket ở trạng thái **Đã xử lý xong** sẽ **không** tự động chuyển sang **Đóng**. Hệ thống luôn yêu cầu Employee xác nhận (Yes/No: "Ticket đã được xử lý xong chưa?"). Nếu Employee không phản hồi sau N ngày (cấu hình được, ví dụ 3 ngày), hệ thống tự động đóng ticket và coi như mặc định xác nhận "đã xử lý xong". 
>
> Thời gian ở trạng thái **Tạm chờ** không tính vào SLA xử lý (tùy theo chính sách SLA của doanh nghiệp). Mọi lần đổi trạng thái được ghi lại trong bảng `ticket_status_history` để phục vụ audit và tính KPI.

## 7. Module AI: Phân loại & Đề xuất Priority

Đây là module mở rộng quan trọng nhất so với hệ thống Helpdesk truyền thống.

### 7.1 Bài toán AI

| Đầu vào | Đầu ra |
|---|---|
| Tiêu đề ticket (`title`) + Mô tả (`description`), có thể kèm phòng ban người tạo | 1) Category đề xuất (Hardware/Software/Network/Email/Printer/Account/ERP/Website) kèm độ tin cậy (confidence score)<br>2) Impact & Urgency đề xuất → tra bảng ma trận → Priority đề xuất |

### 7.2 Hướng tiếp cận kỹ thuật

Đề tài đề xuất mô hình **hybrid: rule-based + machine learning**, phù hợp với quy mô dữ liệu hạn chế của một đồ án thực tập:

| Thành phần | Vai trò |
|---|---|
| **Rule-based (từ khóa)** | Xử lý nhanh các case rõ ràng (VD: từ khóa "mất mạng", "không kết nối" → Network; "quên mật khẩu" → Account) — độ chính xác cao, không cần dữ liệu huấn luyện |
| **Text classification model** | Xử lý các case còn lại bằng mô hình học máy huấn luyện trên tập ticket lịch sử (thật hoặc mô phỏng) |
| **Priority matrix (đã thiết kế ở mục 10)** | Sau khi có Impact/Urgency đề xuất, tra bảng để ra Priority — không để mô hình "đoán" priority trực tiếp, tránh sai lệch khó giải thích |

**Về mô hình phân loại văn bản (do dữ liệu tiếng Việt):**

- Tiền xử lý: tách từ tiếng Việt bằng `underthesea` hoặc `pyvi`, chuẩn hóa, loại bỏ stopword.
- Baseline (khuyến nghị dùng trước, dễ triển khai và giải thích): **TF-IDF + Logistic Regression / SVM** — phù hợp với tập dữ liệu vài nghìn ticket mô phỏng, huấn luyện nhanh, không cần GPU.
- Nâng cao (nếu có thời gian và dữ liệu đủ lớn): fine-tune mô hình **PhoBERT** (BERT tiếng Việt) cho bài toán phân loại đa lớp.
- Đầu ra kèm **confidence score**; nếu confidence thấp hơn ngưỡng (VD: 0.6), hệ thống không tự chọn mà hiển thị "Không chắc chắn, vui lòng chọn thủ công" để tránh gây nhiễu.

### 7.3 Nguyên tắc human-in-the-loop

AI **chỉ đề xuất, không tự động quyết định hoàn toàn**:

1. Khi Employee tạo xong ticket, hệ thống gọi AI service và hiển thị gợi ý category + priority cho IT leader.
2. IT leader có thể **chấp nhận** hoặc **chỉnh sửa** gợi ý.
3. Lựa chọn cuối cùng được lưu lại kèm cờ `is_ai_suggestion_accepted` — đây chính là dữ liệu nhãn (label) để **retrain mô hình định kỳ**, giúp mô hình cải thiện theo thời gian sử dụng thực tế (feedback loop).

### 7.4 Kiến trúc tích hợp AI

AI được tách thành **service riêng** (không nhúng trực tiếp vào Backend Java) để dễ phát triển, huấn luyện và thay thế mô hình độc lập với hệ thống nghiệp vụ chính:

```
React Frontend
      │  (POST /tickets)
      ▼
Spring Boot Backend  ──────REST/HTTP─────►  AI Service (Python FastAPI)
      │                                          │
      │◄─────── category, priority, confidence ──┘
      ▼
MySQL (lưu ticket + kết quả gợi ý AI)
```

- Backend gọi AI Service qua REST nội bộ khi tạo ticket (đồng bộ, timeout ngắn ~1–2s).
- Nếu AI Service không phản hồi (timeout/lỗi), hệ thống **fallback về rule-based đơn giản** hoặc yêu cầu người dùng chọn thủ công — không để nghẽn luồng tạo ticket.
- AI Service độc lập, có thể scale/deploy riêng, thuận lợi khi retrain hoặc đổi mô hình mà không ảnh hưởng Backend chính.

### 7.5 Đánh giá chất lượng mô hình

| Chỉ số | Ý nghĩa |
|---|---|
| Accuracy / F1-score (macro) | Đánh giá trên tập test khi huấn luyện mô hình |
| **AI Suggestion Acceptance Rate** | `(Số ticket giữ nguyên gợi ý AI / Tổng số ticket có gợi ý AI) × 100%` — đo hiệu quả thực tế trong vận hành, đưa lên Dashboard |
| Confusion matrix theo category | Phát hiện category nào mô hình hay nhầm lẫn, phục vụ cải thiện |


## 8. Đối tượng sử dụng & phân quyền

| Vai trò | Quyền hạn chính |
|---|---|
| **Admin** | Quản lý tài khoản, phòng ban, danh mục ticket, mức ưu tiên, SLA, phân quyền người dùng, xem toàn bộ Dashboard & báo cáo, giám sát chỉ số AI Acceptance Rate |
| **IT Leader** | Xem tất cả ticket, xác nhận/điều chỉnh gợi ý AI, phân công ticket, theo dõi tiến độ xử lý, quản lý nhân viên IT, theo dõi SLA, xem báo cáo |
| **IT Support** | Nhận ticket được phân công, bình luận, cập nhật tiến độ, đính kèm hình ảnh, đánh dấu ticket "Đã xử lý xong" |
| **Employee** | Tạo ticket, theo dõi ticket, bình luận, upload hình ảnh, **xác nhận ticket đã xử lý xong hay chưa để cho phép đóng ticket** |

Phân quyền được triển khai theo mô hình **Role-Based Access Control (RBAC)** kết hợp **JWT Authentication**.

## 9. Chức năng hệ thống

| Module | Nội dung chính |
|---|---|
| **1. Đăng nhập & phân quyền** | Đăng nhập, đăng xuất, đổi mật khẩu, quên mật khẩu, JWT Authentication, RBAC |
| **2. Quản lý người dùng** | Quản lý Employee, IT Support, IT Leader, Admin (họ tên, email, SĐT, phòng ban, vai trò, trạng thái) |
| **3. Quản lý Ticket** | Mã ticket, tiêu đề, mô tả, danh mục, mức ưu tiên, người tạo, người xử lý, ngày tạo, hạn xử lý, trạng thái, file đính kèm |
| **4. AI phân loại & đề xuất Priority** | Gọi AI Service khi tạo ticket, hiển thị gợi ý category/priority kèm confidence, cho phép chỉnh sửa, lưu feedback để retrain |
| **5. Quản lý danh mục** | Hardware, Software, Network, Email, Printer, Account, ERP, Website |
| **6. Quản lý mức ưu tiên** | Low, Medium, High, Critical, cấu hình ma trận Impact × Urgency |
| **7. Workflow xử lý Ticket** | Mới tạo → Đã phân công → Đang xử lý → Tạm chờ → Đã xử lý xong → **Xác nhận xử lý xong** → Đóng, có nhánh Từ chối/Hủy/Reopen |
| **8. Xác nhận xử lý xong (Resolution Confirmation)** | Sau khi IT Support đánh dấu "Đã xử lý xong", hệ thống yêu cầu Employee xác nhận Có/Không; "Có" → tự động chuyển ticket sang Đóng; "Không" → tự động Reopen ticket, không yêu cầu nhập đánh giá chất lượng |
| **9. Bình luận & trao đổi** | Trao đổi giữa người dùng và IT, upload ảnh/tài liệu, lưu lịch sử trao đổi |
| **10. Notification** | Thông báo khi ticket được tạo/phân công/đổi trạng thái/chờ xác nhận xử lý/hoàn thành |
| **11. Dashboard** | Tổng số ticket, đang xử lý, đã hoàn thành, quá hạn, theo phòng ban, theo loại, theo nhân viên IT, **AI Suggestion Acceptance Rate**, tỷ lệ xác nhận đóng ticket, biểu đồ thống kê |
| **12. Báo cáo** | Ticket theo tháng, theo phòng ban, theo loại, hiệu suất xử lý, thời gian xử lý trung bình, tỷ lệ hoàn thành đúng hạn, tỷ lệ reopen sau xác nhận, độ chính xác AI theo thời gian |

## 10. Quản lý mức ưu tiên (Priority) & SLA

### 10.1 Công thức xác định Priority

### a) Bản chất công thức
```
Priority = Impact × Urgency
```

Đây **không phải phép nhân số học** giữa hai con số, mà là cách gọi tắt quen thuộc trong ITSM/ITIL cho một **ma trận quyết định (decision matrix)**: kết hợp hai trục đánh giá độc lập để suy ra một giá trị đầu ra duy nhất.

- **Impact (mức ảnh hưởng):** đo *phạm vi* — sự cố tác động đến bao nhiêu người, phòng ban nào, hệ thống có phải trọng yếu hay không.
- **Urgency (mức khẩn cấp):** đo *tốc độ cần xử lý* — sự cố có thể chờ được hay phải xử lý ngay, có deadline nghiệp vụ đi kèm hay không.

Hai trục này **độc lập với nhau**: một sự cố có thể ảnh hưởng ít người nhưng cực kỳ khẩn cấp (Impact thấp, Urgency cao), hoặc ảnh hưởng nhiều người nhưng không gấp (Impact cao, Urgency thấp). Vì vậy không thể suy Priority chỉ từ một trục — cần kết hợp cả hai qua ma trận.

### b) Số hóa thang đo — gán trọng số

Mỗi trục có 3 mức, được gán điểm theo thang tuyến tính:

| Mức | Điểm |
|---|---|
| Low | 1 |
| Medium | 2 |
| High | 3 |

Chọn thang tuyến tính (1-2-3) thay vì thang phi tuyến (VD: hàm mũ 1-2-4) vì mục tiêu cốt lõi của hệ thống là **kết quả dễ giải thích**: "High nghiêm trọng gấp 3 lần Low" là phát biểu người dùng nghiệp vụ hiểu ngay, không cần diễn giải toán học phức tạp.

### c) Quyết định trọng số tương đối giữa hai trục

Đây là bước mang tính **quyết định nghiệp vụ**, không đơn thuần là toán học. Có hai lựa chọn thiết kế:

- **Đối xứng:** `Score = Impact + Urgency` — hai trục đóng góp ngang nhau.
- **Lệch trọng số:** `Score = Impact + (Urgency × 1.5)` — ưu tiên Urgency hơn, theo triết lý "thời gian là yếu tố sống còn của sự cố".

Hệ thống chọn **phương án đối xứng** làm chuẩn, vì:
1. Dễ giải thích trong tài liệu/bảo vệ đồ án — không cần biện minh hệ số 1.5 lấy từ đâu.
2. Phù hợp cách nhiều tài liệu ITIL v3 mô tả ma trận Priority chuẩn.
3. Dễ mở rộng/tùy biến sau này nếu doanh nghiệp muốn đổi trọng số — chỉ cần sửa công thức, không phải thiết kế lại ma trận.

### d) Công thức tính điểm và quy tắc chia khoảng

```
Score(Impact, Urgency) = Weight(Impact) + Weight(Urgency)

Priority = f(Score):
  Score ∈ {2, 3}  →  Low
  Score = 4       →  Medium
  Score = 5       →  High
  Score = 6       →  Critical
```

Điểm nằm trong khoảng lý thuyết **[2, 6]** (tối thiểu 1+1, tối đa 3+3). Cách chia khoảng:

- **2, 3 → Low:** cả hai trục đều thấp, hoặc một trục thấp + một trục trung bình — chưa có yếu tố nào đủ nghiêm trọng.
- **4 → Medium:** điểm trung tâm của thang, rơi vào 3 tổ hợp — (Low, High), (Medium, Medium), (High, Low) — tức có ít nhất một yếu tố đạt mức cao nhất, hoặc cả hai cùng ở mức trung bình. Đây là ranh giới giữa "còn kiểm soát được" và "cần chú ý".
- **5 → High:** rơi vào (Medium, High) và (High, Medium) — một trục đạt mức tối đa **và** trục còn lại đạt ít nhất mức trung bình → tín hiệu rõ ràng của sự cố nghiêm trọng.
- **6 → Critical:** chỉ duy nhất tổ hợp (High, High) — cả hai trục đều tối đa. Critical được dành riêng cho trường hợp "báo động đỏ" thực sự, không san sẻ với bất kỳ tổ hợp nào khác.

### e) Bảng ma trận kết quả (dùng để cấu hình bảng `priority_matrix`)

| Impact \ Urgency | Low | Medium | High |
|---|---|---|---|
| **Low** | Low | Low | Medium |
| **Medium** | Low | Medium | High |
| **High** | Medium | High | Critical |

![Priority Matrix](matrix.png)
(hình ảnh minh hoạ)
### f) Diễn giải các ô dễ gây tranh cãi

**(High, Low) → Medium, không phải High:** Impact cao nhưng Urgency thấp nghĩa là sự cố *có thể chờ được* (có workaround, chưa cần xử lý ngay). Ví dụ: lỗi module báo cáo ảnh hưởng toàn phòng Kế toán, nhưng deadline báo cáo còn 2 tuần — vẫn cần xử lý nhưng không cần chen ngang các ticket đang gấp khác.

**(Low, High) → Medium, không phải Low:** Ngược lại, Urgency cao dù Impact thấp (chỉ 1 người) vẫn được nâng lên Medium thay vì giữ Low, vì "cần xử lý ngay" là tín hiệu không thể bỏ qua hoàn toàn dù phạm vi ảnh hưởng nhỏ.

Hai ô này cùng điểm 4 nhưng **bản chất khác nhau hoàn toàn** — đây chính là giá trị của cách tính bằng điểm số: các tình huống khác nhau về nguyên nhân nhưng tương đương nhau về mức độ ưu tiên tổng thể vẫn được xếp cùng nhóm, một cách nhất quán, lặp lại được, không phụ thuộc cảm tính người phân loại.

### g) Hai tính chất đảm bảo của công thức

- **Đơn điệu (monotonic):** Impact hoặc Urgency tăng thì Priority không bao giờ giảm — đảm bảo tính hợp lý logic, tránh nghịch lý (VD: ticket ảnh hưởng nhiều người hơn nhưng lại có priority thấp hơn).
- **Đối xứng (symmetric):** hoán đổi Impact và Urgency cho cùng kết quả Priority — vì cả hai trục được coi trọng ngang nhau theo lựa chọn thiết kế ở mục (c).

### h) Vai trò của AI trong công thức

Với module AI, **Impact và Urgency được AI đề xuất** dựa trên nội dung ticket:

- Impact: suy luận từ số lượng người/phòng ban bị ảnh hưởng (nếu suy luận được từ text), loại hệ thống bị ảnh hưởng (thiết bị cá nhân vs hệ thống dùng chung/lõi), và số lượng ticket tương tự phát sinh gần thời điểm đó.
- Urgency: suy luận từ mức độ chặn công việc (không dùng được hoàn toàn / vẫn dùng được nhưng hạn chế / có thể chờ), và có deadline/nghiệp vụ gấp gắn kèm hay không.

Sau khi có Impact và Urgency đề xuất, hệ thống **tra bảng ma trận cố định ở mục (e)** — hoàn toàn không để mô hình AI tự "đoán" ra Priority trực tiếp. Cách tách này đảm bảo:

1. **Nhất quán:** cùng một cặp (Impact, Urgency) luôn cho ra cùng một Priority, không phụ thuộc lần chạy mô hình khác nhau.
2. **Dễ giải thích:** người dùng/IT Leader có thể tự tra bảng để hiểu vì sao ticket được xếp priority đó, không cần tin tưởng mù quáng vào "hộp đen" của AI.
3. **Dễ kiểm soát sai số:** nếu AI dự đoán sai, sai số chỉ nằm ở việc chọn nhầm Impact/Urgency (2 giá trị rời rạc, dễ sửa/override), chứ không phải sai toàn bộ logic tính Priority.

### 10.2 Bảng SLA theo mức ưu tiên

Bảng SLA được xây dựng dựa trên nguyên tắc: **Response Time** (thời gian phản hồi — IT Support xác nhận đã tiếp nhận ticket) luôn ngắn hơn nhiều lần so với **Resolution Time** (thời gian xử lý xong hoàn toàn), vì phản hồi chỉ cần ghi nhận "đã thấy, đang xử lý", còn xử lý xong mới thực sự tốn công sức.

Mức Critical được chọn làm đơn vị gốc (15 phút phản hồi — phù hợp với quy mô đội IT Support 2–5 người của doanh nghiệp 100–500 nhân viên), các mức còn lại suy ra theo hệ số nhân tăng dần, đảm bảo khoảng cách giữa các mức đủ lớn để tạo áp lực ưu tiên rõ ràng cho đội xử lý.

| Priority | Thời gian phản hồi | Thời gian xử lý | Phạm vi áp dụng |
|---|---|---|---|
| Critical | 15 phút | 2 giờ | 24/7 (kể cả ngoài giờ, cuối tuần) |
| High | 30 phút | 4 giờ | Giờ hành chính |
| Medium | 2 giờ | 8 giờ (1 ngày làm việc) | Giờ hành chính |
| Low | 4 giờ | 24 giờ (~3 ngày làm việc) | Giờ hành chính |

**Diễn giải:**

- **Critical (15 phút / 2 giờ, 24/7):** tương ứng sự cố ảnh hưởng toàn công ty **và** chặn hoàn toàn công việc (Impact cao + Urgency cao). Do tính chất nghiêm trọng, SLA được tính liên tục kể cả ngoài giờ hành chính, cuối tuần — hạ tầng lõi sập bất kể lúc nào cũng cần phản ứng ngay.
- **High (30 phút / 4 giờ):** 4 giờ tương đương nửa ngày làm việc, đủ để một nhân viên IT Support xử lý dứt điểm trong một ca.
- **Medium (2 giờ / 8 giờ):** 8 giờ đúng bằng 1 ngày làm việc — mốc cam kết phổ biến "xử lý trong ngày".
- **Low (4 giờ / 24 giờ):** 24 giờ tính theo **giờ hành chính tích lũy** (8 giờ/ngày làm việc), thực chất tương đương khoảng 3 ngày làm việc theo lịch thực — không phải 24 giờ đồng hồ liên tục. Cần làm rõ điều này khi trình bày để tránh hiểu nhầm.

> **Lưu ý về giờ hành chính (Business Hours):** trừ mức Critical (tính 24/7), các mức còn lại chỉ tính SLA trong giờ hành chính (VD: 8h–17h, Thứ 2 – Thứ 6). Ticket tạo ngoài giờ hoặc cuối tuần sẽ được cộng dồn thời gian xử lý bắt đầu từ giờ làm việc kế tiếp, không tính theo thời gian thực tuyệt đối — nếu không, một ticket Low tạo lúc 17h thứ Sáu sẽ bị tính hết hạn vào thứ Bảy, trong khi thực tế đội IT không làm việc cuối tuần. Cấu hình giờ làm việc và ngày nghỉ lễ được lưu trong bảng `business_hours_config` để phục vụ tính `sla_deadline` chính xác.

### 10.3 Xác định SLA Breach

```
SLA Deadline   = Created At + SLA xử lý (tính theo Business Hours, trừ Critical tính 24/7)
SLA Breach     = Resolved At > SLA Deadline
Overdue        = Resolved At − SLA Deadline
```

Hệ thống cần lưu: `sla_deadline`, `resolved_at`, `sla_status` (Met / Breached), `overdue_duration`. Thời gian ở trạng thái **Tạm chờ** được loại trừ khỏi tính toán SLA thông qua bảng `sla_pause_log`.

> Lưu ý: `resolved_at` được ghi nhận khi ticket chuyển sang **Đã xử lý xong** (IT Support hoàn tất); thời gian chờ Employee xác nhận (Đã xử lý xong → Đóng) **không** tính là vi phạm SLA xử lý, vì SLA đo hiệu suất của IT Support, không đo tốc độ phản hồi của Employee.

**Cơ chế cảnh báo trước khi vi phạm (Escalation):** thay vì chỉ ghi nhận nhị phân Đạt/Vi phạm khi đã quá hạn, hệ thống nên cảnh báo sớm theo các mốc phần trăm thời gian SLA đã trôi qua:

| Mốc | Hành động |
|---|---|
| Đạt 50% thời gian SLA | Cảnh báo nhẹ tới IT Support đang xử lý |
| Đạt 80% thời gian SLA | Cảnh báo tới IT Leader, hiển thị nổi bật trên Dashboard |
| Vượt 100% (breach) | Ghi nhận vi phạm (`sla_status = Breached`), thông báo cấp quản lý, tính vào KPI SLA Compliance |

## 11. Kiến trúc hệ thống

Hệ thống được phát triển theo mô hình **Client – Server**, mở rộng thêm **AI Service** như một thành phần độc lập:

```
Người dùng
    │
    ▼
Frontend (React + Vite)
    │
    ▼
REST API
    │
    ▼
Backend (Spring Boot) ───REST nội bộ───► AI Service (Python FastAPI + mô hình phân loại)
    │
    ▼
MySQL Database
```

Backend được tổ chức theo kiến trúc **nhiều lớp (layered architecture)**:

```
Controller → Service → Repository → Database
```

AI Service được tổ chức tách biệt để:

- Dễ huấn luyện/retrain mô hình mà không ảnh hưởng hệ thống nghiệp vụ chính.
- Có thể scale độc lập nếu tải AI tăng cao.
- Cho phép thay thế mô hình (từ TF-IDF baseline sang PhoBERT nâng cao) mà không cần sửa Backend Java, chỉ cần giữ nguyên contract API.

## 12. Công nghệ sử dụng

### Backend
- Java 21
- Spring Boot, Spring Security, Spring Data JPA
- JWT Authentication
- Maven
- Lombok, MapStruct
- Swagger / OpenAPI
- Flyway (database migration)
- Bean Validation
- `RestTemplate` / `WebClient` để gọi AI Service

### AI Service
- Python 3.11
- FastAPI (REST API cho model serving)
- scikit-learn (TF-IDF + Logistic Regression/SVM baseline)
- underthesea / pyvi (tách từ tiếng Việt)
- (Tùy chọn nâng cao) Hugging Face Transformers + PhoBERT
- Pandas, NumPy cho xử lý dữ liệu huấn luyện
- Uvicorn (ASGI server)

### Frontend
- ReactJS + Vite
- Material UI
- React Router
- Axios
- TanStack Query
- React Hook Form
- Recharts (biểu đồ Dashboard, bao gồm biểu đồ AI Acceptance Rate)
- Day.js

### Cơ sở dữ liệu
- MySQL 8

### Công cụ phát triển
- IntelliJ IDEA, Visual Studio Code, PyCharm/Jupyter (cho phần AI)
- MySQL Workbench
- Postman
- Git, GitHub
- Docker (đóng gói cả Backend, Frontend và AI Service)

## 13. Thiết kế cơ sở dữ liệu

### 13.1 Nhóm bảng chính

| Bảng | Ý nghĩa |
|---|---|
| `users` | Người dùng hệ thống |
| `roles` / `permissions` | Vai trò và quyền hạn (RBAC) |
| `departments` | Phòng ban |
| `tickets` | Yêu cầu hỗ trợ (bảng trung tâm) |
| `ticket_categories` | Danh mục ticket (Hardware, Software, Network...) |
| `priorities` | Mức độ ưu tiên |
| `priority_matrix` | Ma trận Impact × Urgency → Priority |
| `ticket_status_history` | Lịch sử đổi trạng thái ticket (audit trail) |
| `sla_pause_log` | Ghi nhận thời gian tạm dừng SLA (Tạm chờ) |
| `ticket_comments` | Bình luận / trao đổi |
| `attachments` | Tệp đính kèm |
| `notifications` | Thông báo |
| `audit_logs` / `activity_logs` | Nhật ký thao tác |
| `ticket_history` / `ticket_assignments` | Lịch sử phân công, chuyển ticket |
| `sla_policies` | Chính sách SLA theo priority |
| `knowledge_base` | Bài viết hướng dẫn tự xử lý |
| `ai_classification_logs` | Log mỗi lần gọi AI: input, output gợi ý, confidence, kết quả cuối do người xác nhận — dữ liệu để retrain |

### 13.2 Chi tiết trường dữ liệu bảng `tickets`

| Field | Ý nghĩa |
|---|---|
| `ticket_id` | Mã ticket |
| `title` | Tiêu đề |
| `description` | Mô tả |
| `requester_id` | Người yêu cầu |
| `department_id` | Phòng ban |
| `category_id` | Loại yêu cầu (giá trị cuối cùng, sau xác nhận) |
| `ai_suggested_category_id` | Category do AI đề xuất |
| `ai_category_confidence` | Độ tin cậy của gợi ý category (0–1) |
| `priority` | Mức độ ưu tiên (giá trị cuối cùng) |
| `ai_suggested_priority` | Priority do AI đề xuất |
| `impact` | Mức ảnh hưởng (đã xác nhận) |
| `urgency` | Mức khẩn cấp (đã xác nhận) |
| `is_ai_suggestion_accepted` | Cờ đánh dấu người dùng có giữ nguyên gợi ý AI hay không |
| `status` | Trạng thái |
| `assignee_id` | Nhân viên xử lý |
| `team_id` | Nhóm IT |
| `created_at` | Thời gian tạo |
| `first_response_at` | Thời gian phản hồi đầu tiên |
| `resolved_at` | Thời gian IT Support đánh dấu "Đã xử lý xong" |
| `is_resolution_confirmed` | **Cờ xác nhận của Employee: `true` = đã xử lý xong (cho phép đóng), `false` = chưa xử lý xong (tự động Reopen), `null` = đang chờ xác nhận** |
| `resolution_confirmed_at` | **Thời điểm Employee xác nhận (hoặc thời điểm hệ thống tự động xác nhận sau N ngày không phản hồi)** |
| `closed_at` | Thời gian đóng |
| `sla_deadline` | Deadline SLA |
| `sla_status` | Đạt / vi phạm SLA |
| `resolution` | Cách xử lý (IT Support ghi khi đánh dấu "Đã xử lý xong") |
| `reopen_count` | Số lần ticket bị mở lại (tăng mỗi khi Employee xác nhận "chưa xử lý xong") |


### 13.3 Bảng `ai_classification_logs`

| Field | Ý nghĩa |
|---|---|
| `id` | Khóa chính |
| `ticket_id` | Ticket liên quan |
| `input_text` | Nội dung title + description gửi cho AI |
| `predicted_category` | Category AI dự đoán |
| `predicted_confidence` | Độ tin cậy |
| `predicted_impact` / `predicted_urgency` | Impact/Urgency AI dự đoán |
| `final_category` / `final_priority` | Giá trị con người xác nhận cuối cùng |
| `model_version` | Phiên bản mô hình đã dùng để dự đoán (phục vụ theo dõi khi đổi mô hình) |
| `created_at` | Thời điểm gọi AI |

Cơ sở dữ liệu được thiết kế theo **mô hình quan hệ (relational model)**, đảm bảo tính toàn vẹn dữ liệu và dễ mở rộng. Việc quản lý phiên bản schema sử dụng **Flyway migration**.

## 14. Các chỉ số KPI khai thác từ dữ liệu

| KPI | Công thức |
|---|---|
| First Response Time (FRT) | `FirstResponseAt − CreatedAt` |
| Resolution Time (RT) | `ResolvedAt − CreatedAt` |
| SLA Compliance | `(Ticket đạt SLA / Tổng ticket) × 100%` |
| Reopen Rate | `(Ticket bị mở lại do Employee xác nhận "chưa xong" / Ticket đã ở trạng thái "Đã xử lý xong") × 100%` |
| Average Resolution Time (ART) | `Tổng thời gian xử lý / Số ticket đã xử lý` |
| Completion Rate | `(Ticket đã đóng / Tổng ticket) × 100%` |
| **Resolution Confirmation Rate** | `(Ticket được Employee chủ động xác nhận / Tổng ticket ở trạng thái "Đã xử lý xong") × 100%` — phần còn lại là ticket tự động đóng do không phản hồi sau N ngày |
| **AI Suggestion Acceptance Rate** | `(Ticket giữ nguyên gợi ý AI / Tổng ticket có gợi ý AI) × 100%` |
| **AI Classification Accuracy** (offline, trên tập test) | `(Số dự đoán đúng / Tổng số mẫu test) × 100%` |

Các KPI này là đầu vào trực tiếp cho **Dashboard** và **báo cáo hiệu suất**, đồng thời là dữ liệu minh chứng giá trị thực tiễn của đề tài. Reopen Rate đặc biệt quan trọng vì phản ánh trực tiếp hiệu quả của bước xác nhận xử lý xong: reopen rate cao cho thấy IT Support đang đóng ticket sớm khi lỗi chưa thực sự hết.

## 15. Yêu cầu phi chức năng

- Giao diện thân thiện, dễ sử dụng.
- Thời gian phản hồi nhanh.
- Có cơ chế **fallback** khi AI Service lỗi/timeout (không chặn việc tạo ticket).
- Dễ bảo trì và mở rộng (kiến trúc nhiều lớp, tách biệt rõ ràng, AI Service độc lập).
- Phân quyền rõ ràng theo vai trò (RBAC).
- Đảm bảo an toàn dữ liệu (JWT, mã hóa mật khẩu, validation).
- Hỗ trợ ghi nhật ký thao tác (Audit Log), bao gồm log các lần gọi AI và log xác nhận xử lý xong.
- Có khả năng triển khai trên môi trường thực tế (Docker hóa cả 3 thành phần: Backend, Frontend, AI Service).
- Mô hình AI có khả năng **retrain định kỳ** từ dữ liệu feedback thực tế mà không cần deploy lại Backend.
- **Ràng buộc nghiệp vụ bắt buộc:** ticket không được phép chuyển sang trạng thái Đóng nếu chưa có xác nhận `is_resolution_confirmed = true` (từ Employee hoặc tự động sau N ngày) — ràng buộc này cần được kiểm tra ở tầng Service (Backend), không chỉ ở giao diện.

## 16. Điểm nổi bật của đề tài

Khác với các hệ thống CRUD thông thường, đề tài tập trung vào **quy trình quản lý nghiệp vụ thực tế và ứng dụng AI có kiểm soát**:

- Quản lý vòng đời Ticket (Ticket Lifecycle) đầy đủ, có nhánh Từ chối/Tạm chờ/Hủy/Reopen — không chỉ là workflow tuyến tính đơn giản.
- **Tích hợp AI phân loại & đề xuất Priority theo mô hình hybrid (rule-based + ML)**, có cơ chế human-in-the-loop và feedback loop để cải thiện mô hình theo thời gian.
- **Cơ chế xác nhận xử lý xong (Resolution Confirmation) bắt buộc trước khi đóng ticket**, thay cho đánh giá chất lượng dịch vụ — đảm bảo ticket chỉ đóng khi người dùng thực sự xác nhận sự cố đã hết.
- Phân quyền theo vai trò (RBAC) với 4 nhóm người dùng.
- Cơ chế Priority = Impact × Urgency dựa trên ma trận quyết định chuẩn ITIL, không phải phép tính "hộp đen".
- Quản lý SLA (Service Level Agreement) với cảnh báo vi phạm, có tính đến thời gian tạm dừng.
- Dashboard trực quan, biểu đồ thống kê (Recharts), bao gồm chỉ số đánh giá chất lượng AI và tỷ lệ xác nhận đóng ticket.
- Lưu lịch sử hoạt động (Audit Log), lịch sử chuyển ticket và lịch sử gọi AI.
- Báo cáo hiệu suất xử lý theo nhân viên/phòng ban/thời gian.
- Kiến trúc RESTful API theo chuẩn doanh nghiệp, AI Service tách biệt (microservice-lite) dễ mở rộng/thay thế mô hình.

## 17. Hướng dẫn cài đặt & chạy dự án

> Cập nhật phần này theo cấu trúc thực tế của repo khi triển khai.

### 17.1 Yêu cầu môi trường

- Java 21 (JDK)
- Node.js ≥ 18 & npm/yarn
- Python ≥ 3.11 (cho AI Service)
- MySQL 8
- Maven
- Git

### 17.2 Backend (Spring Boot)

```bash
git clone <repository-url>
cd helpdesk-system/backend

# cấu hình kết nối database trong application.yml
# spring.datasource.url=jdbc:mysql://localhost:3306/helpdesk_db
# ai.service.base-url=http://localhost:8000

mvn spring-boot:run
```

Mặc định API chạy tại: `http://localhost:8080/api`
Tài liệu API (Swagger): `http://localhost:8080/swagger-ui.html`

### 17.3 AI Service (Python FastAPI)

```bash
cd helpdesk-system/ai-service
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt

# huấn luyện mô hình baseline (nếu chưa có model đã lưu)
python train_model.py

# chạy AI service
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Mặc định AI Service chạy tại: `http://localhost:8000`
Tài liệu API AI (Swagger tự sinh của FastAPI): `http://localhost:8000/docs`

### 17.4 Frontend (React + Vite)

```bash
cd helpdesk-system/frontend
npm install
npm run dev
```

Mặc định frontend chạy tại: `http://localhost:5173`

### 17.5 Docker (khuyến nghị để chạy đồng thời cả 3 service)

```bash
docker-compose up -d
```

## 18. Cấu trúc thư mục dự án

```
helpdesk-system/
├── backend/
│   ├── src/main/java/.../controller
│   ├── src/main/java/.../service
│   ├── src/main/java/.../repository
│   ├── src/main/java/.../entity
│   ├── src/main/java/.../dto
│   ├── src/main/java/.../client      (AI Service client)
│   ├── src/main/java/.../security    (JWT, RBAC)
│   ├── src/main/resources/db/migration  (Flyway)
│   └── pom.xml
├── ai-service/
│   ├── main.py                 (FastAPI app, endpoint /classify)
│   ├── train_model.py          (script huấn luyện offline)
│   ├── model/                  (file model đã huấn luyện: .pkl / .bin)
│   ├── preprocessing.py        (tách từ, chuẩn hóa văn bản tiếng Việt)
│   ├── priority_matrix.py      (tra bảng Impact × Urgency)
│   └── requirements.txt
├── frontend/
│   ├── src/pages
│   ├── src/components
│   ├── src/hooks
│   ├── src/services (Axios/TanStack Query)
│   └── package.json
├── docs/
│   ├── use-cases.md
│   ├── ERD.png
│   └── README.md
└── docker-compose.yml
```

## 19. Kế hoạch thực hiện

| Giai đoạn | Nội dung | Thời gian |
|---|---|---|
| Tuần 1 | Khảo sát yêu cầu, phân tích nghiệp vụ, thu thập/mô phỏng dữ liệu ticket cho huấn luyện AI | 1 tuần |
| Tuần 2 | Thiết kế Use Case, ERD, cơ sở dữ liệu, thiết kế ma trận Priority | 1 tuần |
| Tuần 3 | Xây dựng baseline AI (rule-based + TF-IDF/Logistic Regression), đóng gói AI Service FastAPI | 1 tuần |
| Tuần 4–5 | Phát triển Backend (REST API, JWT, RBAC, tích hợp gọi AI Service, ràng buộc xác nhận xử lý xong) | 2 tuần |
| Tuần 6–7 | Phát triển Frontend, tích hợp API, hiển thị gợi ý AI trên form tạo ticket, màn hình xác nhận xử lý xong | 2 tuần |
| Tuần 8 | Kiểm thử, đánh giá độ chính xác AI, tối ưu giao diện | 1 tuần |
| Tuần 9 | Hoàn thiện báo cáo và chuẩn bị bảo vệ | 1 tuần |

## 20. Hướng phát triển

### Giai đoạn 2
- Gửi Email Notification.
- Thông báo thời gian thực bằng WebSocket (bao gồm nhắc nhở xác nhận xử lý xong khi sắp hết hạn N ngày).
- Tích hợp lịch làm việc.

### Giai đoạn 3 – Knowledge Base
- Lưu các lỗi đã xử lý.
- Tìm kiếm hướng dẫn.
- Tái sử dụng kinh nghiệm xử lý, kết hợp gợi ý từ AI Service khi phát hiện ticket tương tự trong Knowledge Base.

### Giai đoạn 4 – Mở rộng ứng dụng AI
- AI gợi ý người xử lý phù hợp (dựa trên tải công việc + lịch sử xử lý category tương tự).
- AI tóm tắt nội dung Ticket.
- Chatbot hỗ trợ người dùng giải quyết lỗi phổ biến.
- Nâng cấp mô hình phân loại từ baseline TF-IDF lên PhoBERT fine-tuned khi có đủ dữ liệu thực tế.
- Cơ chế **auto-retrain định kỳ** dựa trên `ai_classification_logs`.

### Giai đoạn 5 – Nâng cấp kiến trúc
- Docker hóa toàn bộ hệ thống (đã áp dụng ngay từ đầu cho AI Service).
- Triển khai trên Cloud (AWS/Azure/GCP), có thể tách AI Service ra GPU instance riêng nếu nâng cấp mô hình.
- CI/CD tự động, bao gồm pipeline retrain và deploy mô hình AI.
- Redis Cache, RabbitMQ.
- Chuyển đổi sang kiến trúc Microservices.

## 21. Kết luận

Đề tài **"Xây dựng Hệ thống Helpdesk quản lý yêu cầu hỗ trợ kỹ thuật trong doanh nghiệp có tích hợp AI"** có tính thực tiễn cao, phản ánh đúng quy trình hỗ trợ kỹ thuật đang được áp dụng trong nhiều doanh nghiệp, đồng thời bắt kịp xu hướng ứng dụng AI vào ITSM đang phổ biến trên các nền tảng thương mại. Hệ thống giúp số hóa toàn bộ quy trình tiếp nhận, phân công, xử lý và theo dõi yêu cầu hỗ trợ, **giảm tải công đoạn phân loại thủ công bằng AI**, đồng thời **đảm bảo ticket chỉ được đóng khi người dùng xác nhận sự cố đã thực sự được xử lý xong** — thay vì thu thập đánh giá chất lượng dịch vụ, hệ thống tập trung vào tính đúng đắn của việc đóng ticket, góp phần nâng cao hiệu quả làm việc của bộ phận IT và giảm tình trạng ticket bị đóng sớm khi lỗi chưa hết.

Về mặt kỹ thuật, đề tài giúp vận dụng kiến thức đa lĩnh vực: thiết kế cơ sở dữ liệu quan hệ, xây dựng RESTful API, xác thực và phân quyền bằng JWT, tổ chức Backend theo kiến trúc nhiều lớp với Spring Boot, phát triển giao diện Web hiện đại bằng React, thiết kế ràng buộc nghiệp vụ chặt chẽ cho bước xác nhận xử lý xong, và xử lý ngôn ngữ tự nhiên (NLP) tiếng Việt để xây dựng mô hình phân loại văn bản — đây là điểm nhấn giúp đồ án có chiều sâu kỹ thuật hơn so với một hệ thống CRUD thông thường.

Với khả năng mở rộng theo nhiều hướng (AI gợi ý người xử lý, tóm tắt ticket, Knowledge Base, Cloud, Microservices), đề tài phù hợp cho học phần Thực tập cơ sở và có thể tiếp tục phát triển thành đồ án tốt nghiệp hoặc dự án phần mềm doanh nghiệp hoàn chỉnh.

---
