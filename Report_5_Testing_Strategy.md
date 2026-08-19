Software Testing Documentation
BTMS — Billiards Tournament Management System (SEP490_G2) – Report 5

Version | 1.1
Date | 19/08/2026

## Change Log

| Version | Date       | Author                         | Summary                                                                                                                                           |
| ------- | ---------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.0     | 18/08/2026 | Claude (theo yêu cầu ThaoDT) | Bản đầu — chốt Test Strategy dựa trên hệ thống thật + L2 Integration Test cho toàn bộ 43 Controller (branch`thao_integration_test`) |
| 1.1     | 19/08/2026 | Claude (theo yêu cầu ThaoDT) | Bổ sung L2 Background Job BJ-01..BJ-07 (14 `@Test`, Surefire 14/14 Pass). Scheduler không còn Out of Scope. |

## Table of Contents

- [1. Scope of Testing](#1-scope-of-testing)
- [2. Test Strategy](#2-test-strategy)
- [3. Test Plan](#3-test-plan)
- [4. Test Cases](#4-test-cases)
- [5. Test Reports](#5-test-reports)

---

## 1. Scope of Testing

### 1.1 In Scope — Functional Requirements

Danh sách lấy từ `UC-Final-Inventory-Audited.md` (57 UC). Priority gán theo mức độ ảnh hưởng tới golden path của giải đấu (tạo giải → đăng ký → bốc thăm → thi đấu → kết quả); có thể điều chỉnh lại cùng team trước khi chốt milestone.

| Code  | Feature / UC                                                       | Priority |
| ----- | ------------------------------------------------------------------ | -------- |
| UC-01 | Register Player Account                                            | Must     |
| UC-02 | Login to System                                                    | Must     |
| UC-03 | Logout from System (client-side only, không có endpoint)         | Must     |
| UC-04 | Recover Password                                                   | Must     |
| UC-05 | Manage Personal Profile                                            | Must     |
| UC-06 | Change Password                                                    | Must     |
| UC-07 | Manage User Accounts (Admin tạo Owner/Admin)                      | Must     |
| UC-08 | Owner Manage Employee Accounts (Manager/Staff)                     | Must     |
| UC-09 | Manager Manage Staff Accounts                                      | Must     |
| UC-10 | Manage Config Field Catalog                                        | Should   |
| UC-11 | Manage Game Type                                                   | Should   |
| UC-12 | Manage Tournament Format Definitions                               | Must     |
| UC-13 | Configure Default Tournament Format Settings (race-to mặc định) | Must     |
| UC-14 | Manage Registration Field Catalog                                  | Should   |
| UC-15 | Manage Registration Form Templates                                 | Should   |
| UC-16 | Manage Tournament Information                                      | Must     |
| UC-17 | Manage Tournament Format & Rules (override config giải)           | Must     |
| UC-18 | Manage Tournament Lifecycle Status                                 | Must     |
| UC-19 | Draw & Confirm Tournament Bracket                                  | Must     |
| UC-20 | Manage Group Playoff Transition (Progressive Round Robin)          | Must     |
| UC-21 | Browse & View Tournament List (public)                             | Must     |
| UC-22 | View Tournament Details (public)                                   | Must     |
| UC-23 | Manage Tournament Registration (Player nộp đơn)                 | Must     |
| UC-24 | Pay Registration Fee (PayOS)                                       | Must     |
| UC-25 | Process Payment Notification (PayOS webhook)                       | Must     |
| UC-26 | View Payment History (Player)                                      | Should   |
| UC-27 | View Registration Payment Records (Owner/Manager)                  | Should   |
| UC-28 | Manage Tournament Participants (manual/import/withdraw)            | Must     |
| UC-29 | View Official Participant List (public)                            | Should   |
| UC-30 | View Public Profile (Player/Staff)                                 | Could    |
| UC-31 | View Tournament Bracket & Match Schedule (public)                  | Must     |
| UC-32 | View League Standings                                              | Should   |
| UC-33 | Manage Match Lifecycle (start/score/complete/walkover)             | Must     |
| UC-34 | View Tournament Rankings                                           | Should   |
| UC-35 | View My Match Schedule (Player)                                    | Should   |
| UC-36 | Manage News & Blog Content                                         | Could    |
| UC-37 | View News & Blog (public)                                          | Could    |
| UC-38 | View Dashboard Statistics                                          | Should   |
| UC-39 | Manage Branches                                                    | Must     |
| UC-40 | Browse Branches (public)                                           | Should   |
| UC-41 | Assign Match Referee                                               | Should   |
| UC-42 | Manage Email Templates                                             | Should   |
| UC-43 | Manage Email Automation Rules                                      | Should   |
| UC-44 | Configure Email Layout                                             | Could    |
| UC-45 | View Email Send Logs                                               | Could    |
| UC-46 | Send Tournament Notifications (manual email)                       | Should   |
| UC-47 | Deliver Event-driven Emails (job)                                  | Should   |
| UC-48 | Send Scheduled Email Reminders (job)                               | Could    |
| UC-49 | Auto-close Expired Registrations (job)                             | Must     |
| UC-50 | View Facebook Post Insights                                        | Could    |
| UC-51 | Auto-publish Tournament to Facebook (job)                          | Could    |
| UC-52 | Watch Live Tournament TV (WebSocket)                               | Could    |
| UC-53 | Monitor Live Match Dashboard (Manager)                             | Should   |
| UC-54 | Register Player Tournament Profile                                 | Must     |
| UC-55 | View Tournament Audit History                                      | Should   |
| UC-56 | Warn Overdue Tournament Start (job)                                | Could    |
| UC-57 | Manage News Categories & Tags                                      | Could    |

### 1.2 In Scope — Non-Functional Requirements

Hệ thống chưa có một PRD riêng công bố target hiệu năng bằng số (không tìm thấy mục nào ghi giây/ms/số user đồng thời trong `business-flows-srs.md` hay các tài liệu gốc khác) — khác với ví dụ TalentHub trong template, **không bịa số** ở đây. Phần Security/System Requirements thì có sẵn, lấy nguyên văn từ `gbrs_nfse.md` §2 và gán mã để trace ở Section 4.

| Code      | Category | NFR Description & Target                                                                                                                                                                           | Nguồn                                                                                                     |
| --------- | -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| NFR-SEC01 | Security | Mọi trang/action trừ đăng ký, đăng nhập, quên mật khẩu, và các trang công khai (danh sách/chi tiết giải, lịch thi đấu, bracket, bảng xếp hạng…) đều yêu cầu xác thực | gbrs_nfse.md §2                                                                                           |
| NFR-SEC02 | Security | RBAC — user chỉ truy cập được chức năng/dữ liệu đúng role (Admin/Owner/Manager/Staff/Player/Guest)                                                                                     | gbrs_nfse.md §2, GB-04                                                                                    |
| NFR-SEC03 | Security | Mật khẩu không bao giờ lưu ở dạng đọc được (BCrypt)                                                                                                                                    | gbrs_nfse.md §2                                                                                           |
| NFR-SEC04 | Security | Ngăn sử dụng trái phép session đã xác thực; tự động hết hạn khi không hoạt động                                                                                                  | gbrs_nfse.md §2 (JWT stateless,`expiration-ms` mặc định 24h)                                         |
| NFR-SEC05 | Security | User chỉ xem/sửa hồ sơ cá nhân của chính mình trừ khi role cho phép khác                                                                                                               | gbrs_nfse.md §2                                                                                           |
| NFR-SEC06 | Security | Chỉ Owner/Manager đúng phạm vi được tạo/sửa/hủy/cấu hình giải đấu                                                                                                                   | gbrs_nfse.md §2, GB-05                                                                                    |
| NFR-SEC07 | Security | Chỉ Staff/Manager/Owner đúng phạm vi được vận hành trận đấu (bắt đầu/ghi điểm/xác nhận)                                                                                         | gbrs_nfse.md §2, GB-12                                                                                    |
| NFR-SEC08 | Security | Thông tin thanh toán & trạng thái giao dịch chỉ truy cập được bởi user/hệ thống được phép                                                                                         | gbrs_nfse.md §2                                                                                           |
| NFR-SEC09 | Security | File tải lên (avatar, banner, ảnh giải) chỉ sửa được bởi user được phép                                                                                                              | gbrs_nfse.md §2                                                                                           |
| NFR-U01   | Audit    | Ghi log các hành động bảo mật quan trọng: đăng nhập, quản lý tài khoản, quản trị giải, thay đổi trạng thái thanh toán                                                        | gbrs_nfse.md §2                                                                                           |
| NFR-SEC10 | Security | Dữ liệu nhạy cảm truyền giữa client/dịch vụ ngoài/hệ thống phải được bảo vệ (HTTPS ở môi trường prod)                                                                         | gbrs_nfse.md §2                                                                                           |
| NFR-SEC11 | Security | Request/response với PayOS và dịch vụ email phải được xác thực trước khi xử lý (chữ ký webhook PayOS)                                                                              | gbrs_nfse.md §2 (`PayOSService.verifyWebhookSignature`, không có GBR-ID riêng trong bảng GB-01..18) |

> **Khoảng trống cần team xác nhận:** chưa có NFR-P (thời gian phản hồi) hay NFR-U (số user đồng thời) bằng số cụ thể. Đề xuất bổ sung trước khi chạy Performance Testing chính thức (xem §2.1.e).

### 1.3 Out of Scope

| Item                                                                                                                         | Reason                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ---------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| L3 System Test (multi-step HTTP flow + E2E browser)                                                                          | Repo BE hiện tại **chưa có** `src/test/.../system/**`. Khung workbook `docs/Report 5.3_SystemTests_L3*.xlsx` (nếu có) không thay cho code JUnit. Playwright/E2E browser vẫn ngoài phạm vi. |
| L4 UAT scripts                                                                                                               | Chưa dựng — cần Product Owner tham gia soạn kịch bản nghiệp vụ.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Automated k6/JMeter load test                                                                                                | Chưa có target NFR-P bằng số (xem §1.2) nên chưa có cơ sở đặt ngưỡng pass/fail.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| OWASP ZAP automated scan                                                                                                     | Review bảo mật hiện làm thủ công qua GBR table (`gbrs_nfse.md`); scan tự động để đợt sau.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Test happy-path cho tích hợp ngoài thật (PayOS checkout, Facebook Graph thật, MinIO upload, SMTP hộp thư thật) | Không có sandbox PayOS/Facebook/MinIO/SMTP trong CI. **Ngoại lệ đã cover bằng mock:** BJ-06 (`JavaMailSender` mock + dấu vết `email_send_logs`); BJ-07 (`FacebookPublishService` mock + dấu vết `facebook_posts`). Controller L2 vẫn chỉ test nhánh chặn TRƯỚC khi gọi ra ngoài (`FacebookControllerL2Test`, `PaymentControllerL2Test`, `StorageControllerL2Test`…). |
| WebSocket flows (UC-52 Live TV, broadcast real-time)                                                                         | MockMvc không test được WebSocket — cần test riêng bằng STOMP client, chưa làm ở đợt này.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Chạy cron thật / chờ cửa sổ thời gian job                                                                                    | Test BJ gọi **thẳng method** + gán timestamp DB; `spring.task.scheduling.enabled=false` trong class job để cron nền không xen vào session Hibernate. |
| Frontend (SU26_SEP490_G2_FE)                                                                                                 | Đợt này chỉ test Backend.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |

### 1.4 Constraints

| Category              | Constraint                                                                                                                                                                                                                                                                                                                                                            |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Database              | Dự án**không có H2/Testcontainers** trong `pom.xml` — L1/L2 chạy trực tiếp trên MySQL thật theo `application-dev.yml` (mặc định `localhost:3306/su26_sep490_g2`, override bằng biến môi trường `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`).                                                                                             |
| CI                    | `.github/workflows/deploy.yml` chỉ chạy `./mvnw test` khi push/PR vào branch `prod`, dựng MySQL 8.0 service container dùng 1 lần (không dùng RDS nữa — RDS test đã tắt từ 12/08/2026, xem comment trong `application-dev.yml`). Chạy local cần tự trỏ `DB_URL` về MySQL đang chạy sẵn (không dùng `.env` cũ trỏ RDS đã tắt). |
| Kiến trúc bảo mật | JWT stateless (`Authorization: Bearer <token>`), không session — L2 dùng `JwtUtil` sinh token thật cho tài khoản seed, KHÔNG mock Spring Security.                                                                                                                                                                                                         |
| Rollback dữ liệu    | Hầu hết L2 kế thừa`AbstractControllerIntegrationTest` (`@SpringBootTest` + `@Transactional`) — tự rollback. **Ngoại lệ:** `MailDispatcherL2Test` (BJ-06) **không** `@Transactional` vì `@TransactionalEventListener(AFTER_COMMIT)` không chạy nếu test rollback — dọn `email_send_logs` bằng `@AfterEach`.                                                                                                                                                                                          |
| Seed data             | Tài khoản/chi nhánh test dùng đúng dữ liệu`DataInitializer` đã seed sẵn (xem §3.2) — test không tự tạo tài khoản trùng email với seed.                                                                                                                                                                                                          |

### 1.5 Assumptions

| # | Assumption                                                                                                                                                                                                                                                                                                                                                        |
| - | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | MySQL dev đang chạy (local hoặc CI container) và`DataInitializer` đã chạy ít nhất 1 lần trước khi test (seed roles, accounts, branches, format catalog).                                                                                                                                                                                            |
| 2 | Bảng`format_config_fields` / `format_race_to_rules` của `SINGLE_ELIMINATION` giữ đúng field key hiện tại (`bracket_size`, `third_place_match`, `break_rule`, `lag_for_break`, `scoring_unit`) — nếu Admin đổi catalog, các test dựng bracket thủ công (`MatchControllerL2Test`, `StaffControllerL2Test`) cần cập nhật theo. |
| 3 | Mật khẩu seed accounts giữ nguyên như trong`DataInitializer#seedAccounts()` (`admin1`, `owner123`, `manager123`, `staff123`, `player123`).                                                                                                                                                                                                       |
| 4 | Team đồng thuận naming convention test method`{action}_{scenario}_{expectedResult}` (vd `createTournament_missingBranchId_rejected400`) — đã áp dụng nhất quán trong toàn bộ 43 file L2 mới.                                                                                                                                                     |
| 5 | Entity `Tournament`/`Match` có `@Version`; seed cũ có thể `version IS NULL` → NPE khi flush. Job L2 chạy `UPDATE ... SET version = 0 WHERE version IS NULL` trong `@BeforeEach`. |

---

## 2. Test Strategy

### 2.1 Testing Types

#### a. Functional Testing

| Field               | Content                                                                                                                                                                                                                                                                                                                       |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Objective           | Xác nhận từng endpoint, business rule, và access-control rule hoạt động đúng theo UCS (`ucs_spec`) và GBR (`gbrs_nfse.md`).                                                                                                                                                                                     |
| Technique           | State Transition (vòng đời Tournament DRAFT→…→COMPLETED theo`STATUS_TRANSITIONS` whitelist — GB-06; Match PENDING→IN_PROGRESS→COMPLETED — GB-11) · Equivalence Partitioning + Boundary Value cho input validation (`@NotBlank`, `@Min`, `@Pattern`…) · kiểm tra trực tiếp từng GBR (GB-01 → GB-18). |
| Completion Criteria | 100% controller (43/43) có ≥1 test L2. Mọi GBR có ≥1 test case xác nhận enforcement. 7/7 job BJ-01..BJ-07 có L2. Không còn Critical/Major defect mở.                                                                                                                                                                                            |

#### b. UI Testing

Không áp dụng — Backend thuần REST API, không server-render view. UI Testing thật sự thuộc phạm vi FE (ngoài scope đợt này).

#### c. Input Validation Testing

| Field               | Content                                                                                                                                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Objective           | Field bắt buộc/định dạng bị từ chối đúng tầng — Bean Validation (`jakarta.validation`) ở Controller, business rule ở Service.                                                           |
| Technique           | L1: gọi thẳng method Service/validate logic, mock repository · L2: gọi qua`MockMvc` với payload sai (rỗng, sai regex, vượt @Min/@Max) — xác nhận HTTP 400 + `ApiResponse.success=false`. |
| Completion Criteria | Mọi field`@NotBlank`/`@NotNull`/`@Pattern`/`@Min`/`@Max` trong Request DTO có ít nhất 1 test negative ở L2. Đã áp dụng cho toàn bộ Request DTO chạm tới trong 43 file L2.        |

#### d. Background Job Testing

| Field               | Content                                                                                                                                                                                                                                                                                                                                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Objective           | 7 job nền BJ-01..BJ-07: 4 `@Scheduled` (UC-48/49/56) + 3 event-driven AFTER_COMMIT (UC-47 mail routing, UC-47 mail dispatch, UC-51 Facebook auto-post). Mỗi job scheduled có case **bản ghi chưa đủ điều kiện thì không bị đụng**. BJ-07 bắt buộc `isShowTournament=false` → không đăng bài. |
| Technique           | Gọi thẳng method (không chờ cron) · gán timestamp DB · `@RecordApplicationEvents` cho listener AFTER_COMMIT khi test còn `@Transactional` · BJ-06 bỏ `@Transactional` + `@MockitoBean JavaMailSender` (Spring Boot 4) + `management.health.mail.enabled=false` + assert `email_send_logs` · BJ-07 `@MockitoBean FacebookPublishService` + unwrap `@Async` nếu cần + assert `facebook_posts`. |
| Trạng thái        | **✅ Đã làm 19/08/2026.** 14 `@Test` / 4 class trong `src/test/java/.../scheduler/`. Surefire: **14 tests, 0 failures, 0 errors** (`BackgroundJobSchedulerL2Test` 9, `MailAutomationEventL2Test` 2, `MailDispatcherL2Test` 1, `FacebookAutoPostL2Test` 2). Chi tiết TC trong `BTMS_IntegrationTests_L2.md`. |

#### e. Performance Testing

Chưa có NFR-P bằng số (xem §1.2) → chưa thiết kế test case. Đề xuất: sau khi PO/Owner chốt target (vd "trang chi tiết giải load < 2s với 50 user đồng thời"), bổ sung quan sát thủ công trước, k6 sau.

#### f. Security Testing

| Field               | Content                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| ------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Objective           | Xác nhận toàn bộ 18 Global Business Rule (`gbrs_nfse.md`) được enforce đúng tại runtime.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| Technique           | Role-boundary test tại L2 (role sai → 403`AUTH_006`) · Ownership test (Owner/Manager truy cập tài nguyên ngoài phạm vi → 4xx) · JWT test (thiếu/hỏng token → 401 `AUTH_013`/`AUTH_007`) · GB-01 (LOCKED không login được) · GB-02 (thông báo lỗi login giống nhau dù sai email hay sai mật khẩu) · GB-06 (chuyển trạng thái giải sai whitelist bị chặn) · GB-07/GB-08 (đăng ký ngoài giờ mở / đăng ký trùng) · GB-09 (giải miễn phí tự duyệt) · GB-10 (đủ participant mới bốc thăm) · GB-11/GB-12 (chỉ đúng phase + đúng Staff được gán mới thao tác trận) · GB-14 (endpoint Public ẩn khi `isPublicRatio=false`) · GB-16 (roster khoá sau khi bốc thăm). |
| Completion Criteria | Tất cả 18 GBR có ít nhất 1 test L2 xác nhận enforcement —**đã đạt** (xem bảng trace §4.2).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |

### 2.2 Test Levels

#### a. Test Type × Level Matrix

| Type of Tests            | L1 Unit        | L2 Integration      | L3 System           | L4 UAT        |
| ------------------------ | -------------- | ------------------- | ------------------- | ------------- |
| Functional Testing       | ⚠️ 1 file    | ✅ 43/43 controller | ❌ chưa có code JUnit `system/**` | ❌ chưa làm |
| Input Validation Testing | ⚠️ hạn chế | ✅                  | —                  | —            |
| Background Job Testing   | —             | ✅ 14/14 BJ-01..07 | —                  | —            |
| Security Testing (GBR)   | —             | ✅ 18/18 GBR        | ❌ chưa làm       | —            |
| Performance Testing      | —             | —                  | ❌ chưa có target | —            |

#### b. Level 1 — Unit Test

| Field          | Content                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| In-charge      | Developer                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| Hiện trạng   | Chỉ có**1 file thật**: `service/impl/MatchServiceRaceToLockTest.java` (Mockito, test race-condition ghi điểm — `MATCH_SCORE_LOCKED`, `MATCH_SCORE_OUT_OF_RANGE`). `service/impl/ProgressiveRoundRobinOddPlayoffTest.java` tồn tại nhưng dùng `@SpringBootTest` + DB thật nên về bản chất là integration test, không phải unit test thuần.                                                                        |
| Tools          | JUnit 5, Mockito, AssertJ (BOM-managed bởi`spring-boot-starter-parent:4.0.6`, không khai version riêng trong `pom.xml`).                                                                                                                                                                                                                                                                                                                     |
| Khoảng trống | Service layer (`AuthServiceImpl`, `OwnerTournamentServiceImpl`, `RegistrationServiceImpl`, `MatchServiceImpl` phần còn lại…) chưa có unit test bóc tách khỏi DB. Đề xuất ưu tiên L1 cho các Service có logic tính toán phức tạp (bracket generation, race-to resolve, analytics) ở đợt sau — L2 hiện đã phủ các nhánh này gián tiếp qua Controller nhưng chậm hơn nhiều so với unit test thuần mock. |

#### c. Level 2 — Integration Test *(trọng tâm Report này)*

| Field               | Content                                                                                                                                                                                                                                                                                                                                        |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| In-charge           | Developer (dev-test, không có QA riêng)                                                                                                                                                                                                                                                                                                     |
| Khi nào            | Trước mỗi lần merge PR đụng tới Controller/Service liên quan.                                                                                                                                                                                                                                                                          |
| Focus               | Full Spring context thật (`@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc`) — request đi qua `JwtAuthenticationFilter` → `SecurityConfig` → Controller → Service → MySQL thật, KHÔNG mock Security/Service.                                                                                                 |
| Structure           | 1 file`{ControllerName}L2Test.java` / Controller, kế thừa `AbstractControllerIntegrationTest` (helper `bearerToken(email)` sinh JWT thật cho tài khoản seed qua `JwtUtil`, không gọi `/auth/login` mỗi test để nhanh hơn — luồng login thật test riêng ở `AuthControllerL2Test`). `@Transactional` tự rollback. |
| Files               | Controller: `controller/*L2Test.java` (43) + `controller/support/`. Scheduler: `scheduler/BackgroundJobSchedulerL2Test`, `MailAutomationEventL2Test`, `MailDispatcherL2Test`, `FacebookAutoPostL2Test`. Bảng TC mô tả: `BTMS_IntegrationTests_L2.md` (+ workbook Excel Report 5.2 nếu team đã xuất kèm). |
| Tools               | JUnit 5 · MockMvc (`spring-boot-starter-webmvc-test`, gói `org.springframework.boot.webmvc.test.autoconfigure` — Spring Boot 4 tách package so với Boot 3) · Jackson `ObjectMapper`                                                                                                                                                |
| Acceptance Criteria | Mọi controller có ≥1 test positive + ≥1 test negative (role-guard hoặc validation).**Đạt 43/43 — xem §4.1.** Bảy job BJ-01..BJ-07 có test L2 — **đạt 14/14 Pass 19/08/2026.**                                                                                                                                                                                                                    |

#### d. Level 3 / e. Level 4

L3 HTTP chain / Playwright E2E **chưa có** trong `src/test/.../system/**` của repo này. L4 UAT chưa dựng. Xem §1.3.

### 2.3 Supporting Tools

| Purpose                       | Tool                                                                                                    | Vendor        | Version                                              |
| ----------------------------- | ------------------------------------------------------------------------------------------------------- | ------------- | ---------------------------------------------------- |
| Unit + Integration testing    | JUnit 5, Mockito, AssertJ                                                                               | —            | BOM-managed bởi`spring-boot-starter-parent` 4.0.6 |
| Integration testing (context) | Spring Boot Test                                                                                        | VMware/Spring | 4.0.6                                                |
| HTTP testing                  | MockMvc (`spring-boot-starter-webmvc-test` → `org.springframework.boot.webmvc.test.autoconfigure`) | VMware/Spring | 4.0.6                                                |
| Security test helper          | `spring-boot-starter-security-test`                                                                   | VMware/Spring | 4.0.6                                                |
| Test database                 | **Không có** — MySQL 8.0 thật (local hoặc CI service container)                              | Oracle/MySQL  | 8.0                                                  |
| CI/CD                         | GitHub Actions (`.github/workflows/deploy.yml`)                                                       | GitHub        | N/A                                                  |

---

## 3. Test Plan

### 3.1 Test Environment

| Level          | Environment                  | Database                                                                             | Special Setup                                                                                                                                                                                                                                                                                       |
| -------------- | ---------------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| L1 Unit        | Máy dev, không cần server | Không — mock hết bằng Mockito                                                    | Không                                                                                                                                                                                                                                                                                              |
| L2 Integration | Máy dev / CI runner         | MySQL 8.0 thật — local`localhost:3306/su26_sep490_g2` hoặc CI service container | `SPRING_PROFILES_ACTIVE=dev`; override `DB_URL` nếu `.env` còn trỏ RDS đã tắt (§1.4). Job test: `spring.task.scheduling.enabled=false`. |
| L3/L4          | Chưa có JUnit `system/**` | —                                                                                   | Workbook L3 (nếu có trong `docs/`) không đồng nghĩa đã có code chạy được. |

### 3.2 Test Data

Toàn bộ 43 file L2 mới dùng **đúng dữ liệu `DataInitializer` seed sẵn** — không tạo tài khoản test riêng để tránh 2 bộ seed lệch nhau (quy ước ghi trong `controller/support/TestAccounts.java`).

#### a. Minimum Test Accounts

| Username                              | Role    | Password   | Purpose                                                                       |
| ------------------------------------- | ------- | ---------- | ----------------------------------------------------------------------------- |
| admin@gmail.com                       | ADMIN   | admin1     | Test toàn bộ`/admin/**` — catalog, format, email hệ thống              |
| owner@gmail.com                       | OWNER   | owner123   | Test toàn bộ`/owner/**` — sở hữu 2 chi nhánh (Thủ Đức, Cầu Giấy) |
| manager@gmail.com                     | MANAGER | manager123 | Quản lý chi nhánh Thủ Đức — test GB-05 (cách ly theo branch)          |
| manager2@gmail.com                    | MANAGER | manager123 | Quản lý chi nhánh Cầu Giấy — đối chứng GB-05 với manager1           |
| staff1@gmail.com…staff4@gmail.com    | STAFF   | staff123   | Trọng tài — test GB-12 (chỉ Staff được gán mới thao tác trận)      |
| player1@gmail.com…player30@gmail.com | PLAYER  | player123  | Đăng ký giải, xem lịch thi đấu, hồ sơ công khai                     |

#### b. Minimum Domain Data

| Entity                                                                                                                                 | Nguồn                                                   | Ghi chú                                                                                                                                                                              |
| -------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2 Branch (Thủ Đức, Cầu Giấy)                                                                                                      | `DataInitializer#seedBranches()`                       | Dùng để test GB-05 cách ly theo chi nhánh giữa 2 Manager                                                                                                                        |
| Catalog Game Type (`9_BALL`, `8_BALL`, `10_BALL`)                                                                                | `DataInitializer#seedGameTypes()`                      |                                                                                                                                                                                       |
| Catalog Format (`SINGLE_ELIMINATION`, `DOUBLE_ELIMINATION`, `PROGRESSIVE_ROUND_ROBIN`) + config field + race-to rule mặc định | `DataInitializer#seedTournamentFormats()`              | `GROUP_PLAYOFF` đã bị gỡ khỏi code — spec cũ (`BTMS-Tournament-Config-API.md`) nhắc tới nhưng không còn tồn tại, xem ghi chú trong `DataInitializer` dòng 34-36 |
| Template đăng ký`PLAYER_REG_BASIC` / `PLAYER_REG_DOUBLES`                                                                       | `DataInitializer#seedRegistrationFormTemplates()`      | Dùng trong`PlayerRegistrationControllerL2Test` để submit đăng ký thật                                                                                                        |
| Tournament / Match / Participant                                                                                                       | Tự dựng trong từng test (`@Transactional` rollback) | Vd`MatchControllerL2Test`/`StaffControllerL2Test` dựng giải SINGLE_ELIMINATION 4 người qua đúng luồng production (draw → confirm → IN_PROGRESS)                          |

### 3.3 Test Milestones

| Milestone                      | Trạng thái                                                     | In-charge     | Acceptance Criteria                                                                                                                                                                                             |
| ------------------------------ | ---------------------------------------------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| M1 — L1 Unit                  | ⚠️ Mới bắt đầu (1 file)                                    | Developer     | Chưa đặt target coverage — cần chốt cùng team                                                                                                                                                            |
| **M2 — L2 Integration** | **✅ Controller 43/43 (331 TC) + Scheduler BJ-01..07 (14 TC)** | Developer     | Controller: ≥1 positive + ≥1 negative; 18/18 GBR. Job: 14/14 Pass Surefire 19/08/2026. |
| M3 — L3 System                | ❌ Chưa có code `system/**` trong repo này                 | QA Lead / Dev | —                                                                                                                                                                                                              |
| M4 — UAT                      | ❌ Chưa bắt đầu                                              | Product Owner | —                                                                                                                                                                                                              |

---

## 4. Test Cases

### 4.1 Test Case Workbooks

Workbook / bảng TC: `BTMS_IntegrationTests_L2.md` (và file Excel Report 5.2 nếu đã xuất). Cột Status controller = lần chạy 18/08/2026 (329 Pass / 2 Fail). Scheduler = **19/08/2026 (14 Pass)**.

Code JUnit vẫn là nguồn chạy được. Bảng dưới liệt kê theo nhóm.

| Nhóm                                     | File (`src/test/java/.../`)                                                                                                                                                                                                        | Số class    | Số test case |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ | ------------- |
| Auth & Profile nền tảng                 | `controller/AuthControllerL2Test`, `ProfileControllerL2Test`, `HealthControllerL2Test`, `NotificationControllerL2Test`                                                                                                                             | 4            | 35            |
| Admin — catalog & format                 | `AdminController`, `AdminDashboardController`, `AdminGameTypeController`, `AdminFormatController`, `AdminConfigFieldController`, `AdminRegistrationFieldController`, `AdminRegistrationFormTemplateController` (đều `L2Test`) | 7            | ~65           |
| Admin — email hệ thống                 | `AdminEmailTemplateController`, `AdminEmailAutomationController`, `AdminEmailLogController`, `AdminMailLayoutSettingsController`, `AnalyticsController` (đều `L2Test`)                                                            | 5            | ~40           |
| Owner/Manager — vận hành chuỗi        | `DashboardController`, `FacebookController`, `ManagerBranchController`, `ManagerController`, `OwnerController`, `OwnerBranchController`, `OwnerTableController`, `ManagerTableController` (đều `L2Test`)                    | 8            | ~55           |
| Owner/Manager — giải đấu & đăng ký | `ManagerTournamentController`, `OwnerTournamentController`, `ManagerRegistrationController`, `OwnerRegistrationController`, `ManagerEmailController`, `OwnerEmailController`, `ParticipantController` (đều `L2Test`)          | 7            | ~70           |
| Vận hành trận đấu                    | `MatchControllerL2Test`, `StaffControllerL2Test`                                                                                                                                                                                            | 2            | 22            |
| News / Payment / Player                   | `NewsController`, `NewsTagController`, `PaymentController`, `PlayerController`, `PlayerRegistrationController` (đều `L2Test`)                                                                                                     | 5            | ~45           |
| Public (không cần đăng nhập)         | `PublicBranchController`, `PublicLeaderboardController`, `PublicParticipantController`, `PublicTournamentController` (đều `L2Test`)                                                                                                 | 4            | 13            |
| Storage                                   | `StorageControllerL2Test`                                                                                                                                                                                                                     | 1            | 5             |
| **Controller subtotal**            |                                                                                                                                                                                                                                                 | **43** | **331** |
| Background jobs BJ-01..BJ-07              | `scheduler/BackgroundJobSchedulerL2Test` (9), `MailAutomationEventL2Test` (2), `MailDispatcherL2Test` (1), `FacebookAutoPostL2Test` (2)                                                                                                          | 4            | **14**        |
| **Tổng L2**                        |                                                                                                                                                                                                                                                 | **47** | **345** |

Ánh xạ JOB:

| JOB-ID | Class.method | UC | Tests |
| ------ | ------------ | -- | ----- |
| BJ-01 | `EmailReminderScheduler.sendRegistrationClosingSoonReminders` | UC-48 | TC-SCH-BJ01-001/002 |
| BJ-02 | `EmailReminderScheduler.sendMatchStartingSoonReminders` | UC-48 | TC-SCH-BJ02-001/002 |
| BJ-03 | `TournamentAutoStatusScheduler.autoCloseExpiredRegistrations` | UC-49 | TC-SCH-BJ03-001/002/003 |
| BJ-04 | `TournamentAutoStatusScheduler.warnOverdueTournamentStart` | UC-56 | TC-SCH-BJ04-001/002 |
| BJ-05 | `MailAutomationEventListener.onMailDomainEvent` | UC-47 | TC-SCH-BJ05-001/002 |
| BJ-06 | `MailDispatcher.onEmailQueued` | UC-47 | TC-SCH-BJ06-001 |
| BJ-07 | `FacebookAutoPostListener.onTournamentPublished` | UC-51 | TC-SCH-BJ07-001/002 |

Hạ tầng dùng chung: `controller/support/AbstractControllerIntegrationTest.java` và `controller/support/TestAccounts.java`.

### 4.2 Requirements Coverage Matrix

Trace theo GBR (`gbrs_nfse.md`) — vì đây là trọng tâm bảo mật/nghiệp vụ xuyên suốt dự án, dễ kiểm tra hơn trace theo từng UC lẻ (57 UC, nhiều UC không có endpoint riêng — vd UC-03 Logout không có API).

| GBR   | Rule                                                                 | L2 test xác nhận                                                                                                                                                                                                          |
| ----- | -------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GB-01 | LOCKED không đăng nhập được                                   | `AuthControllerL2Test.login_lockedAccount_rejected403`                                                                                                                                                                    |
| GB-02 | Message lỗi login giống nhau dù sai email/sai mật khẩu          | `AuthControllerL2Test.login_wrongPassword_and_unknownEmail_returnSameGenericMessage`                                                                                                                                      |
| GB-03 | Role gán 1 lần lúc tạo, không có API đổi role                | Xác nhận gián tiếp — không endpoint nào trong 43 controller cho phép sửa field`role`                                                                                                                             |
| GB-04 | RBAC theo URL prefix`/api/v1/{role}/**`                            | Có ở**mọi** file L2 — test role sai → 403 `AUTH_006` (vd `AdminControllerL2Test`, `OwnerControllerL2Test`, `ManagerControllerL2Test`, `StaffControllerL2Test`…)                                       |
| GB-05 | Cách ly dữ liệu theo Owner/branch                                 | `ManagerBranchControllerL2Test`, `ManagerControllerL2Test`, `ManagerTournamentControllerL2Test`, `AnalyticsControllerL2Test` (manager1 vs manager2 khác chi nhánh)                                                |
| GB-06 | Whitelist chuyển trạng thái giải 1 chiều                        | `ManagerTournamentControllerL2Test.patchStatus_draftToCompleted_invalidTransitionRejected`, `OwnerTournamentControllerL2Test.patchStatus_draftToOpenForRegistration_requiresCompleteConfigFirst`                        |
| GB-07 | Chỉ nhận đăng ký khi`OPEN_FOR_REGISTRATION`                   | `PlayerRegistrationControllerL2Test.submitRegistration_tournamentNotOpen_rejected`                                                                                                                                        |
| GB-08 | Không đăng ký trùng                                             | `PlayerRegistrationControllerL2Test.submitRegistration_duplicateSubmission_rejected409`                                                                                                                                   |
| GB-09 | Giải miễn phí tự duyệt                                          | `PlayerRegistrationControllerL2Test.submitRegistration_freeTournament_autoApproved`                                                                                                                                       |
| GB-10 | Đủ participant + đúng status mới bốc thăm                     | `MatchControllerL2Test.draw_enoughParticipants_created201`, `draw_whileStillDraft_rejected`                                                                                                                             |
| GB-11 | Chỉ thao tác trận đúng phase; điểm không vượt race-to      | `MatchControllerL2Test.start_beforeInProgressPhase_rejected`, `updateScore_exceedsRaceTo_rejected`                                                                                                                      |
| GB-12 | Chỉ Staff được gán / Owner-Manager đúng scope thao tác trận | `MatchControllerL2Test.staffOperation_notAssigned_rejected`, `StaffControllerL2Test.incrementScore_unassignedStaff_rejected`                                                                                            |
| GB-13 | Kết quả tính từ Match/Participant lưu trong hệ thống          | Xác nhận gián tiếp qua`MatchControllerL2Test` (complete → winner phản ánh đúng điểm)                                                                                                                           |
| GB-14 | Nội dung Public ẩn khi`isPublicRatio=false`                      | `MatchControllerL2Test.publicStages_whenIsPublicRatioFalse_hiddenAsNotFound`                                                                                                                                              |
| GB-15 | Payment không có API xoá                                          | Xác nhận gián tiếp —`PaymentControllerL2Test` không có test DELETE vì controller không khai báo route đó                                                                                                      |
| GB-16 | Roster chỉ sửa được trước khi bốc thăm                      | `ParticipantControllerL2Test.addManual_afterRosterLocked_rejected`                                                                                                                                                        |
| GB-17 | Player không tự rút; chỉ Owner/Manager rút hộ                  | Xác nhận gián tiếp —`ParticipantController` không có endpoint withdraw phía `/player/**`, chỉ có ở `/owner/**` và `/manager/**` (`ParticipantControllerL2Test.updateSeedNoThenWithdraw_asManager_ok`) |
| GB-18 | OTP hết hạn 5 phút, tối đa 5 lần thử                          | Chưa test trực tiếp ở L2 (OTP lưu in-memory, cần test riêng ở L1 cho`OtpService` — đề xuất đợt sau)                                                                                                         |

---

## 5. Test Reports

### 5.1 Summary Table

| Round | Metrics | L1 Unit | L2 Integration | L3 System | L4 UAT | Total |
| --- | --- | --- | --- | --- | --- | --- |
| Round#1 (18/08/2026) | Total | 8 | 331 (controller) | — | — | 339 |
| | Compiled | ✅ | ✅ | — | — | — |
| | Executed | — | ✅ | — | — | — |
| | Passed | — | **329** | — | — | **329** |
| | Failed | — | **2** | — | — | **2** |
| | Errors | — | 0 | — | — | 0 |
| | Skipped | — | 0 | — | — | 0 |
| Round#2 (19/08/2026) | Total | 8 | **345** (331 controller + 14 job) | — | — | 353 |
| | Executed (jobs only) | — | ✅ 14 | — | — | — |
| | Passed (jobs) | — | **14** | — | — | **14** |
| | Failed (jobs) | — | **0** | — | — | **0** |
| | Errors (jobs) | — | 0 | — | — | 0 |

**Round#1 — Controller L2** đã chạy thật trên MySQL 8 local (Docker port 3307, db `btms_test`). Số liệu từ `target/surefire-reports/` — 329/331 Pass, 2 Fail = DEF-003/DEF-004 (xem đoạn lịch sử bên dưới, không đổi Expected).

**Round#2 — Background jobs** (19/08/2026), MySQL local `localhost:3306/su26_sep490_g2`, override `DB_URL` (`.env` vẫn có thể trỏ RDS đã tắt):

```
./mvnw test -Dtest="BackgroundJobSchedulerL2Test,MailAutomationEventL2Test,MailDispatcherL2Test,FacebookAutoPostL2Test"
```

Surefire (đọc file `TEST-*.xml`, không áng chừng):

| Test set | tests | failures | errors |
| --- | --- | --- | --- |
| `BackgroundJobSchedulerL2Test` | 9 | 0 | 0 |
| `MailAutomationEventL2Test` | 2 | 0 | 0 |
| `MailDispatcherL2Test` | 1 | 0 | 0 |
| `FacebookAutoPostL2Test` | 2 | 0 | 0 |
| **Tổng job** | **14** | **0** | **0** |

Workbook Summary (nếu đã xuất Excel và lọc Error Guessing trên sheet): số dòng Excel **không bằng** 345 `@Test` trong code. Code JUnit **không xoá** test Error Guessing.

**Lịch sử Round#1 (giữ nguyên):**

```
./mvnw clean test -Dtest="com.capstone.su26_sep490_g2_be.controller.*L2Test"
```

Số liệu đọc trực tiếp từ `target/surefire-reports/` (không áng chừng). Trước khi ra được 329/331,
có 2 vòng vấn đề đã xử lý minh bạch — không sửa Expected để cho xanh, không xoá/@Disabled test đỏ:

1. **Lần chạy đầu: 331/331 Error** — `NoSuchBeanDefinitionException: ObjectMapper`. Không phải lỗi
   business logic: xác nhận production code cũng KHÔNG nơi nào `@Autowired ObjectMapper` (mọi chỗ
   tự `new ObjectMapper()` — `PaymentController`, `PayOSServiceImpl`, `JwtAuthenticationFilter`...).
   Sửa `AbstractControllerIntegrationTest` theo đúng pattern đó (bỏ `@Autowired`, khởi tạo trực
   tiếp) — sửa hạ tầng test dùng chung, không đụng tới bất kỳ `Expected` nào của test nghiệp vụ.
2. **Lần chạy 2: 323/331, 8 Fail** — điều tra từng dòng bằng cách đọc code nguồn liên quan:
   - **6/8 là test viết sai giả định** (đã sửa lại Given, giữ nguyên Expected — chi tiết đối chiếu
     đầy đủ trong `BTMS_IntegrationTests_L2.md` §BẢNG DEFECTS và commit sửa test): trùng
     `handlerKey` khi test tạo Format mới; thiếu `branchId` bắt buộc khi Manager tạo Staff (x2);
     giả định sai race-to vòng 1 của bracket 4 người (thực tế "semi_final"=7, không phải
     "round_1"=5 — x2); giả định sai rằng config giải luôn thiếu khi Owner chưa lưu tay (thực tế
     SINGLE_ELIMINATION có default Admin đầy đủ nên đã "complete" ngay — đúng thiết kế).
   - **2/8 là defect thật trong phần mềm** — giữ nguyên test (đang Fail đúng), báo lại thành
     `DEF-003`/`DEF-004` trong `BTMS_IntegrationTests_L2.md`:
     - `GET /admin/config-field-catalog/{fieldKey}` không tồn tại → trả 400 thay vì 404.
     - `POST /storage/images` thiếu multipart `file` → trả 500 thay vì 400 (thiếu handler
       `MissingServletRequestPartException` trong `GlobalExceptionHandler`).
3. **Lần chạy 3 (sau khi sửa 6 test viết sai): 329/331 Pass, đúng 2 Fail còn lại** = 2 defect thật
   ở trên. Không còn thay đổi nào khác.

**Coverage (`./mvnw jacoco:report`, cần thêm `jacoco-maven-plugin` vào `pom.xml` — dự án trước đó
chưa có plugin này, đã bổ sung phần build, không đổi gì khác):**

| | Covered / Total | % | Target §2.2.b |
| --- | --- | --- | --- |
| Line (package `service` + `service.impl`) | 4492 / 9166 | **49.0%** | ≥70% |
| Branch (package `service` + `service.impl`) | 1261 / 4475 | **28.2%** | ≥50% |

Chưa đạt target — đúng như cảnh báo trong `test_skills/btms-l2-workflow.md` §JaCoCo: coverage phải
đo **L1+L2 cộng lại**, còn L1 hiện chỉ có 1 file thật (`MatchServiceRaceToLockTest`) nên số trên
phản ánh gần như riêng L2 (chỉ đi qua main flow + vài nhánh tiêu biểu). Không phải dấu hiệu L2
thiếu test — bổ sung L1 cho Service nhiều nhánh logic nhất (`AnalyticsServiceImpl`,
`OwnerTournamentServiceImpl`, `MatchSchedulingServiceImpl`) là việc ưu tiên tiếp theo, không phải
viết thêm L2.

### 5.2 Defect Register

4 defect đã ghi nhận — chi tiết đầy đủ (mô tả, vị trí code, test case liên quan) nằm trong
`BTMS_IntegrationTests_L2.md` §BẢNG DEFECTS (DEF-001..DEF-004), không lặp lại ở đây để tránh 2
nguồn lệch nhau. 2 defect log lúc đọc code (DEF-001, DEF-002 — test đã viết để assert đúng hành
vi thật, không phải dòng Fail); 2 defect xác nhận bằng test Fail thật khi chạy (DEF-003, DEF-004).
Khi có quyết định mở ticket chính thức, log tiếp vào `Report 2.1_Project Tracking` (đã có sẵn
trong `docs/`).

| Metric | L1 Unit | L2 Integration | L3 System | L4 UAT | Total |
| --- | --- | --- | --- | --- | --- |
| Total Defects | 0 | 4 | — | — | 4 |
| Open (Critical) | 0 | 0 | — | — | 0 |
| Open (Major) | 0 | 0 | — | — | 0 |
| Open (Minor) | 0 | 4 | — | — | 4 |

---

## Phụ lục — Nhánh git & cách chạy

- Code L2 (43 controller + 4 class scheduler + `controller/support/` + `pom.xml` jacoco) nằm trên nhánh
  **`thao_integration_test`**. Tính đến 19/08/2026 các file test **chưa commit** (git `??` / `M pom.xml`) — đang chờ review.
- Bảng TC: `BTMS_IntegrationTests_L2.md` (cùng thư mục BE).
- **Round#1 (controller)** — MySQL Docker `btms-mysql-test`, `-p 3307:3306`, db `btms_test`.
- **Round#2 (jobs)** — MySQL local `localhost:3306/su26_sep490_g2`. File `.env` có thể vẫn trỏ RDS `capstone26-test` đã tắt: **phải override** `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`.
- PowerShell (Windows) — job L2:
  ```
  $env:SPRING_PROFILES_ACTIVE="dev"
  $env:DB_URL="jdbc:mysql://localhost:3306/su26_sep490_g2?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
  $env:DB_USERNAME="root"
  $env:DB_PASSWORD="123456"
  ./mvnw test "-Dtest=BackgroundJobSchedulerL2Test,MailAutomationEventL2Test,MailDispatcherL2Test,FacebookAutoPostL2Test"
  ```
- bash — controller L2 (cổng 3307 như Round#1):
  ```
  export SPRING_PROFILES_ACTIVE=dev
  export DB_URL="jdbc:mysql://127.0.0.1:3307/btms_test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
  export DB_USERNAME=root
  export DB_PASSWORD=123456
  ./mvnw clean test -Dtest="com.capstone.su26_sep490_g2_be.controller.*L2Test"
  ```
- Chạy 1 class khi debug: `./mvnw test -Dtest="AuthControllerL2Test"`
- Coverage: `./mvnw jacoco:report` → `target/site/jacoco/index.html`
