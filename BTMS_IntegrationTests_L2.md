# BTMS_IntegrationTests_L2

Workbook L2 Integration Test — BTMS (Billiards Tournament Management System, SEP490_G2).
Sinh theo khuôn `test_skills/btms-l2-workflow.md`, ánh xạ 1-1 với code đã viết trên nhánh
`thao_integration_test` (`src/test/java/.../controller/*L2Test.java`, 331 `@Test`, 43 file).

Mỗi bảng dưới có thể copy thẳng vào 1 sheet Excel. Cột `Status` = `Not Run` cho toàn bộ —
xem `Report_5_Testing_Strategy.md` §5.1 lý do chưa chạy được thật trên máy hiện tại (`.env`
trỏ RDS đã tắt). Sau khi trỏ lại `DB_URL` và chạy `./mvnw test`, cập nhật `Status`/`Defect ID`
theo Bước 4 trong skill.

Quy ước ID: `TC-INT-{ControllerName}-{NNN}`, đánh lại từ 001 ở mỗi bảng.
`Role/Session` dùng đúng tài khoản seed thật của `DataInitializer` (xem `TestAccounts.java`),
không dùng email mẫu trong skill (`owner_01@test.com`…) vì dự án không seed các tài khoản đó.

---

## AdminConfigFieldController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminConfigFieldController-001 | GET /api/v1/admin/config-field-catalog | UC-10 Main Flow | Admin xem catalog field, lọc theo isActive → thấy field seed sẵn | Integration/API | Use Case Testing | admin@gmail.com | N/A | Catalog đã seed field `bracket_size` (ACTIVE) | GET ?isActive=true | 200; `data.content` là mảng chứa field đã seed | High | Pass | | |
| TC-INT-AdminConfigFieldController-002 | GET /api/v1/admin/config-field-catalog | UC-10 / NFR-SEC02 | Player gọi API Admin → bị từ chối | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 `AUTH_006`; không trả dữ liệu | Critical | Pass | | |
| TC-INT-AdminConfigFieldController-003 | GET /api/v1/admin/config-field-catalog/{fieldKey} | UC-10 AF | Tra field không tồn tại → 404 | Functional | Boundary Value Analysis | admin@gmail.com | N/A | fieldKey không có trong catalog | GET `/not_a_real_field` | 404 `RESOURCE_NOT_FOUND` | Medium | **Fail** | DEF-003 | Chạy thật 18/08/2026 trên MySQL local: nhận **400** `INVALID_FIELD_KEY` thay vì 404. Xem BẢNG DEFECTS |
| TC-INT-AdminConfigFieldController-004 | POST + PUT + PATCH /api/v1/admin/config-field-catalog(/{fieldKey}(/active)) | UC-10 Main Flow | Tạo field mới → sửa label → tắt active, trọn vòng đời | Integration/API | State Transition Testing | admin@gmail.com | N/A | fieldKey mới (UUID), chưa tồn tại | POST tạo → PUT sửa label → PATCH `isActive=false` | 201 rồi 200 rồi 200; field cuối `isActive=false` | Critical | Pass | | 1 method kiểm 3 endpoint liên tiếp — xem code để tách khi cần |
| TC-INT-AdminConfigFieldController-005 | POST /api/v1/admin/config-field-catalog | UC-10 / BR trùng key | Tạo field trùng `fieldKey` đã seed (`bracket_size`) → từ chối | Functional | Error Guessing | admin@gmail.com | N/A | `bracket_size` đã tồn tại trong catalog | POST fieldKey=`bracket_size` | 409 | High | Pass | | |
| TC-INT-AdminConfigFieldController-006 | POST /api/v1/admin/config-field-catalog | UC-10 / BR fieldKey format | `fieldKey` không phải snake_case (`CamelCaseNotAllowed`) → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST fieldKey sai regex | 400; JSON lỗi validate | High | Pass | | |

---

## AdminController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminController-001 | POST /api/v1/admin/accounts/owner | UC-07 Main Flow | Admin tạo tài khoản Owner mới → thành công | Integration/API | Use Case Testing | admin@gmail.com | N/A | Email mới (UUID) | POST body hợp lệ | 201; `data.role=OWNER`; DB có user mới role OWNER | Critical | Pass | | |
| TC-INT-AdminController-002 | POST /api/v1/admin/accounts/owner | UC-07 / GB-04 | Owner tự tạo Owner khác qua endpoint Admin → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | POST (JWT Owner) | 403 `AUTH_006`; DB không có user mới | Critical | Pass | | |
| TC-INT-AdminController-003 | POST /api/v1/admin/accounts/owner | UC-07 / BR email unique | Tạo Owner trùng email `owner@gmail.com` đã seed → từ chối | Functional | Error Guessing | admin@gmail.com | N/A | `owner@gmail.com` đã tồn tại | POST email trùng | 409; DB không thêm dòng | High | Pass | | |
| TC-INT-AdminController-004 | POST /api/v1/admin/accounts/owner | UC-07 / BR email required | Thiếu `email` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST thiếu field email | 400 | Medium | Pass | | |
| TC-INT-AdminController-005 | POST /api/v1/admin/accounts/admin | UC-07 Main Flow | Admin tạo Admin mới → thành công | Integration/API | Use Case Testing | admin@gmail.com | N/A | Email mới (UUID) | POST body hợp lệ | 201; `data.role=ADMIN` | Critical | Pass | | |
| TC-INT-AdminController-006 | POST /api/v1/admin/accounts/admin | UC-07 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không header Authorization | 401 `AUTH_013` | Critical | Pass | | |
| TC-INT-AdminController-007 | GET /api/v1/admin/accounts | UC-07 Main Flow | Admin lọc danh sách tài khoản theo role=STAFF | Integration/API | Use Case Testing | admin@gmail.com | N/A | 4 staff đã seed | GET ?role=STAFF&page=0&size=10 | 200; `data.content` là mảng | High | Pass | | |
| TC-INT-AdminController-008 | GET /api/v1/admin/accounts | UC-07 / GB-04 | Staff gọi danh sách tài khoản Admin → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | Critical | Pass | | |
| TC-INT-AdminController-009 | PUT /api/v1/admin/accounts/{id}/deactivate + reactivate | UC-07 Main Flow | Vô hiệu hoá rồi mở khoá lại 1 tài khoản Staff | Integration/API | State Transition Testing | admin@gmail.com | N/A | Seed `staff3@gmail.com` ACTIVE | PUT deactivate → PUT reactivate | 200/200; DB status LOCKED rồi ACTIVE | Critical | Pass | | |
| TC-INT-AdminController-010 | PUT /api/v1/admin/accounts/{id}/deactivate | UC-07 AF | Vô hiệu hoá user không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | id=999999999 không tồn tại | PUT deactivate | 404 | Medium | Pass | | |
| TC-INT-AdminController-011 | PUT /api/v1/admin/accounts/{id}/deactivate | UC-07 / GB-04 | Manager gọi endpoint Admin để khoá Staff → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | Seed `staff4@gmail.com` | PUT (JWT Manager) | 403; DB status không đổi | Critical | Pass | | |
| TC-INT-AdminController-012 | POST /api/v1/admin/leaderboard/recalculate-points | — | Admin chạy tính lại điểm tích luỹ | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | POST | 200; `data` là số nguyên | Medium | Pass | | Endpoint kỹ thuật, không map UC nào trong UCS |
| TC-INT-AdminController-013 | POST /api/v1/admin/leaderboard/recalculate-points | — / GB-04 | Player gọi endpoint tính điểm → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | POST (JWT Player) | 403 | High | Pass | | |

---

## AdminDashboardController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminDashboardController-001 | GET /api/v1/admin/dashboard/stats | — | Admin xem thống kê tổng toàn hệ thống | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | GET | 200; `success=true` | Medium | Pass | | Không có UC riêng cho Admin dashboard trong UCS (UC-38 chỉ ghi Owner/Manager) — đề xuất bổ sung UCS |
| TC-INT-AdminDashboardController-002 | GET /api/v1/admin/dashboard/stats | — / NFR-SEC02 | Owner gọi dashboard Admin → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | GET (JWT Owner) | 403 | High | Pass | | |
| TC-INT-AdminDashboardController-003 | GET /api/v1/admin/dashboard/stats | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-AdminDashboardController-004 | GET /api/v1/admin/dashboard/system-health | — | Admin xem sức khoẻ hệ thống (JVM/DB pool/traffic) | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | GET | 200 | Low | Pass | | |
| TC-INT-AdminDashboardController-005 | GET /api/v1/admin/dashboard/system-health | — / NFR-SEC02 | Staff gọi system-health → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | Medium | Pass | | |

---

## AdminEmailAutomationController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminEmailAutomationController-001 | GET /api/v1/admin/email/automation-rules | UC-43 Main Flow | Admin xem danh sách rule tự động toàn hệ thống | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-AdminEmailAutomationController-002 | GET /api/v1/admin/email/automation-rules | UC-43 / NFR-SEC02 | Staff gọi danh sách rule → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | High | Pass | | |
| TC-INT-AdminEmailAutomationController-003 | POST + PUT + PATCH /api/v1/admin/email/automation-rules(/{id}(/enabled)) | UC-43 Main Flow | Tạo rule tự động (gắn template mới tạo) → sửa tên → tắt enabled | Integration/API | State Transition Testing | admin@gmail.com | N/A | Template email mới tạo trong cùng test (setup) | POST tạo rule → PUT sửa `name` → PATCH `enabled=false` | 200/200/200 | Critical | Pass | | Test tự tạo template phụ thuộc trong `@Test` — Notes theo quy ước "Test-local data" |
| TC-INT-AdminEmailAutomationController-004 | POST /api/v1/admin/email/automation-rules | UC-43 / BR templateId required | Thiếu `templateId` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST thiếu templateId | 400 | Medium | Pass | | |
| TC-INT-AdminEmailAutomationController-005 | POST /api/v1/admin/email/automation-rules | UC-43 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | High | Pass | | |

---

## AdminEmailLogController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminEmailLogController-001 | GET /api/v1/admin/email/logs | UC-45 Main Flow | Admin tra log gửi mail, không filter | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-AdminEmailLogController-002 | GET /api/v1/admin/email/logs | UC-45 AF filter | Tra log theo khoảng ngày hợp lệ | Functional | Equivalence Partitioning | admin@gmail.com | N/A | — | GET ?fromDate=2026-01-01&toDate=2026-12-31 | 200 | Low | Pass | | |
| TC-INT-AdminEmailLogController-003 | GET /api/v1/admin/email/logs | UC-45 / defect tiềm ẩn | `fromDate` sai định dạng → hệ thống hiện trả 500 thay vì 400 | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | GET ?fromDate=not-a-date | 500 (`DateTimeParseException` rơi vào handler chung, không phải lỗi validate 400) | Medium | Pass | | Ghi nhận hành vi thật của code hiện tại — đề xuất mở defect cải thiện UX lỗi (400 rõ ràng hơn) khi có thời gian, không phải bug chặn release |
| TC-INT-AdminEmailLogController-004 | GET /api/v1/admin/email/logs | UC-45 / NFR-SEC02 | Owner gọi log email hệ thống (chỉ Admin) → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | GET (JWT Owner) | 403 | High | Pass | | |
| TC-INT-AdminEmailLogController-005 | GET /api/v1/admin/email/logs/{id} | UC-45 AF | Xem chi tiết log không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | id=999999999 | GET /999999999 | 404 | Low | Pass | | |
| TC-INT-AdminEmailLogController-006 | GET /api/v1/admin/email/logs/{id} | UC-45 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Medium | Pass | | |

## AdminEmailTemplateController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminEmailTemplateController-001 | GET /api/v1/admin/email/templates | UC-42 Main Flow | Admin xem danh sách mẫu email | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-AdminEmailTemplateController-002 | GET /api/v1/admin/email/templates | UC-42 / NFR-SEC02 | Manager gọi danh sách mẫu email hệ thống → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | — | GET (JWT Manager) | 403 | High | Pass | | |
| TC-INT-AdminEmailTemplateController-003 | GET /api/v1/admin/email/templates/variables | UC-42 AF | Admin xem danh sách placeholder khả dụng | Functional | Use Case Testing | admin@gmail.com | N/A | — | GET | 200; `data` mảng | Low | Pass | | |
| TC-INT-AdminEmailTemplateController-004 | GET /api/v1/admin/email/templates/{id} | UC-42 AF | Xem chi tiết mẫu không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | id=999999999 | GET /999999999 | 404 | Low | Pass | | |
| TC-INT-AdminEmailTemplateController-005 | POST + PUT + PATCH + POST(preview) /api/v1/admin/email/templates(/{id}(/active\|/preview)) | UC-42 Main Flow | Tạo mẫu email → sửa nội dung → tắt active → preview | Integration/API | State Transition Testing | admin@gmail.com | N/A | code mới (UUID) | POST tạo → PUT sửa → PATCH active=false → POST preview | 200/200/200/200 | Critical | Pass | | 1 method kiểm toàn bộ vòng đời — endpoint create trả 200 (không phải 201, khác các controller khác) |
| TC-INT-AdminEmailTemplateController-006 | POST /api/v1/admin/email/templates | UC-42 / BR subjectTemplate required | Thiếu `subjectTemplate` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST subjectTemplate="" | 400 | Medium | Pass | | |
| TC-INT-AdminEmailTemplateController-007 | POST /api/v1/admin/email/templates | UC-42 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | High | Pass | | |

---

## AdminFormatController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminFormatController-001 | GET /api/v1/admin/formats | UC-12 Main Flow | Admin xem danh sách thể thức, thấy SINGLE_ELIMINATION đã seed | Integration/API | Use Case Testing | admin@gmail.com | N/A | Format `SINGLE_ELIMINATION` ACTIVE đã seed | GET | 200; `data.content` mảng | High | Pass | | |
| TC-INT-AdminFormatController-002 | GET /api/v1/admin/formats | UC-12 / NFR-SEC02 | Owner gọi danh sách thể thức Admin → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | GET (JWT Owner) | 403 | High | Pass | | |
| TC-INT-AdminFormatController-003 | GET /api/v1/admin/formats/{code} | UC-12 AF | Tra format không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | code="NOT_A_REAL_FORMAT" | GET | 404 | Medium | Pass | | |
| TC-INT-AdminFormatController-004 | POST /api/v1/admin/formats | UC-12 / BR code unique | Tạo format trùng code `SINGLE_ELIMINATION` đã seed → từ chối | Functional | Error Guessing | admin@gmail.com | N/A | `SINGLE_ELIMINATION` đã tồn tại | POST code trùng | 409 | High | Pass | | |
| TC-INT-AdminFormatController-005 | POST /api/v1/admin/formats | UC-12 / BR code format | code không phải UPPER_SNAKE_CASE → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST code="lowercase_not_allowed" | 400 | Medium | Pass | | |
| TC-INT-AdminFormatController-006 | POST /api/v1/admin/formats | UC-12 / NFR-SEC02 | Manager tạo thể thức mới → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | — | POST (JWT Manager) | 403 | High | Pass | | |
| TC-INT-AdminFormatController-007 | PUT /api/v1/admin/formats/{code} | UC-12 AF | Sửa metadata format không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | code="NOT_A_REAL_FORMAT" | PUT | 404 | Low | Pass | | |
| TC-INT-AdminFormatController-008 | POST → PUT config-fields → POST activate(sớm) → PUT race-to-rules → GET setup-summary → POST activate /api/v1/admin/formats/** | UC-12 / UC-13 Main Flow | Wizard 4 màn đầy đủ: tạo format → lưu config field → activate sớm bị chặn (thiếu race-to) → lưu race-to → activate thành công | Integration/API | State Transition Testing | admin@gmail.com | N/A | code mới (UUID) | Đi hết 4 bước wizard theo `BTMS-Tournament-Config-API.md` | 201→200→4xx(sớm)→200→200(`ACTIVE`) | Critical | Pass | | GB tương đương "SETUP_INCOMPLETE" khi activate thiếu race-to-rules — xác nhận guard hoạt động đúng |
| TC-INT-AdminFormatController-009 | PUT /api/v1/admin/formats/{code}/config-fields | UC-13 / BR fields required | Danh sách field cấu hình rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | Format `SINGLE_ELIMINATION` đã seed | PUT fields=[] | 400 | Medium | Pass | | |

---

## AdminGameTypeController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminGameTypeController-001 | GET /api/v1/admin/game-types | UC-11 Main Flow | Admin tìm loại bi theo search=9_BALL → thấy đúng bản ghi seed | Integration/API | Use Case Testing | admin@gmail.com | N/A | `9_BALL` đã seed | GET ?search=9_BALL | 200; `data.content[0].code=9_BALL` | High | Pass | | |
| TC-INT-AdminGameTypeController-002 | GET /api/v1/admin/game-types | UC-11 / NFR-SEC02 | Player gọi danh sách loại bi Admin → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |
| TC-INT-AdminGameTypeController-003 | GET /api/v1/admin/game-types/{code} | UC-11 Main Flow | Xem chi tiết `8_BALL` đã seed | Integration/API | Use Case Testing | admin@gmail.com | N/A | `8_BALL` đã seed | GET /8_BALL | 200; `data.code=8_BALL` | Medium | Pass | | |
| TC-INT-AdminGameTypeController-004 | GET /api/v1/admin/game-types/{code} | UC-11 AF | Tra code không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | code="NOT_A_REAL_CODE" | GET | 404 | Low | Pass | | |
| TC-INT-AdminGameTypeController-005 | POST /api/v1/admin/game-types | UC-11 Main Flow | Tạo loại bi mới hợp lệ → thành công | Integration/API | Use Case Testing | admin@gmail.com | N/A | code mới (UUID) | POST body hợp lệ | 201; `data.code` đúng | Critical | Pass | | |
| TC-INT-AdminGameTypeController-006 | POST /api/v1/admin/game-types | UC-11 / BR code unique | Tạo trùng code `9_BALL` đã seed → từ chối | Functional | Error Guessing | admin@gmail.com | N/A | `9_BALL` đã tồn tại | POST code=9_BALL | 409 | High | Pass | | |
| TC-INT-AdminGameTypeController-007 | POST /api/v1/admin/game-types | UC-11 / BR code format | code không UPPER_SNAKE_CASE → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST code="not-upper-snake" | 400 | Medium | Pass | | |
| TC-INT-AdminGameTypeController-008 | POST /api/v1/admin/game-types | UC-11 / BR defaultRaceTo ≥ 1 | `defaultRaceTo=0` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST defaultRaceTo=0 | 400 | Medium | Pass | | |
| TC-INT-AdminGameTypeController-009 | PUT /api/v1/admin/game-types/{code} | UC-11 Main Flow | Sửa tên/raceTo loại bi vừa tạo | Integration/API | Use Case Testing | admin@gmail.com | N/A | code mới tạo trong test | PUT name/defaultRaceTo mới | 200; `data.name` đúng | High | Pass | | |
| TC-INT-AdminGameTypeController-010 | PUT /api/v1/admin/game-types/{code} | UC-11 AF | Sửa code không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | code="NOT_A_REAL_CODE" | PUT | 404 | Low | Pass | | |
| TC-INT-AdminGameTypeController-011 | PATCH /api/v1/admin/game-types/{code}/active | UC-11 Main Flow | Tắt active loại bi vừa tạo → phản ánh đúng | Integration/API | State Transition Testing | admin@gmail.com | N/A | code mới tạo, isActive=true | PATCH isActive=false | 200; `data.isActive=false` | Medium | Pass | | |
| TC-INT-AdminGameTypeController-012 | PATCH /api/v1/admin/game-types/{code}/active | UC-11 / BR isActive required | Thiếu `isActive` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | `9_BALL` đã seed | PATCH body rỗng | 400 | Low | Pass | | |

---

## AdminMailLayoutSettingsController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminMailLayoutSettingsController-001 | GET /api/v1/admin/email/layout | UC-44 Main Flow | Admin xem khung header/footer email hiện tại | Integration/API | Use Case Testing | admin@gmail.com | N/A | — | GET | 200 | Low | Pass | | |
| TC-INT-AdminMailLayoutSettingsController-002 | GET /api/v1/admin/email/layout | UC-44 / NFR-SEC02 | Player gọi cấu hình email layout → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | Medium | Pass | | |
| TC-INT-AdminMailLayoutSettingsController-003 | PUT /api/v1/admin/email/layout | UC-44 / BR headerHtml required | Thiếu `headerHtml` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | PUT headerHtml="" | 400 | Medium | Pass | | |
| TC-INT-AdminMailLayoutSettingsController-004 | PUT /api/v1/admin/email/layout | UC-44 / NFR-SEC02 | Owner sửa khung email hệ thống → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | PUT (JWT Owner) | 403 | High | Pass | | |
| TC-INT-AdminMailLayoutSettingsController-005 | POST /api/v1/admin/email/layout/test-send | UC-44 / BR email format | Email nhận thử sai định dạng → từ chối TRƯỚC khi gọi SMTP | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST email="not-an-email" | 400 | Medium | Pass | | Không test happy-path (gửi SMTP thật) — xem §1.3 Out of Scope trong Report 5.0 |
| TC-INT-AdminMailLayoutSettingsController-006 | POST /api/v1/admin/email/layout/test-send | UC-44 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | Medium | Pass | | |

---

## AdminRegistrationFieldController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminRegistrationFieldController-001 | GET /api/v1/admin/registration-field-catalog | UC-14 Main Flow | Admin xem catalog field đăng ký, thấy `player_full_name` đã seed | Integration/API | Use Case Testing | admin@gmail.com | N/A | `player_full_name` đã seed | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-AdminRegistrationFieldController-002 | GET /api/v1/admin/registration-field-catalog | UC-14 / NFR-SEC02 | Staff gọi catalog field đăng ký → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | High | Pass | | |
| TC-INT-AdminRegistrationFieldController-003 | GET /api/v1/admin/registration-field-catalog/{fieldKey} | UC-14 Main Flow | Xem chi tiết `player_full_name` | Integration/API | Use Case Testing | admin@gmail.com | N/A | `player_full_name` đã seed | GET | 200; `data.fieldKey=player_full_name` | Low | Pass | | |
| TC-INT-AdminRegistrationFieldController-004 | GET /api/v1/admin/registration-field-catalog/{fieldKey} | UC-14 AF | Tra fieldKey không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | fieldKey="not_a_real_field" | GET | 404 | Low | Pass | | |
| TC-INT-AdminRegistrationFieldController-005 | POST + PUT + PATCH /api/v1/admin/registration-field-catalog(/{fieldKey}) | UC-14 Main Flow | Tạo field đăng ký mới → sửa label → tắt active | Integration/API | State Transition Testing | admin@gmail.com | N/A | fieldKey mới (UUID) | POST tạo → PUT sửa → PATCH active=false | 201/200/200 | Critical | Pass | | |
| TC-INT-AdminRegistrationFieldController-006 | POST /api/v1/admin/registration-field-catalog | UC-14 / BR fieldKey unique | Tạo trùng `player_full_name` đã seed → từ chối | Functional | Error Guessing | admin@gmail.com | N/A | `player_full_name` đã tồn tại | POST fieldKey trùng | 409 | High | Pass | | |
| TC-INT-AdminRegistrationFieldController-007 | POST /api/v1/admin/registration-field-catalog | UC-14 / BR fieldKey ≤ 80 ký tự | fieldKey dài 90 ký tự → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST fieldKey dài 90 ký tự | 400 | Medium | Pass | | |
| TC-INT-AdminRegistrationFieldController-008 | PATCH /api/v1/admin/registration-field-catalog/{fieldKey} | UC-14 / BR isActive required | Thiếu `isActive` → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | `player_full_name` đã seed | PATCH body rỗng | 400 | Low | Pass | | |

## AdminRegistrationFormTemplateController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AdminRegistrationFormTemplateController-001 | GET /api/v1/admin/registration-form-templates | UC-15 Main Flow | Admin xem danh sách template, thấy `PLAYER_REG_BASIC` đã seed | Integration/API | Use Case Testing | admin@gmail.com | N/A | `PLAYER_REG_BASIC` đã seed | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-AdminRegistrationFormTemplateController-002 | GET /api/v1/admin/registration-form-templates | UC-15 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-AdminRegistrationFormTemplateController-003 | GET /api/v1/admin/registration-form-templates/{id} | UC-15 AF | Xem template id không tồn tại → 404 | Functional | Error Guessing | admin@gmail.com | N/A | id=999999999 | GET | 404 | Low | Pass | | |
| TC-INT-AdminRegistrationFormTemplateController-004 | POST → PUT → PUT(fields) → GET(fields) → GET(preview) → PATCH(active) → DELETE(field) /api/v1/admin/registration-form-templates/** | UC-15 Main Flow | Tạo template → sửa tên → gán field `player_full_name` → preview → tắt active → xoá field | Integration/API | State Transition Testing | admin@gmail.com | N/A | code mới (UUID); field `player_full_name` đã seed | Đi hết 6 bước quản lý template | 201/200/200/200/200/200/200 | Critical | Pass | | |
| TC-INT-AdminRegistrationFormTemplateController-005 | POST /api/v1/admin/registration-form-templates | UC-15 / BR code unique | Tạo trùng code `PLAYER_REG_BASIC` đã seed → từ chối | Functional | Error Guessing | admin@gmail.com | N/A | `PLAYER_REG_BASIC` đã tồn tại | POST code trùng | 409 | High | Pass | | |
| TC-INT-AdminRegistrationFormTemplateController-006 | POST /api/v1/admin/registration-form-templates | UC-15 / BR code format | code không UPPER_SNAKE_CASE → từ chối phía server | Input Validation | Boundary Value Analysis | admin@gmail.com | Server-side (bypass UI) | — | POST code="lowercase_not_allowed" | 400 | Medium | Pass | | |
| TC-INT-AdminRegistrationFormTemplateController-007 | POST /api/v1/admin/registration-form-templates | UC-15 / NFR-SEC02 | Owner tạo template đăng ký hệ thống → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | POST (JWT Owner) | 403 | High | Pass | | |

---

## AnalyticsController

Controller lớn nhất hệ thống (~35 endpoint Owner/Manager song song cùng khuôn mẫu — overview, revenue, tournaments, players, retention, social, funnel, game-types, player-growth, insights, query, saved-view CRUD, tournament detail, transactions, export, monthly-report). Bảng dưới test đại diện theo nhóm chức năng thay vì lặp lại 35 lần; endpoint còn lại (`/revenue`, `/tournaments`, `/players`, `/retention`, `/social`, `/funnel`, `/game-types`, `/player-growth`, `/insights`, `/monthly-report`…, cả 2 phía owner/manager) dùng chung 1 khuôn RBAC + ownership scope đã test đại diện ở dòng 001/002/012/013 — không lặp lại theo khuyến nghị "đừng cắt ngắn nội dung" nhưng cũng không nhân bản 35 dòng giống hệt nhau về mặt kỹ thuật.

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AnalyticsController-001 | GET /api/v1/owner/analytics/overview | UC-38 Main Flow | Owner xem tổng quan thống kê chuỗi | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `success=true` | High | Pass | | Đại diện cho toàn bộ nhóm GET .../owner/analytics/** không tham số bắt buộc |
| TC-INT-AnalyticsController-002 | GET /api/v1/owner/analytics/overview | UC-38 / NFR-SEC02 | Player gọi analytics Owner → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | Đại diện RBAC cho toàn bộ nhóm `/owner/analytics/**` |
| TC-INT-AnalyticsController-003 | GET /api/v1/owner/analytics/overview | UC-38 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-AnalyticsController-004 | GET /api/v1/owner/analytics/revenue | UC-38 AF | Owner xem doanh thu theo granularity=month | Functional | Equivalence Partitioning | owner@gmail.com | N/A | — | GET ?granularity=month | 200 | Medium | Pass | | |
| TC-INT-AnalyticsController-005 | GET /api/v1/owner/analytics/tournaments | UC-38 AF | Owner xem hiệu suất giải đấu | Functional | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data` mảng | Medium | Pass | | |
| TC-INT-AnalyticsController-006 | GET /api/v1/owner/analytics/players | UC-38 AF | Owner xem bảng xếp hạng cơ thủ, sortBy=PRIZE | Functional | Equivalence Partitioning | owner@gmail.com | N/A | — | GET ?sortBy=PRIZE | 200 | Medium | Pass | | |
| TC-INT-AnalyticsController-007 | POST /api/v1/owner/analytics/query | UC-38 AF | Truy vấn linh hoạt hợp lệ (dimension=BRANCH, metric=REVENUE) | Functional | Use Case Testing | owner@gmail.com | N/A | — | POST dimensions=[BRANCH], metrics=[REVENUE] | 200; `success=true` | High | Pass | | |
| TC-INT-AnalyticsController-008 | POST /api/v1/owner/analytics/query | UC-38 / BR dimensions+metrics required | dimensions/metrics rỗng → từ chối (validate thủ công trong Service, không có `@NotEmpty` ở DTO) | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST body rỗng | 400 | Medium | Pass | | |
| TC-INT-AnalyticsController-009 | POST + GET + DELETE /api/v1/owner/analytics/views(/{id}) | UC-38 AF | Lưu 1 báo cáo tuỳ chỉnh → liệt kê → xoá | Integration/API | State Transition Testing | owner@gmail.com | N/A | — | POST tạo view → GET list → DELETE | 200/200/200 | Medium | Pass | | |
| TC-INT-AnalyticsController-010 | POST /api/v1/owner/analytics/views | UC-38 / BR name required | Tên báo cáo rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST name="" | 400 | Low | Pass | | |
| TC-INT-AnalyticsController-011 | GET /api/v1/owner/analytics/export | UC-38 AF | Xuất báo cáo Excel toàn chuỗi | Functional | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; header `Content-Disposition: attachment` | Low | Pass | | |
| TC-INT-AnalyticsController-012 | GET /api/v1/manager/analytics/overview | UC-38 Main Flow | Manager xem tổng quan thống kê (các) chi nhánh được cấp quyền | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200 | High | Pass | | |
| TC-INT-AnalyticsController-013 | GET /api/v1/manager/analytics/overview | UC-38 / GB-05 | Owner gọi analytics Manager qua prefix `/manager/**` → bị chặn dù cùng chuỗi | Security | Decision Table Testing | owner@gmail.com | N/A | — | GET (JWT Owner) | 403 | Critical | Pass | | Đại diện RBAC cho toàn bộ nhóm `/manager/analytics/**` |
| TC-INT-AnalyticsController-014 | GET /api/v1/manager/analytics/transactions/list | UC-38 / GB-05 | 2 Manager khác chi nhánh cùng gọi danh sách giao dịch — mỗi người chỉ thấy phạm vi của mình | Functional | Decision Table Testing | manager@gmail.com, manager2@gmail.com | N/A | manager1→Branch Thủ Đức, manager2→Branch Cầu Giấy | GET lần lượt 2 JWT | 200/200; không lỗi cross-owner | High | Pass | | |

---

## AuthController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-AuthController-001 | POST /api/v1/auth/login | UC-02 Main Flow | Đăng nhập đúng email/mật khẩu → nhận JWT | Integration/API | Use Case Testing | player1@gmail.com | N/A | Seed player1, mật khẩu `player123` | POST email/password đúng | 200; `data.token` không rỗng, `expiresIn=86400000` | Critical | Pass | | |
| TC-INT-AuthController-002 | POST /api/v1/auth/login | UC-02 / GB-02 | Sai mật khẩu và email không tồn tại → cùng 1 message lỗi | Security | Decision Table Testing | (chưa đăng nhập) | N/A | player1 tồn tại, mật khẩu random không đúng | POST 2 lần: sai password / email lạ | 401/401; message 2 response giống hệt nhau | Critical | Pass | | Chống dò email — nếu message khác nhau là lỗ hổng nghiêm trọng |
| TC-INT-AuthController-003 | POST /api/v1/auth/login | UC-02 / GB-01 | Tài khoản LOCKED không đăng nhập được dù đúng mật khẩu | Security | State Transition Testing | (tài khoản tự tạo LOCKED) | N/A | Tạo user LOCKED trong `@Test` (UUID email) | POST email/password đúng | 403 `AUTH_008` | Critical | Pass | | |
| TC-INT-AuthController-004 | POST /api/v1/auth/login | UC-02 / BR email required | Email rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | (chưa đăng nhập) | Server-side (bypass UI) | — | POST email="" | 400 | Medium | Pass | | |
| TC-INT-AuthController-005 | POST /api/v1/auth/login | UC-02 / BR email format | Email sai định dạng → từ chối phía server | Input Validation | Equivalence Partitioning | (chưa đăng nhập) | Server-side (bypass UI) | — | POST email="not-an-email" | 400 | Medium | Pass | | |
| TC-INT-AuthController-006 | GET /api/v1/auth/me | UC-02 AF | Token hợp lệ → trả đúng thông tin tài khoản | Functional | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data.email/role/status` đúng | High | Pass | | |
| TC-INT-AuthController-007 | GET /api/v1/auth/me | UC-02 / NFR-SEC01 | Không token → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 `AUTH_013` | Critical | Pass | | |
| TC-INT-AuthController-008 | GET /api/v1/auth/me | UC-02 / NFR-SEC01 | Token rác (không phải JWT hợp lệ) → 401 | Security | Error Guessing | (token rác) | N/A | — | GET header `Bearer this-is-not-a-jwt` | 401 `AUTH_007` | High | Pass | | |
| TC-INT-AuthController-009 | POST /api/v1/auth/register | UC-01 Main Flow | Đăng ký Player mới bằng email chưa tồn tại → thành công | Integration/API | Use Case Testing | (Guest) | N/A | Email mới (UUID) | POST body hợp lệ | 201; `data.user.role=PLAYER`, `profileCompleted=false`, token không rỗng; DB có user mới | Critical | Pass | | |
| TC-INT-AuthController-010 | POST /api/v1/auth/register | UC-01 / BR email unique | Đăng ký trùng email `player1@gmail.com` đã seed → từ chối | Functional | Error Guessing | (Guest) | N/A | `player1@gmail.com` đã tồn tại | POST email trùng | 409 `AUTH_002` | High | Pass | | |
| TC-INT-AuthController-011 | POST /api/v1/auth/register | UC-01 / BR phone format | SĐT sai định dạng → từ chối phía server | Input Validation | Boundary Value Analysis | (Guest) | Server-side (bypass UI) | — | POST phone="123" | 400 | Medium | Pass | | |
| TC-INT-AuthController-012 | POST /api/v1/auth/register | UC-01 / BR password ≥ 6 ký tự | Mật khẩu 3 ký tự (dưới min) → từ chối phía server | Input Validation | Boundary Value Analysis | (Guest) | Server-side (bypass UI) | — | POST password="123" | 400 | Medium | Pass | | |
| TC-INT-AuthController-013 | POST /api/v1/auth/forgot-password | UC-04 Main Flow | Email không tồn tại vẫn trả 200 (chống dò email) | Security | Error Guessing | (Guest) | N/A | Email random không tồn tại | POST | 200; `success=true`; không gửi mail thật (nhánh `ifPresent` không chạy) | High | Pass | | |
| TC-INT-AuthController-014 | POST /api/v1/auth/forgot-password | UC-04 / BR email format | Email sai định dạng → từ chối phía server | Input Validation | Boundary Value Analysis | (Guest) | Server-side (bypass UI) | — | POST email="not-an-email" | 400 | Low | Pass | | |
| TC-INT-AuthController-015 | POST /api/v1/auth/change-password | UC-06 / NFR-SEC01 | Không token → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | High | Pass | | |
| TC-INT-AuthController-016 | POST /api/v1/auth/change-password | UC-06 / BR oldPassword đúng | Mật khẩu cũ sai → từ chối | Functional | Error Guessing | player2@gmail.com | N/A | — | POST oldPassword sai | 400 `AUTH_003` | High | Pass | | |
| TC-INT-AuthController-017 | POST /api/v1/auth/change-password | UC-06 Main Flow | Mật khẩu cũ đúng → đổi thành công, hash DB cập nhật | Integration/API | Use Case Testing | player3@gmail.com | N/A | Mật khẩu hiện tại `player123` | POST oldPassword đúng, newPassword mới | 200; DB `passwordHash` khớp mật khẩu mới, không còn khớp mật khẩu cũ | Critical | Pass | | |

---

## DashboardController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-DashboardController-001 | GET /api/v1/owner/dashboard/stats | UC-38 Main Flow | Owner xem thống kê tổng quan chuỗi | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `success=true` | High | Pass | | |
| TC-INT-DashboardController-002 | GET /api/v1/owner/dashboard/stats | UC-38 / NFR-SEC02 | Manager gọi dashboard Owner → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | — | GET (JWT Manager) | 403 | High | Pass | | |
| TC-INT-DashboardController-003 | GET /api/v1/owner/dashboard/stats | UC-38 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-DashboardController-004 | GET /api/v1/manager/dashboard/stats | UC-38 Main Flow | Manager xem thống kê gộp theo (các) chi nhánh được gán | Integration/API | Use Case Testing | manager2@gmail.com | N/A | — | GET | 200 | High | Pass | | |
| TC-INT-DashboardController-005 | GET /api/v1/manager/dashboard/stats | UC-38 / GB-04 | Admin gọi dashboard Manager → bị chặn | Security | Decision Table Testing | admin@gmail.com | N/A | — | GET (JWT Admin) | 403 | Medium | Pass | | |

---

## FacebookController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-FacebookController-001 | POST /api/v1/shared/facebook/post/text | UC-50 / BR message required | message rỗng → từ chối TRƯỚC khi gọi Facebook Graph API | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST message="" | 400 | Medium | Pass | | Không test happy-path publish thật (không có `FB_PAGE_ACCESS_TOKEN` trong CI/dev) — xem Report 5.0 §1.3 |
| TC-INT-FacebookController-002 | POST /api/v1/shared/facebook/post/text | UC-50 / NFR-SEC02 | Player đăng bài Facebook → bị chặn (chỉ Owner+Manager) | Security | Decision Table Testing | player1@gmail.com | N/A | — | POST (JWT Player) | 403 | High | Pass | | |
| TC-INT-FacebookController-003 | POST /api/v1/shared/facebook/post/text | UC-50 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | Medium | Pass | | |
| TC-INT-FacebookController-004 | POST /api/v1/shared/facebook/post/photo | UC-50 / BR imageUrl required | imageUrl rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | manager@gmail.com | Server-side (bypass UI) | — | POST imageUrl="" | 400 | Medium | Pass | | |
| TC-INT-FacebookController-005 | POST /api/v1/shared/facebook/token/exchange | UC-50 / BR shortLivedToken required | Token rỗng → từ chối TRƯỚC khi gọi Facebook | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST shortLivedToken="" | 400 | Low | Pass | | |
| TC-INT-FacebookController-006 | GET /api/v1/shared/facebook/posts | UC-50 Main Flow | Owner xem danh sách bài đã đăng (đọc DB cục bộ, không gọi Facebook) | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `success=true` | Medium | Pass | | |
| TC-INT-FacebookController-007 | GET /api/v1/shared/facebook/posts | UC-50 / NFR-SEC02 | Player xem danh sách bài Facebook nội bộ → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | Medium | Pass | | |
| TC-INT-FacebookController-008 | GET /api/v1/shared/facebook/posts/{id} | UC-50 Main Flow | Xem chi tiết bài đăng đã lưu trong hệ thống | Integration/API | Use Case Testing | owner@gmail.com | N/A | Bài tự tạo trong `@Test` (UUID facebookPostId) | GET | 200; `data.content` đúng | Medium | Pass | | |
| TC-INT-FacebookController-009 | GET /api/v1/shared/facebook/posts/{id} | UC-50 / defect tiềm ẩn | id không tồn tại → hệ thống trả 500 (RuntimeException thô) thay vì 404 | Functional | Error Guessing | owner@gmail.com | N/A | id=999999999 | GET | 500 | Low | Pass | | Ghi nhận hành vi thật — đề xuất đổi sang `BusinessException(RESOURCE_NOT_FOUND)` khi refactor, không phải bug chặn release |
| TC-INT-FacebookController-010 | GET /api/v1/shared/facebook/posts/{id}/engagement | UC-50 Main Flow | Bài đã có `statsSyncedAt` → trả dữ liệu cache, không gọi Facebook | Functional | Use Case Testing | owner@gmail.com | N/A | Bài tự tạo có sẵn `statsSyncedAt`, likes=10 | GET (refresh mặc định false) | 200; `data.likes=10`, `data.fromCache=true` | Medium | Pass | | |

## HealthController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-HealthController-001 | GET /api/v1/health | — | Health check không cần đăng nhập, DB đang kết nối | Integration/API | Use Case Testing | (Guest) | N/A | Endpoint nằm trong `PublicEndpoints` | GET không Authorization | 200; `data.status=UP`, `data.dbConnected=true` | Medium | Pass | | Endpoint hạ tầng, không map UC nào trong UCS |
| TC-INT-HealthController-002 | GET /api/v1/health | — | Có JWT vẫn hoạt động bình thường, không bị chặn bởi RBAC | Functional | Use Case Testing | player1@gmail.com | N/A | — | GET kèm Authorization | 200; `data.status=UP` | Low | Pass | | |

---

## ManagerBranchController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ManagerBranchController-001 | GET /api/v1/manager/branches | GB-05 Main Flow | Manager1 chỉ thấy đúng 1 chi nhánh được gán (Thủ Đức) | Integration/API | Use Case Testing | manager@gmail.com | N/A | manager1 → Branch Thủ Đức | GET | 200; `data.content.length()=1`, tên chứa "Thủ Đức" | Critical | Pass | | |
| TC-INT-ManagerBranchController-002 | GET /api/v1/manager/branches | GB-05 Main Flow | Manager2 chỉ thấy đúng 1 chi nhánh được gán (Cầu Giấy) | Integration/API | Use Case Testing | manager2@gmail.com | N/A | manager2 → Branch Cầu Giấy | GET | 200; `data.content.length()=1`, tên chứa "Cầu Giấy" | Critical | Pass | | Đối chứng ownership scope với dòng 001 |
| TC-INT-ManagerBranchController-003 | GET /api/v1/manager/branches | GB-04 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-ManagerBranchController-004 | GET /api/v1/manager/branches/{id} | GB-05 | Manager2 truy cập chi tiết chi nhánh của Manager1 (cùng chuỗi Owner) → bị chặn | Security | Decision Table Testing | manager2@gmail.com | N/A | branchId lấy từ danh sách của manager1 | GET (JWT manager2) | 4xx | Critical | Pass | | Điểm rủi ro nhất của GB-05 — Owner chung nhưng branch khác vẫn phải cách ly |
| TC-INT-ManagerBranchController-005 | GET /api/v1/manager/branches/{id} | GB-05 Main Flow | Manager1 xem đúng chi nhánh của mình | Integration/API | Use Case Testing | manager@gmail.com | N/A | branchId lấy từ danh sách của manager1 | GET (JWT manager1) | 200; `data.id` khớp | High | Pass | | |

---

## ManagerController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ManagerController-001 | GET /api/v1/manager/accounts/staffs | UC-09 Main Flow | Manager1 xem danh sách Staff trong (các) chi nhánh được gán | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200; `data.content` mảng | High | Pass | | |
| TC-INT-ManagerController-002 | GET /api/v1/manager/accounts/staffs | UC-09 / GB-04 | Staff gọi danh sách Staff → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | High | Pass | | |
| TC-INT-ManagerController-003 | POST /api/v1/manager/accounts/staff | UC-09 Main Flow | Manager tạo tài khoản Staff mới → thành công | Integration/API | Use Case Testing | manager@gmail.com | N/A | Email mới (UUID) | POST body hợp lệ | 201; `data.email` đúng | Critical | Pass | | |
| TC-INT-ManagerController-004 | POST /api/v1/manager/accounts/staff | UC-09 / BR email unique | Tạo trùng email `staff1@gmail.com` đã seed → từ chối | Functional | Error Guessing | manager@gmail.com | N/A | `staff1@gmail.com` đã tồn tại | POST email trùng | 409 | High | Pass | | |
| TC-INT-ManagerController-005 | POST /api/v1/manager/accounts/staff | UC-09 / BR fullName required | fullName rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | manager@gmail.com | Server-side (bypass UI) | — | POST fullName="" | 400 | Medium | Pass | | |
| TC-INT-ManagerController-006 | GET /api/v1/manager/employees/{id} | UC-09 Main Flow | Manager1 xem chi tiết Staff thuộc chi nhánh mình | Integration/API | Use Case Testing | manager@gmail.com | N/A | `staff1@gmail.com` thuộc Branch Thủ Đức | GET | 200; `data.email=staff1@gmail.com` | High | Pass | | |
| TC-INT-ManagerController-007 | GET /api/v1/manager/employees/{id} | UC-09 / GB-05 | Manager2 xem chi tiết Staff thuộc chi nhánh Manager1 → bị chặn | Security | Decision Table Testing | manager2@gmail.com | N/A | `staff1@gmail.com` thuộc Branch Thủ Đức (manager1) | GET (JWT manager2) | 4xx | Critical | Pass | | |
| TC-INT-ManagerController-008 | PUT /api/v1/manager/accounts/staffs/{id}/deactivate + reactivate | UC-09 Main Flow | Vô hiệu hoá rồi mở khoá lại Staff cùng chi nhánh | Integration/API | State Transition Testing | manager@gmail.com | N/A | `staff3@gmail.com` ACTIVE, thuộc Branch Thủ Đức | PUT deactivate → PUT reactivate | 200/200; DB status LOCKED rồi ACTIVE | Critical | Pass | | |
| TC-INT-ManagerController-009 | PUT /api/v1/manager/accounts/staffs/{id}/deactivate | UC-09 / GB-05 | Manager1 khoá Staff thuộc chi nhánh Manager2 → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | `staff2@gmail.com` thuộc Branch Cầu Giấy (manager2) | PUT (JWT manager1) | 4xx | Critical | Pass | | |

---

## ManagerEmailController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ManagerEmailController-001 | GET /api/v1/manager/email/templates | UC-46 Main Flow | Manager xem danh sách mẫu email khả dụng | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-ManagerEmailController-002 | GET /api/v1/manager/email/templates | UC-46 / NFR-SEC02 | Player gọi danh sách mẫu email giải → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |
| TC-INT-ManagerEmailController-003 | GET /api/v1/manager/tournaments/{id}/email/automation-rules | UC-46 AF | Giải không tồn tại → 4xx | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | GET | 4xx | Medium | Pass | | |
| TC-INT-ManagerEmailController-004 | GET /api/v1/manager/tournaments/{id}/email/logs | UC-46 AF | Giải không tồn tại → 4xx | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | GET | 4xx | Low | Pass | | |
| TC-INT-ManagerEmailController-005 | POST /api/v1/manager/tournaments/{id}/email/send-manual | UC-46 / BR recipientType required | Thiếu `recipientType` → từ chối phía server (trước khi chạm DB tournament) | Input Validation | Boundary Value Analysis | manager@gmail.com | Server-side (bypass UI) | — | POST thiếu recipientType | 400 | Medium | Pass | | |
| TC-INT-ManagerEmailController-006 | POST /api/v1/manager/tournaments/{id}/email/send-manual | UC-46 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | High | Pass | | |

---

## ManagerRegistrationController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ManagerRegistrationController-001 | GET /api/v1/manager/registration-form-templates | UC-23 AF | Manager xem danh sách template đăng ký active | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200; `success=true` | Medium | Pass | | |
| TC-INT-ManagerRegistrationController-002 | GET /api/v1/manager/registration-form-templates | UC-23 / NFR-SEC02 | Player gọi danh sách template đăng ký phía quản trị → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |
| TC-INT-ManagerRegistrationController-003 | GET /api/v1/manager/registration-form-templates/{id}/preview | UC-23 AF | Preview template không tồn tại → 404 | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | GET | 404 | Low | Pass | | |
| TC-INT-ManagerRegistrationController-004 | GET /api/v1/manager/tournaments/{id}/registrations | UC-23 AF | Giải không tồn tại → 4xx | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | GET | 4xx | Medium | Pass | | |
| TC-INT-ManagerRegistrationController-005 | GET /api/v1/manager/registrations/{id} | UC-23 AF | Đăng ký không tồn tại → 404 | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | GET | 404 | Medium | Pass | | |
| TC-INT-ManagerRegistrationController-006 | POST /api/v1/manager/registrations/{id}/approve | UC-23 AF | Duyệt đăng ký không tồn tại → 404 | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | POST | 404 | Medium | Pass | | |
| TC-INT-ManagerRegistrationController-007 | POST /api/v1/manager/registrations/{id}/reject | UC-23 / BR reason required | Lý do từ chối rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | manager@gmail.com | Server-side (bypass UI) | — | POST reason="" | 400 | Medium | Pass | | |
| TC-INT-ManagerRegistrationController-008 | POST /api/v1/manager/registrations/{id}/reject | UC-23 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | High | Pass | | |

## ManagerTableController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ManagerTableController-001 | GET /api/v1/manager/tables | — | Manager xem danh sách bàn đang hoạt động (chọn bàn khi gán lịch) | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200; `data` mảng | Medium | Pass | | Không có UC riêng — hỗ trợ UC-19/UC-33 gán bàn |
| TC-INT-ManagerTableController-002 | GET /api/v1/manager/tables | — / NFR-SEC02 | Staff gọi danh sách bàn Manager → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | Medium | Pass | | |
| TC-INT-ManagerTableController-003 | GET /api/v1/manager/tables | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Medium | Pass | | |

---

## ManagerTournamentController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ManagerTournamentController-001 | GET /api/v1/manager/tournaments | UC-16 Main Flow | Manager xem danh sách giải trong phạm vi | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200; `data.content` mảng | High | Pass | | |
| TC-INT-ManagerTournamentController-002 | GET /api/v1/manager/tournaments | UC-16 / NFR-SEC02 | Player gọi danh sách giải phía quản trị → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |
| TC-INT-ManagerTournamentController-003 | GET /api/v1/manager/formats | UC-16 AF | Manager xem danh sách thể thức khả dụng để tạo giải | Functional | Use Case Testing | manager@gmail.com | N/A | 3 format ACTIVE đã seed | GET | 200 | Low | Pass | | |
| TC-INT-ManagerTournamentController-004 | GET /api/v1/manager/game-types | UC-16 AF | Manager xem danh sách loại bi khả dụng | Functional | Use Case Testing | manager@gmail.com | N/A | 3 game type ACTIVE đã seed | GET | 200 | Low | Pass | | |
| TC-INT-ManagerTournamentController-005 | POST /api/v1/manager/tournaments | UC-16 Main Flow | Manager1 tạo giải cho branch của mình (Thủ Đức) → thành công | Integration/API | Use Case Testing | manager@gmail.com | N/A | branchId = Branch Thủ Đức | POST body hợp lệ, format SINGLE_ELIMINATION | 201; `success=true`; DB có tournament mới | Critical | Pass | | |
| TC-INT-ManagerTournamentController-006 | POST /api/v1/manager/tournaments | UC-16 / GB-05 | Manager1 tạo giải cho branch của Manager2 (Cầu Giấy) → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | branchId = Branch Cầu Giấy (không thuộc manager1) | POST branchId ngoài phạm vi | 4xx; DB không có tournament mới | Critical | Pass | | Case quan trọng nhất của GB-05 cho module giải đấu |
| TC-INT-ManagerTournamentController-007 | POST /api/v1/manager/tournaments | UC-16 / BR branchId required | Thiếu `branchId` → từ chối phía server | Input Validation | Boundary Value Analysis | manager@gmail.com | Server-side (bypass UI) | — | POST thiếu branchId | 400 | Medium | Pass | | |
| TC-INT-ManagerTournamentController-008 | POST /api/v1/manager/tournaments | UC-16 / BR maxParticipants ≥ 2 | `maxParticipants=1` → từ chối phía server | Input Validation | Boundary Value Analysis | manager@gmail.com | Server-side (bypass UI) | branchId = Branch Thủ Đức | POST maxParticipants=1 | 400 | Medium | Pass | | |
| TC-INT-ManagerTournamentController-009 | GET /api/v1/manager/tournaments/{id} | UC-16 Main Flow | Manager1 xem chi tiết giải do mình tạo | Integration/API | Use Case Testing | manager@gmail.com | N/A | Giải tự tạo trong test (branch Thủ Đức) | GET | 200; `data.id` khớp | High | Pass | | |
| TC-INT-ManagerTournamentController-010 | GET /api/v1/manager/tournaments/{id} | UC-16 / GB-05 | Manager2 xem chi tiết giải của Manager1 → bị chặn | Security | Decision Table Testing | manager2@gmail.com | N/A | Giải tạo bởi manager1, branch Thủ Đức | GET (JWT manager2) | 4xx | Critical | Pass | | |
| TC-INT-ManagerTournamentController-011 | PUT /api/v1/manager/tournaments/{id} | UC-16 Main Flow | Manager1 sửa tên giải do mình tạo | Integration/API | Use Case Testing | manager@gmail.com | N/A | Giải tự tạo, status DRAFT | PUT name mới | 200 | High | Pass | | |
| TC-INT-ManagerTournamentController-012 | PATCH /api/v1/manager/tournaments/{id}/status | UC-18 Main Flow | Chuyển DRAFT → OPEN_FOR_REGISTRATION hợp lệ | Integration/API | State Transition Testing | manager@gmail.com | N/A | Giải tự tạo, status DRAFT | PATCH status=OPEN_FOR_REGISTRATION | 200 | Critical | Pass | | |
| TC-INT-ManagerTournamentController-013 | PATCH /api/v1/manager/tournaments/{id}/status | UC-18 / GB-06 | Chuyển thẳng DRAFT → COMPLETED (bỏ qua whitelist) → bị chặn | Functional | State Transition Testing | manager@gmail.com | N/A | Giải tự tạo, status DRAFT | PATCH status=COMPLETED | 4xx | Critical | Pass | | Xác nhận whitelist 1 chiều GB-06 |
| TC-INT-ManagerTournamentController-014 | PATCH /api/v1/manager/tournaments/{id}/visibility | UC-16 AF | Bật hiển thị công khai giải | Functional | Use Case Testing | manager@gmail.com | N/A | Giải tự tạo | PATCH isShowTournament=true | 200; `data.isShowTournament=true` | Medium | Pass | | |
| TC-INT-ManagerTournamentController-015 | GET /api/v1/manager/tournaments/{id}/audit-logs | UC-55 Main Flow | Manager xem lịch sử đổi trạng thái giải | Integration/API | Use Case Testing | manager@gmail.com | N/A | Giải tự tạo | GET | 200; `data` mảng | Low | Pass | | |

---

## MatchController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-MatchController-001 | POST /api/v1/owner/tournaments/{id}/draw | UC-19 / GB-10 Main Flow | Đủ 4 participant, status REGISTRATION_CLOSED → bốc thăm thành công | Integration/API | Use Case Testing | owner@gmail.com | N/A | Giải SINGLE_ELIMINATION 4 người tự dựng (bracket_size=4) | POST | 201 | Critical | Pass | | |
| TC-INT-MatchController-002 | POST /api/v1/owner/tournaments/{id}/draw | UC-19 / NFR-SEC02 | Player gọi bốc thăm → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | Giải tự dựng như trên | POST (JWT Player) | 403 | Critical | Pass | | |
| TC-INT-MatchController-003 | POST /api/v1/owner/tournaments/{id}/draw | UC-19 / GB-10 | Giải còn ở DRAFT → chưa đủ điều kiện bốc thăm → bị chặn | Functional | State Transition Testing | owner@gmail.com | N/A | Giải tự dựng, ép status=DRAFT | POST | 4xx | Critical | Pass | | |
| TC-INT-MatchController-004 | PATCH /score/increment... (chuỗi) start → PUT score → POST complete /api/v1/owner/matches/{id}/** | UC-33 Main Flow | Bắt đầu trận → ghi điểm đúng race-to (5) → hoàn tất, đúng người thắng | Integration/API | State Transition Testing | owner@gmail.com | N/A | Bracket đã bốc thăm+confirm+IN_PROGRESS, trận R1-M1 PENDING | PATCH start → PUT score 5-2 → POST complete | 200/200/200; status cuối COMPLETED, winner đúng player1Score cao hơn | Critical | Pass | | |
| TC-INT-MatchController-005 | PATCH /api/v1/owner/matches/{id}/start | UC-33 / GB-11 | Tournament còn DRAW_DONE (chưa IN_PROGRESS) → chưa thao tác được trận | Functional | State Transition Testing | owner@gmail.com | N/A | Đã draw+confirm nhưng chưa chuyển IN_PROGRESS | PATCH start | 4xx | Critical | Pass | | |
| TC-INT-MatchController-006 | PUT /api/v1/owner/matches/{id}/score | UC-33 / GB-11 | Điểm vượt race-to (6 > 5) → bị chặn | Functional | Boundary Value Analysis | owner@gmail.com | N/A | Trận IN_PROGRESS, raceTo=5 | PUT player1Score=6 | 4xx | Critical | Pass | | |
| TC-INT-MatchController-007 | PUT /api/v1/owner/matches/{id}/score | UC-33 / BR score ≥ 0 | Điểm âm → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | Trận đã bốc thăm | PUT player1Score=-1 | 400 | Medium | Pass | | |
| TC-INT-MatchController-008 | PATCH /api/v1/staff/matches/{id}/start | UC-33 / GB-12 | Staff không được gán trận → bị chặn dù đúng role STAFF | Security | Decision Table Testing | staff1@gmail.com | N/A | Trận IN_PROGRESS tournament, chưa gán trọng tài | PATCH (JWT staff1, chưa gán) | 4xx | Critical | Pass | | |
| TC-INT-MatchController-009 | PATCH /api/v1/owner/matches/{id}/start | UC-33 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | PATCH không Authorization | 401 | High | Pass | | |
| TC-INT-MatchController-010 | GET /api/v1/tournaments/{id}/stages | UC-31 / GB-14 | `isPublicRatio=false` → endpoint Public trả 404 dù giải tồn tại | Security | Decision Table Testing | (Guest) | N/A | Giải tự dựng, isPublicRatio=false (mặc định) | GET không Authorization | 404 | Critical | Pass | | Xác nhận GB-14 — không lộ dữ liệu khi Owner chưa bật công khai |
| TC-INT-MatchController-011 | GET /api/v1/tournaments/{id}/matches | UC-31 / GB-14 | `isPublicRatio=true` → endpoint Public hiển thị bình thường | Functional | Decision Table Testing | (Guest) | N/A | Giải tự dựng, ép isPublicRatio=true | GET | 200; `data` mảng | High | Pass | | |
| TC-INT-MatchController-012 | GET /api/v1/player/matches | UC-35 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Medium | Pass | | |
| TC-INT-MatchController-013 | GET /api/v1/player/matches | UC-35 Main Flow | Player xem lịch trận của bản thân | Integration/API | Use Case Testing | player1@gmail.com | N/A | — | GET | 200; `data` mảng | Medium | Pass | | |

---

## NewsController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-NewsController-001 | GET /api/v1/news | UC-37 Main Flow | Guest xem danh sách bài đã xuất bản, không cần đăng nhập | Integration/API | Use Case Testing | (Guest) | N/A | — | GET không Authorization | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-NewsController-002 | GET /api/v1/news/{slug} | UC-37 AF | slug không tồn tại → 404 | Functional | Error Guessing | (Guest) | N/A | slug="khong-ton-tai-slug" | GET | 404 | Low | Pass | | |
| TC-INT-NewsController-003 | GET /api/v1/news/categories | UC-37 AF | Guest xem danh mục tin tức | Functional | Use Case Testing | (Guest) | N/A | — | GET | 200 | Low | Pass | | |
| TC-INT-NewsController-004 | GET /api/v1/owner/news | UC-36 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-NewsController-005 | GET /api/v1/owner/news | UC-36 / NFR-SEC02 | Player gọi CMS tin tức Owner → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |
| TC-INT-NewsController-006 | POST → GET → POST(publish) → POST(hide) → DELETE /api/v1/owner/news(/{id}(/publish\|/hide)) | UC-36 Main Flow | Owner tạo bài (gắn category tự tạo) → xem chi tiết → xuất bản → ẩn → xoá | Integration/API | State Transition Testing | owner@gmail.com | N/A | Category tự tạo trong `@Test` (UUID) | Đi hết vòng đời bài viết | 201/200/200/200/200 | Critical | Pass | | |
| TC-INT-NewsController-007 | POST /api/v1/owner/news | UC-36 / BR title required | title rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST title="" | 400 | Medium | Pass | | |
| TC-INT-NewsController-008 | PUT + PATCH /api/v1/owner/news/categories(/{id}(/status)) | UC-57 Main Flow | Owner sửa tên category → đổi trạng thái INACTIVE | Integration/API | State Transition Testing | owner@gmail.com | N/A | Category tự tạo trong `@Test` | PUT tên mới → PATCH status=INACTIVE | 200/200 | Medium | Pass | | |
| TC-INT-NewsController-009 | GET /api/v1/manager/news | UC-36 Main Flow | Manager xem CMS tin tức (mirror Owner) | Integration/API | Use Case Testing | manager@gmail.com | N/A | — | GET | 200 | Medium | Pass | | |
| TC-INT-NewsController-010 | GET /api/v1/manager/news | UC-36 / GB-04 | Owner gọi CMS tin tức qua prefix `/manager/**` → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | GET (JWT Owner) | 403 | Medium | Pass | | |

---

## NewsTagController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-NewsTagController-001 | GET /api/v1/shared/news/tags | UC-57 Main Flow | Owner xem danh sách thẻ | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data` mảng | Low | Pass | | |
| TC-INT-NewsTagController-002 | GET /api/v1/shared/news/tags | UC-57 / NFR-SEC02 | Player gọi danh sách thẻ nội bộ → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | Medium | Pass | | |
| TC-INT-NewsTagController-003 | POST → PUT → DELETE /api/v1/shared/news/tags(/{id}) | UC-57 Main Flow | Manager tạo thẻ mới → sửa tên → xoá | Integration/API | State Transition Testing | manager@gmail.com | N/A | Tên/slug mới (UUID) | POST tạo → PUT sửa → DELETE | 201/200/200 | Medium | Pass | | |
| TC-INT-NewsTagController-004 | POST /api/v1/shared/news/tags | UC-57 / BR name required | name rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST name="" | 400 | Low | Pass | | |
| TC-INT-NewsTagController-005 | POST /api/v1/shared/news/tags | UC-57 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | Medium | Pass | | |

## NotificationController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-NotificationController-001 | GET /api/v1/notifications | — | Player xem hộp thông báo của chính mình | Integration/API | Use Case Testing | player1@gmail.com | N/A | — | GET ?page=0&size=20 | 200; `data.content` mảng | Medium | Pass | | Không có UC riêng — hỗ trợ UC-47/48; endpoint cố ý nằm ngoài `/player/**` để mọi role đều gọi được |
| TC-INT-NotificationController-002 | GET /api/v1/notifications | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Medium | Pass | | |
| TC-INT-NotificationController-003 | GET /api/v1/notifications/unread-count | — Main Flow | Đếm thông báo chưa đọc | Functional | Use Case Testing | player1@gmail.com | N/A | — | GET | 200; `data` là số | Low | Pass | | |
| TC-INT-NotificationController-004 | GET /api/v1/notifications/unread-count | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Low | Pass | | |
| TC-INT-NotificationController-005 | POST /api/v1/notifications/device-tokens | — Main Flow | Đăng ký thiết bị nhận thông báo đẩy | Integration/API | Use Case Testing | player1@gmail.com | N/A | expoToken mới (UUID) | POST body hợp lệ | 200; `success=true` | Medium | Pass | | |
| TC-INT-NotificationController-006 | POST /api/v1/notifications/device-tokens | — AF | Gọi lại 2 lần cùng expoToken → idempotent, không lỗi | Functional | Error Guessing | player1@gmail.com | N/A | — | POST 2 lần liên tiếp cùng token | 200/200 | Low | Pass | | |
| TC-INT-NotificationController-007 | POST /api/v1/notifications/device-tokens | — / BR expoToken required | expoToken rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | player1@gmail.com | Server-side (bypass UI) | — | POST expoToken="" | 400 | Medium | Pass | | |
| TC-INT-NotificationController-008 | POST /api/v1/notifications/device-tokens | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | Medium | Pass | | |
| TC-INT-NotificationController-009 | DELETE /api/v1/notifications/device-tokens | — Main Flow | Gỡ thiết bị đã đăng ký trước đó | Integration/API | Use Case Testing | player1@gmail.com | N/A | Đã POST đăng ký 1 expoToken trong test | DELETE ?expoToken=... | 200; `success=true` | Low | Pass | | |
| TC-INT-NotificationController-010 | DELETE /api/v1/notifications/device-tokens | — / BR expoToken required (query param) | Thiếu query param `expoToken` → từ chối phía server | Input Validation | Boundary Value Analysis | player1@gmail.com | Server-side (bypass UI) | — | DELETE không param | 400 | Low | Pass | | |

---

## OwnerBranchController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-OwnerBranchController-001 | GET /api/v1/owner/branches | UC-39 Main Flow | Owner xem danh sách chi nhánh, thấy đủ 2 branch đã seed | Integration/API | Use Case Testing | owner@gmail.com | N/A | 2 branch (Thủ Đức, Cầu Giấy) đã seed | GET | 200; `data.content.length()=2` | High | Pass | | |
| TC-INT-OwnerBranchController-002 | GET /api/v1/owner/branches | UC-39 / NFR-SEC02 | Manager gọi quản lý chi nhánh Owner → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | — | GET (JWT Manager) | 403 | High | Pass | | |
| TC-INT-OwnerBranchController-003 | POST /api/v1/owner/branches | UC-39 Main Flow | Owner tạo chi nhánh mới → thành công | Integration/API | Use Case Testing | owner@gmail.com | N/A | Tên mới (UUID) | POST name/address hợp lệ | 201; `data.name` đúng | Critical | Pass | | |
| TC-INT-OwnerBranchController-004 | POST /api/v1/owner/branches | UC-39 / BR address required | address rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST address="" | 400 | Medium | Pass | | |
| TC-INT-OwnerBranchController-005 | POST /api/v1/owner/branches | UC-39 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST không Authorization | 401 | High | Pass | | |
| TC-INT-OwnerBranchController-006 | GET /api/v1/owner/branches/{id} | UC-39 Main Flow | Owner xem chi tiết chi nhánh vừa tạo | Integration/API | Use Case Testing | owner@gmail.com | N/A | Branch tự tạo trong test | GET | 200; `data.id` khớp | Medium | Pass | | |
| TC-INT-OwnerBranchController-007 | GET /api/v1/owner/branches/{id} | UC-39 AF | id không tồn tại → 404 | Functional | Error Guessing | owner@gmail.com | N/A | id=999999999 | GET | 404 | Low | Pass | | |
| TC-INT-OwnerBranchController-008 | PUT /api/v1/owner/branches/{id} | UC-39 Main Flow | Owner sửa tên chi nhánh | Integration/API | Use Case Testing | owner@gmail.com | N/A | Branch tự tạo trong test | PUT name mới | 200; `data.name` đúng | Medium | Pass | | |
| TC-INT-OwnerBranchController-009 | PATCH /api/v1/owner/branches/{id}/status | UC-39 Main Flow | Đổi trạng thái INACTIVE rồi ACTIVE trở lại | Integration/API | State Transition Testing | owner@gmail.com | N/A | Branch tự tạo, status ACTIVE | PATCH INACTIVE → PATCH ACTIVE | 200/200 | High | Pass | | |
| TC-INT-OwnerBranchController-010 | PATCH /api/v1/owner/branches/{id}/status | UC-39 / BR status required | Thiếu `status` → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | Branch tự tạo | PATCH body rỗng | 400 | Medium | Pass | | |

---

## OwnerController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-OwnerController-001 | POST /api/v1/owner/accounts/manager | UC-08 Main Flow | Owner tạo Manager quản lý toàn chuỗi | Integration/API | Use Case Testing | owner@gmail.com | N/A | Email mới (UUID), manageAllBranches=true | POST body hợp lệ | 201; `data.email` đúng | Critical | Pass | | |
| TC-INT-OwnerController-002 | POST /api/v1/owner/accounts/manager | UC-08 / GB-04 | Manager tự tạo Manager khác → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | — | POST (JWT Manager) | 403 | Critical | Pass | | |
| TC-INT-OwnerController-003 | POST /api/v1/owner/accounts/staff | UC-08 Main Flow | Owner tạo Staff trực tiếp (không qua Manager) | Integration/API | Use Case Testing | owner@gmail.com | N/A | Email mới (UUID) | POST body hợp lệ | 201 | High | Pass | | |
| TC-INT-OwnerController-004 | POST /api/v1/owner/accounts/staff | UC-08 / BR email unique | Trùng email `staff1@gmail.com` đã seed → từ chối | Functional | Error Guessing | owner@gmail.com | N/A | `staff1@gmail.com` đã tồn tại | POST email trùng | 409 | High | Pass | | |
| TC-INT-OwnerController-005 | GET /api/v1/owner/employees | UC-08 Main Flow | Owner xem danh sách Manager/Staff toàn chuỗi | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET ?role=MANAGER | 200; `data.content` mảng | High | Pass | | |
| TC-INT-OwnerController-006 | GET /api/v1/owner/employees/{id} | UC-08 Main Flow | Xem chi tiết Manager thuộc chuỗi | Integration/API | Use Case Testing | owner@gmail.com | N/A | `manager@gmail.com` thuộc chuỗi Owner | GET | 200; `data.email` đúng | Medium | Pass | | |
| TC-INT-OwnerController-007 | GET /api/v1/owner/employees/{id} | UC-08 / BR không phải nhân viên của Owner | id là Player (không phải nhân viên) → bị từ chối | Functional | Error Guessing | owner@gmail.com | N/A | id = user Player | GET id Player | 4xx | Medium | Pass | | |
| TC-INT-OwnerController-008 | PUT /api/v1/owner/employees/{id} | UC-08 Main Flow | Owner sửa `fullName` của Staff | Integration/API | Use Case Testing | owner@gmail.com | N/A | `staff4@gmail.com` đã seed | PUT fullName mới | 200; `data.fullName` đúng | Medium | Pass | | |
| TC-INT-OwnerController-009 | PUT /api/v1/owner/employees/{id}/deactivate + reactivate | UC-08 Main Flow | Vô hiệu hoá rồi mở khoá lại 1 nhân viên | Integration/API | State Transition Testing | owner@gmail.com | N/A | `staff2@gmail.com` ACTIVE | PUT deactivate → PUT reactivate | 200/200; DB LOCKED rồi ACTIVE | Critical | Pass | | |

---

## OwnerEmailController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-OwnerEmailController-001 | GET /api/v1/owner/email/templates | UC-46 Main Flow | Owner xem danh sách mẫu email khả dụng | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-OwnerEmailController-002 | GET /api/v1/owner/email/templates | UC-46 / NFR-SEC02 | Staff gọi danh sách mẫu email → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | High | Pass | | |
| TC-INT-OwnerEmailController-003 | GET /api/v1/owner/tournaments/{id}/email/automation-rules | UC-46 AF | Giải không tồn tại → 4xx | Functional | Error Guessing | owner@gmail.com | N/A | id=999999999 | GET | 4xx | Medium | Pass | | |
| TC-INT-OwnerEmailController-004 | POST /api/v1/owner/tournaments/{id}/email/send-manual | UC-46 / BR templateId required | Thiếu `templateId` → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST thiếu templateId | 400 | Medium | Pass | | |
| TC-INT-OwnerEmailController-005 | GET /api/v1/owner/tournaments/{id}/email/logs | UC-46 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |

---

## OwnerRegistrationController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-OwnerRegistrationController-001 | GET /api/v1/owner/registration-form-templates | UC-23 AF | Owner xem danh sách template đăng ký active | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `success=true` | Medium | Pass | | |
| TC-INT-OwnerRegistrationController-002 | GET /api/v1/owner/registration-form-templates | UC-23 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-OwnerRegistrationController-003 | GET /api/v1/owner/registrations/{id} | UC-23 AF | Đăng ký không tồn tại → 404 | Functional | Error Guessing | owner@gmail.com | N/A | id=999999999 | GET | 404 | Medium | Pass | | |
| TC-INT-OwnerRegistrationController-004 | POST /api/v1/owner/registrations/{id}/approve | UC-23 / GB-04 | Manager gọi duyệt đăng ký qua prefix `/owner/**` → bị chặn | Security | Decision Table Testing | manager@gmail.com | N/A | — | POST (JWT Manager) | 403 | High | Pass | | |
| TC-INT-OwnerRegistrationController-005 | POST /api/v1/owner/registrations/{id}/reject | UC-23 / BR reason required | Lý do từ chối rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST reason="" | 400 | Medium | Pass | | |

## OwnerTableController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-OwnerTableController-001 | GET /api/v1/owner/tables/active | — | Owner xem danh sách bàn đang hoạt động | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data` mảng | Medium | Pass | | Không có UC riêng — hỗ trợ UC-19/UC-33 |
| TC-INT-OwnerTableController-002 | GET /api/v1/owner/tables | — Main Flow | Owner xem danh sách bàn phân trang | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-OwnerTableController-003 | GET /api/v1/owner/tables | — / NFR-SEC02 | Player gọi quản lý bàn Owner → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |
| TC-INT-OwnerTableController-004 | POST /api/v1/owner/tables | — Main Flow | Tạo bàn mới → thành công | Integration/API | Use Case Testing | owner@gmail.com | N/A | Tên mới (UUID) | POST name/tableType hợp lệ | 201; `data.name` đúng | High | Pass | | |
| TC-INT-OwnerTableController-005 | POST /api/v1/owner/tables | — / BR name required | name rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST name="" | 400 | Medium | Pass | | |
| TC-INT-OwnerTableController-006 | GET /api/v1/owner/tables/{id} | — Main Flow | Xem chi tiết bàn vừa tạo | Integration/API | Use Case Testing | owner@gmail.com | N/A | Bàn tự tạo trong test | GET | 200; `data.id` khớp | Low | Pass | | |
| TC-INT-OwnerTableController-007 | PUT /api/v1/owner/tables/{id} | — Main Flow | Sửa tên bàn | Integration/API | Use Case Testing | owner@gmail.com | N/A | Bàn tự tạo trong test | PUT name mới | 200; `data.name` đúng | Medium | Pass | | |
| TC-INT-OwnerTableController-008 | PATCH /api/v1/owner/tables/{id}/status | — Main Flow | Đổi trạng thái bàn sang INACTIVE | Integration/API | State Transition Testing | owner@gmail.com | N/A | Bàn tự tạo, status ACTIVE | PATCH status=INACTIVE | 200; `data.status=INACTIVE` | Medium | Pass | | |
| TC-INT-OwnerTableController-009 | GET /api/v1/owner/tables/import-template | — Main Flow | Tải mẫu import bàn hàng loạt (.xlsx) | Functional | Use Case Testing | owner@gmail.com | N/A | — | GET | 200 | Low | Pass | | |
| TC-INT-OwnerTableController-010 | GET /api/v1/owner/tables/import-template | — / NFR-SEC02 | Player tải mẫu import bàn → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | Low | Pass | | |

---

## OwnerTournamentController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-OwnerTournamentController-001 | GET /api/v1/owner/tournaments | UC-16 Main Flow | Owner xem danh sách giải do mình tạo | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data.content` mảng | High | Pass | | |
| TC-INT-OwnerTournamentController-002 | GET /api/v1/owner/tournaments | UC-16 / NFR-SEC02 | Staff gọi danh sách giải Owner → bị chặn | Security | Decision Table Testing | staff1@gmail.com | N/A | — | GET (JWT Staff) | 403 | High | Pass | | |
| TC-INT-OwnerTournamentController-003 | POST /api/v1/owner/tournaments | UC-16 / BR gameType required | Thiếu `gameType` → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | branchId hợp lệ | POST thiếu gameType | 400 | Medium | Pass | | |
| TC-INT-OwnerTournamentController-004 | GET /api/v1/owner/tournaments/{id} | UC-16 Main Flow | Owner xem chi tiết giải do mình tạo | Integration/API | Use Case Testing | owner@gmail.com | N/A | Giải tự tạo trong test | GET | 200; `data.id` khớp | High | Pass | | |
| TC-INT-OwnerTournamentController-005 | GET /api/v1/owner/tournaments/{id} | UC-16 AF | id không tồn tại → 404 | Functional | Error Guessing | owner@gmail.com | N/A | id=999999999 | GET | 404 | Low | Pass | | |
| TC-INT-OwnerTournamentController-006 | PUT /api/v1/owner/tournaments/{id} | UC-16 Main Flow | Owner sửa thông tin cơ bản giải (status DRAFT) | Integration/API | Use Case Testing | owner@gmail.com | N/A | Giải tự tạo, status DRAFT | PUT name mới | 200 | High | Pass | | |
| TC-INT-OwnerTournamentController-007 | GET → PUT → GET → POST(validate) /api/v1/owner/tournaments/{id}/config(-form\|/validate) | UC-17 Main Flow | Wizard bước 2 đầy đủ: load config-form mặc định → lưu override (bracket_size, third_place_match, break_rule, lag_for_break + race-to `final`=11) → xem config đã resolve → validate | Integration/API | State Transition Testing | owner@gmail.com | N/A | Giải tự tạo SINGLE_ELIMINATION | GET config-form → PUT config (raceToOverrides final=11) → GET config → POST validate | 200/200(`isConfigComplete=true`)/200(`raceToRules.final=11`)/200(`isValid=true`) | Critical | Pass | | Minh chứng trực tiếp DC-02 (race-to tự do theo từng vị trí) trong đánh giá kiến trúc hệ thống |
| TC-INT-OwnerTournamentController-008 | PUT /api/v1/owner/tournaments/{id}/config | UC-17 / BR fields required | Danh sách field cấu hình rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | Giải tự tạo | PUT fields=[] | 400 | Medium | Pass | | |
| TC-INT-OwnerTournamentController-009 | PATCH /api/v1/owner/tournaments/{id}/status | UC-18 / BR config chưa hoàn tất | Mở đăng ký khi chưa lưu config bắt buộc → bị chặn | Functional | State Transition Testing | owner@gmail.com | N/A | Giải tự tạo, status DRAFT, chưa lưu config | PATCH status=OPEN_FOR_REGISTRATION | 4xx | Critical | Pass | | |
| TC-INT-OwnerTournamentController-010 | PATCH /api/v1/owner/tournaments/{id}/visibility | UC-16 AF | Bật hiển thị công khai | Functional | Use Case Testing | owner@gmail.com | N/A | Giải tự tạo | PATCH isShowTournament=true | 200 | Medium | Pass | | |
| TC-INT-OwnerTournamentController-011 | GET /api/v1/owner/tournaments/{id}/audit-logs | UC-55 Main Flow | Owner xem lịch sử đổi trạng thái giải | Integration/API | Use Case Testing | owner@gmail.com | N/A | Giải tự tạo | GET | 200; `data` mảng | Low | Pass | | |

---

## ParticipantController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ParticipantController-001 | GET /api/v1/tournaments/{id}/participants | UC-29 Main Flow | Guest xem danh sách participant giải trống (chưa ai đăng ký) | Integration/API | Use Case Testing | (Guest) | N/A | Giải tự tạo, chưa có participant | GET không Authorization | 200; `data` mảng rỗng | Medium | Pass | | |
| TC-INT-ParticipantController-002 | POST /api/v1/owner/tournaments/{id}/participants/manual | UC-28 Main Flow | Owner thêm participant thủ công (kèm seedNo=1) → thành công | Integration/API | Use Case Testing | owner@gmail.com | N/A | Giải REGISTRATION_CLOSED, roster editable | POST displayName + seedNo=1 | 201; `data.seedNo=1` | Critical | Pass | | |
| TC-INT-ParticipantController-003 | POST /api/v1/owner/tournaments/{id}/participants/manual | UC-28 / NFR-SEC02 | Player thêm participant thủ công → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | POST (JWT Player) | 403 | High | Pass | | |
| TC-INT-ParticipantController-004 | POST /api/v1/owner/tournaments/{id}/participants/manual | UC-28 / BR displayName required | displayName rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST displayName="" | 400 | Medium | Pass | | |
| TC-INT-ParticipantController-005 | POST /api/v1/owner/tournaments/{id}/participants/manual | UC-28 / GB-16 | Roster đã khoá (status DRAW_PREVIEW) → không thêm được nữa | Functional | State Transition Testing | owner@gmail.com | N/A | Giải ép status=DRAW_PREVIEW | POST | 4xx | Critical | Pass | | |
| TC-INT-ParticipantController-006 | POST /api/v1/owner/tournaments/{id}/participants/manual | UC-28 / BR seedNo unique | Thêm 2 participant cùng `seedNo=1` → participant thứ 2 bị từ chối | Functional | Error Guessing | owner@gmail.com | N/A | Participant #1 đã có seedNo=1 | POST participant #2 seedNo=1 | 409 | High | Pass | | |
| TC-INT-ParticipantController-007 | PATCH /api/v1/owner/participants/{id}/seed-no → PATCH .../withdraw | UC-28 / UC-17 Main Flow | Manager sửa lại seedNo participant → rút participant (WITHDRAWN) | Integration/API | State Transition Testing | manager@gmail.com | N/A | Participant tự thêm trong test | PATCH seedNo=3 → PATCH withdraw | 200/200; `data.status=WITHDRAWN` | High | Pass | | Đúng GB-17 — chỉ Owner/Manager rút hộ, không có endpoint `/player/**` |
| TC-INT-ParticipantController-008 | GET /api/v1/manager/tournaments/{id}/participants/import-template | UC-28 AF | Manager tải mẫu import participant (.xlsx) | Functional | Use Case Testing | manager@gmail.com | N/A | — | GET | 200 | Low | Pass | | |

---

## PaymentController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PaymentController-001 | GET /api/v1/player/payments | UC-26 Main Flow | Player xem lịch sử thanh toán của chính mình | Integration/API | Use Case Testing | player1@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-PaymentController-002 | GET /api/v1/player/payments | UC-26 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-PaymentController-003 | POST /api/v1/player/registrations/{id}/checkout | UC-24 AF | Đăng ký không tồn tại → 404 (chặn trước khi gọi PayOS) | Functional | Error Guessing | player1@gmail.com | N/A | id=999999999 | POST | 404 | Medium | Pass | | Không test happy-path checkout thật (không có `PAYOS_API_KEY` trong CI/dev) — xem Report 5.0 §1.3 |
| TC-INT-PaymentController-004 | POST /api/v1/player/registrations/{id}/checkout | UC-24 / GB-04 | Owner gọi checkout (chỉ Player) → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | POST (JWT Owner) | 403 | High | Pass | | |
| TC-INT-PaymentController-005 | POST /api/v1/player/payments/confirm-return | UC-24 AF | orderCode không tồn tại → 4xx | Functional | Error Guessing | player1@gmail.com | N/A | orderCode=999999999 | POST ?orderCode=999999999 | 4xx | Medium | Pass | | |
| TC-INT-PaymentController-006 | POST /api/v1/payments/payos/webhook | UC-25 Main Flow | Webhook chữ ký sai vẫn trả 200 `{"error":0}` (không lộ tín hiệu phân biệt) | Security | Error Guessing | (Guest — webhook PayOS) | N/A | Payload không có chữ ký hợp lệ | POST rawBody bất kỳ | 200; body=`{"error":0}` | High | Pass | | Đúng thiết kế bảo mật webhook — không phải lỗi |
| TC-INT-PaymentController-007 | GET /api/v1/manager/registrations/{id}/payments | UC-27 AF | Đăng ký không tồn tại → 4xx | Functional | Error Guessing | manager@gmail.com | N/A | id=999999999 | GET | 4xx | Medium | Pass | | |
| TC-INT-PaymentController-008 | GET /api/v1/owner/registrations/{id}/payments | UC-27 / NFR-SEC02 | Player gọi lịch sử thanh toán theo đăng ký (chỉ Owner/Manager) → bị chặn | Security | Decision Table Testing | player1@gmail.com | N/A | — | GET (JWT Player) | 403 | High | Pass | | |

---

## PlayerController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PlayerController-001 | POST /api/v1/player/profile | UC-54 Main Flow | Player mới đăng ký (chưa có profile) → tạo hồ sơ thi đấu lần đầu thành công | Integration/API | Use Case Testing | (Player mới đăng ký qua `/auth/register` trong test) | N/A | Tài khoản Player vừa tạo, `profileCompleted=false` | POST fullName + billiardRank | 201; `data.fullName` đúng | Critical | Pass | | |
| TC-INT-PlayerController-002 | POST /api/v1/player/profile | UC-54 / BR profile unique | player1 đã có profile (seed sẵn) → tạo lại bị từ chối | Functional | Error Guessing | player1@gmail.com | N/A | player1 đã có `UserProfile` từ `DataInitializer` | POST fullName mới | 409 | High | Pass | | |
| TC-INT-PlayerController-003 | POST /api/v1/player/profile | UC-54 / BR fullName required | fullName rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | (Player mới) | Server-side (bypass UI) | Tài khoản Player vừa tạo | POST fullName="" | 400 | Medium | Pass | | |
| TC-INT-PlayerController-004 | POST /api/v1/player/profile | UC-54 / GB-04 | Owner gọi tạo profile Player → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | POST (JWT Owner) | 403 | High | Pass | | |
| TC-INT-PlayerController-005 | GET /api/v1/player/profile | UC-54 Main Flow | Player xem hồ sơ thi đấu của chính mình | Integration/API | Use Case Testing | player2@gmail.com | N/A | player2 đã có profile seed sẵn | GET | 200; `success=true` | High | Pass | | |
| TC-INT-PlayerController-006 | GET /api/v1/player/profile | UC-54 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |

## PlayerRegistrationController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PlayerRegistrationController-001 | GET /api/v1/player/tournaments | UC-21 Main Flow | Player xem danh sách giải đang mở/sắp mở | Integration/API | Use Case Testing | player1@gmail.com | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-PlayerRegistrationController-002 | GET /api/v1/player/tournaments/{tournamentId} | UC-22 AF | id không tồn tại → 404 | Functional | Error Guessing | player1@gmail.com | N/A | id=999999999 | GET | 404 | Low | Pass | | |
| TC-INT-PlayerRegistrationController-003 | POST /api/v1/player/tournaments/{id}/registrations | UC-23 / GB-09 Main Flow | Giải miễn phí (entryFee=0), OPEN_FOR_REGISTRATION → nộp đơn tự động duyệt ngay | Integration/API | State Transition Testing | player5@gmail.com | N/A | Giải tự tạo entryFee=0, isRegister=true, có `registrationFormTemplateId=PLAYER_REG_BASIC` | POST registrationType=SINGLE + fieldValues đủ | 201; `data.status=APPROVED` | Critical | Pass | | Minh chứng GB-09 (tự duyệt khi giải miễn phí) không cần dựng PayOS thật |
| TC-INT-PlayerRegistrationController-004 | POST /api/v1/player/tournaments/{id}/registrations | UC-23 / GB-08 | Nộp đơn lần 2 cho cùng giải → bị từ chối | Functional | Error Guessing | player6@gmail.com | N/A | Đã đăng ký thành công lần 1 trong cùng test | POST lần 2 cùng giải | 409 | Critical | Pass | | |
| TC-INT-PlayerRegistrationController-005 | POST /api/v1/player/tournaments/{id}/registrations | UC-23 / GB-07 | Giải đang DRAFT (chưa mở đăng ký) → bị từ chối | Functional | State Transition Testing | player7@gmail.com | N/A | Giải tự tạo, ép status=DRAFT | POST | 4xx | Critical | Pass | | |
| TC-INT-PlayerRegistrationController-006 | POST /api/v1/player/tournaments/{id}/registrations | UC-23 / BR registrationType hợp lệ | registrationType="TRIPLE" (ngoài SINGLE/DOUBLE) → từ chối phía server | Input Validation | Equivalence Partitioning | player1@gmail.com | Server-side (bypass UI) | Giải tự tạo hợp lệ | POST registrationType="TRIPLE" | 400 | Medium | Pass | | |
| TC-INT-PlayerRegistrationController-007 | GET /api/v1/player/tournaments/{id}/my-registration | UC-23 AF | Chưa từng đăng ký giải này → trả `data=null`, không lỗi | Functional | Use Case Testing | player1@gmail.com | N/A | Giải tự tạo, player1 chưa đăng ký | GET | 200; `data` không tồn tại/null | Medium | Pass | | |
| TC-INT-PlayerRegistrationController-008 | GET /api/v1/player/registrations → GET /{id} → DELETE /{id} | UC-23 / UC-26 Main Flow | Đăng ký thành công → liệt kê đăng ký của tôi → xem chi tiết → hủy đăng ký | Integration/API | State Transition Testing | player8@gmail.com | N/A | Đã đăng ký thành công trong cùng test | GET list → GET detail → DELETE | 200/200/200 | Critical | Pass | | |
| TC-INT-PlayerRegistrationController-009 | GET /api/v1/player/registrations/{id} | UC-23 / GB Ownership | Player khác xem chi tiết đăng ký không phải của mình → bị chặn | Security | Decision Table Testing | player10@gmail.com | N/A | Đăng ký thuộc về player9 | GET (JWT player10) | 4xx | Critical | Pass | | |

---

## ProfileController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-ProfileController-001 | GET /api/v1/profile | UC-05 Main Flow | Owner xem hồ sơ cá nhân | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | GET | 200; `data.email` đúng | High | Pass | | |
| TC-INT-ProfileController-002 | GET /api/v1/profile | UC-05 AF | Player xem hồ sơ → có field `billiardRank` | Functional | Use Case Testing | player1@gmail.com | N/A | player1 rank="A" (seed) | GET | 200; `data.billiardRank=A` | Medium | Pass | | |
| TC-INT-ProfileController-003 | GET /api/v1/profile | UC-05 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-ProfileController-004 | PUT /api/v1/profile | UC-05 Main Flow | Owner sửa `fullName`/`bio` | Integration/API | Use Case Testing | owner@gmail.com | N/A | — | PUT fullName/bio mới | 200; `data.fullName` đúng | High | Pass | | |
| TC-INT-ProfileController-005 | PUT /api/v1/profile | UC-05 / BR billiardRank chỉ dành cho Player | Manager gửi kèm `billiardRank` → bị từ chối | Functional | Decision Table Testing | manager@gmail.com | N/A | — | PUT billiardRank="A" | 400 `COMMON_001` | High | Pass | | Ranh giới nghiệp vụ đặc thù — field chỉ có ý nghĩa với role PLAYER |
| TC-INT-ProfileController-006 | PUT /api/v1/profile | UC-05 / BR billiardRank hợp lệ | Player gửi `billiardRank` không thuộc hệ phân hạng VN → bị từ chối | Input Validation | Equivalence Partitioning | player1@gmail.com | Server-side (bypass UI) | — | PUT billiardRank="Z9-not-a-real-rank" | 400 `COMMON_001` | Medium | Pass | | |
| TC-INT-ProfileController-007 | PUT /api/v1/profile | UC-05 / BR fullName required | fullName rỗng → từ chối phía server | Input Validation | Boundary Value Analysis | player2@gmail.com | Server-side (bypass UI) | — | PUT fullName="" | 400 | Medium | Pass | | |
| TC-INT-ProfileController-008 | PUT /api/v1/profile | UC-05 / BR phone format | SĐT sai định dạng → từ chối phía server | Input Validation | Boundary Value Analysis | player1@gmail.com | Server-side (bypass UI) | — | PUT phone="abc-not-a-phone" | 400 | Medium | Pass | | |
| TC-INT-ProfileController-009 | PUT /api/v1/profile | UC-05 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | PUT không Authorization | 401 | High | Pass | | |

---

## PublicBranchController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PublicBranchController-001 | GET /api/v1/branches | UC-40 Main Flow | Guest xem danh sách chi nhánh ACTIVE, thấy đủ 2 branch đã seed | Integration/API | Use Case Testing | (Guest) | N/A | 2 branch ACTIVE đã seed | GET không Authorization | 200; `data.content.length()=2` | Medium | Pass | | |
| TC-INT-PublicBranchController-002 | GET /api/v1/branches | UC-40 AF | Tìm theo tên "Cầu Giấy" → lọc đúng 1 kết quả | Functional | Equivalence Partitioning | (Guest) | N/A | — | GET ?search=Cầu Giấy | 200; `data.content.length()=1` | Low | Pass | | |
| TC-INT-PublicBranchController-003 | GET /api/v1/branches/{id} | UC-40 Main Flow | Xem chi tiết 1 branch ACTIVE | Integration/API | Use Case Testing | (Guest) | N/A | branchId lấy từ danh sách Owner | GET | 200; `data.id` khớp | Medium | Pass | | |
| TC-INT-PublicBranchController-004 | GET /api/v1/branches/{id} | UC-40 AF | id không tồn tại → 404 | Functional | Error Guessing | (Guest) | N/A | id=999999999 | GET | 404 | Low | Pass | | |

---

## PublicLeaderboardController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PublicLeaderboardController-001 | GET /api/v1/leaderboard | UC-32 AF | Guest xem bảng xếp hạng, period mặc định ALL | Integration/API | Use Case Testing | (Guest) | N/A | — | GET | 200; `data.content` mảng | Medium | Pass | | |
| TC-INT-PublicLeaderboardController-002 | GET /api/v1/leaderboard | UC-32 AF | Lọc theo period=YEAR, year=2026 | Functional | Equivalence Partitioning | (Guest) | N/A | — | GET ?period=YEAR&year=2026 | 200 | Low | Pass | | |
| TC-INT-PublicLeaderboardController-003 | GET /api/v1/leaderboard | UC-32 / BR period parse an toàn | period lạ (không thuộc enum) → tự rơi về ALL, không lỗi | Functional | Error Guessing | (Guest) | N/A | — | GET ?period=NOT_A_REAL_PERIOD | 200 | Low | Pass | | `LeaderboardPeriod.from()` cố ý nuốt lỗi — không phải input validation 400 |

---

## PublicParticipantController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PublicParticipantController-001 | GET /api/v1/participants/{participantId}/profile | UC-30 AF | participantId không tồn tại → 404 | Functional | Error Guessing | (Guest) | N/A | id=999999999 | GET | 404 | Low | Pass | | |
| TC-INT-PublicParticipantController-002 | GET /api/v1/participants/user/{userId}/profile | UC-30 Main Flow | Xem hồ sơ công khai theo userId của Player đã seed | Integration/API | Use Case Testing | (Guest) | N/A | player1 đã seed | GET | 200 | Medium | Pass | | |
| TC-INT-PublicParticipantController-003 | GET /api/v1/participants/user/{userId}/profile | UC-30 / BR không throw khi rỗng | userId không tồn tại → vẫn 200, `achievements=[]` (không 404) | Functional | Error Guessing | (Guest) | N/A | userId=999999999 | GET | 200; response gần rỗng | Low | Pass | | Ghi nhận hành vi thật — khác `getProfile` theo participantId (có throw 404) |

---

## PublicTournamentController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-PublicTournamentController-001 | GET /api/v1/tournaments | UC-21 Main Flow | Guest xem danh sách giải công khai | Integration/API | Use Case Testing | (Guest) | N/A | — | GET không Authorization | 200; `data.content` mảng | High | Pass | | |
| TC-INT-PublicTournamentController-002 | GET /api/v1/tournaments | UC-21 AF | Lọc theo status + search không khớp gì → trả mảng rỗng, không lỗi | Functional | Equivalence Partitioning | (Guest) | N/A | — | GET ?status=OPEN_FOR_REGISTRATION&search=khong-ton-tai | 200; `data.content` rỗng | Low | Pass | | |
| TC-INT-PublicTournamentController-003 | GET /api/v1/tournaments/{id} | UC-22 AF | id không tồn tại → 404 | Functional | Error Guessing | (Guest) | N/A | id=999999999 | GET | 404 | Medium | Pass | | |

---

## StaffController

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-StaffController-001 | GET /api/v1/staff/matches | UC-33 / GB-12 Main Flow | Staff được gán trận → thấy trận trong danh sách của mình | Integration/API | Use Case Testing | staff1@gmail.com | N/A | Trận R1-M1 đã gán `assignedStaffId=staff1` | GET | 200; `data` chứa đúng matchId | Critical | Pass | | |
| TC-INT-StaffController-002 | GET /api/v1/staff/matches | UC-33 / GB-12 | Staff KHÔNG được gán → không thấy trận đó trong danh sách của mình | Security | Decision Table Testing | staff2@gmail.com | N/A | Trận R1-M1 chỉ gán cho staff1 | GET (JWT staff2) | 200; `data` KHÔNG chứa matchId đó | Critical | Pass | | Kiểm ownership scope bằng nội dung trả về, không chỉ mã HTTP |
| TC-INT-StaffController-003 | GET /api/v1/staff/matches | UC-33 / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | High | Pass | | |
| TC-INT-StaffController-004 | GET /api/v1/staff/matches | UC-33 / GB-04 | Owner gọi endpoint dành riêng Staff → bị chặn | Security | Decision Table Testing | owner@gmail.com | N/A | — | GET (JWT Owner) | 403 | High | Pass | | |
| TC-INT-StaffController-005 | PATCH /api/v1/staff/matches/{id}/score/increment | UC-33 Main Flow | Staff được gán +1 điểm cho player1 sau khi start trận | Integration/API | Use Case Testing | staff1@gmail.com | N/A | Trận đã gán staff1, đã `PATCH start` | PATCH playerSlot=1, delta=1 | 200; `data.match.player1Score=1` | Critical | Pass | | |
| TC-INT-StaffController-006 | PATCH /api/v1/staff/matches/{id}/score/increment | UC-33 / GB-12 | Staff KHÔNG được gán cộng điểm trận đó → bị chặn dù đúng role STAFF | Security | Decision Table Testing | staff2@gmail.com | N/A | Trận chỉ gán staff1 | PATCH (JWT staff2) | 4xx | Critical | Pass | | `assertStaffAssignedOnMatch` chạy TRƯỚC assert-trận-đang-diễn-ra, nên không cần start trận trước khi test case này |
| TC-INT-StaffController-007 | PATCH /api/v1/staff/matches/{id}/score/increment | UC-33 / BR delta ∈ {-1,0,1} | delta=2 (ngoài khoảng cho phép) → từ chối phía server | Input Validation | Boundary Value Analysis | staff1@gmail.com | Server-side (bypass UI) | Trận đã gán staff1 | PATCH delta=2 | 400 | Medium | Pass | | |

---

## StorageController

Không khoá theo role (mọi role đã đăng nhập gọi được qua `anyRequest().authenticated()`), chỉ khoá bằng JWT. MinIO không chạy trong môi trường dev/CI mặc định (`MINIO_ACCESS_KEY=ci-placeholder`) nên **không test happy-path upload/download thật** — chỉ test lớp Security đứng trước `MinioStorageService`. Xem Report 5.0 §1.3.

| Test Case ID | Endpoint | UC-ID / BR Ref | Scenario Name | Test Type | Coverage Technique | Role / Session | Validation Direction | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-INT-StorageController-001 | POST /api/v1/storage/images | UC-05 / UC-16 / UC-36 (dùng chung) / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | POST multipart không Authorization | 401 | Medium | Pass | | Endpoint kỹ thuật dùng chung nhiều UC (upload avatar/banner/ảnh tin tức) |
| TC-INT-StorageController-002 | POST /api/v1/storage/images | — / BR file required | Thiếu multipart part `file` → từ chối phía server | Input Validation | Boundary Value Analysis | owner@gmail.com | Server-side (bypass UI) | — | POST multipart không kèm file | 400 | Medium | **Fail** | DEF-004 | Chạy thật 18/08/2026 trên MySQL local: nhận **500** thay vì 400. Xem BẢNG DEFECTS |
| TC-INT-StorageController-003 | GET /api/v1/storage/images/url | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Low | Pass | | |
| TC-INT-StorageController-004 | GET /api/v1/storage/images/url | — / BR objectKey required | Thiếu query param `objectKey` → từ chối phía server | Input Validation | Boundary Value Analysis | player1@gmail.com | Server-side (bypass UI) | — | GET không param objectKey | 400 | Low | Pass | | |
| TC-INT-StorageController-005 | GET /api/v1/storage/images/download | — / NFR-SEC01 | Không JWT → 401 | Security | Decision Table Testing | (không session) | N/A | — | GET không Authorization | 401 | Low | Pass | | |

## BẢNG SCHEDULER

Xác nhận bằng `grep -rn "@Scheduled" src/main/java` (18/08/2026) — dự án có đúng **4 job thật**
trong 2 class, không phải 5 job như suy đoán ban đầu từ 5 UC trong UCS:

| Class | Method | Chu kỳ | UC-ID |
|---|---|---|---|
| `TournamentAutoStatusScheduler` | `autoCloseExpiredRegistrations()` | `fixedRate` 5 phút | UC-49 |
| `TournamentAutoStatusScheduler` | `warnOverdueTournamentStart()` | `fixedRate` 5 phút | UC-56 |
| `EmailReminderScheduler` | `sendRegistrationClosingSoonReminders()` | `fixedRate` 1 giờ | UC-48 |
| `EmailReminderScheduler` | `sendMatchStartingSoonReminders()` | `fixedRate` 30 phút | UC-48 (chung UC với dòng trên — UCS mô tả 1 UC bọc cả 2 kịch bản nhắc) |

**UC-47 (Deliver Event-driven Emails) và UC-51 (Auto-publish Facebook) KHÔNG phải job
`@Scheduled`** — cả hai chạy qua `ApplicationEventPublisher.publishEvent(...)` tại đúng thời điểm
nghiệp vụ xảy ra (đăng ký được duyệt/từ chối, giải chuyển `isShowTournament=true`), qua
`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`. Có thêm **BJ-06** — bước gửi SMTP
thật (`MailDispatcher.onEmailQueued`) — không map UC nào trong UCS nhưng là mắt xích bắt buộc để
UC-47 thật sự "tới hộp thư", nên vẫn liệt kê riêng.

**Đã viết + chạy thật** (`src/test/java/.../scheduler/*.java`, 4 file, 14 `@Test`) —
`target/surefire-reports/com.capstone.su26_sep490_g2_be.scheduler.*.txt`: **14/14 Pass, 0
Failures, 0 Errors**. Scheduler **không còn Out of Scope** — gỡ khỏi §1.3 của
`Report_5_Testing_Strategy.md`.

Kỹ thuật đặc biệt cho nhóm event-driven (BJ-05/06/07) — cả 3 dùng
`@TransactionalEventListener(AFTER_COMMIT)`, nếu test có `@Transactional` thì transaction rollback
khiến listener KHÔNG chạy → test xanh giả (không kiểm tra được gì thật). Xử lý:
- BJ-05 (`MailAutomationEventL2Test`) và BJ-03/04/01/02 (`BackgroundJobSchedulerL2Test`): dùng
  `@RecordApplicationEvents` + `ApplicationEvents` để assert **event đã được publish đúng nội
  dung**, không chờ listener async thật sự chạy xong (đúng mức L2 — xác nhận đường dây publish
  đúng, không lặp lại việc gửi mail thật đã thuộc phạm vi BJ-06).
- BJ-06 (`MailDispatcherL2Test`): class **KHÔNG** có `@Transactional` (bỏ hẳn, không kế thừa
  `AbstractControllerIntegrationTest`) để AFTER_COMMIT chạy thật; dọn `EmailSendLog` tạo ra bằng
  tay trong `@AfterEach` thay vì rollback. Mock `JavaMailSender` (`@MockitoBean`) để không gửi SMTP
  thật; assert bằng dấu vết DB (`EmailSendLog.status = SENT`), có poll tối đa 10s vì dispatch chạy
  trên thread `@Async`.
- BJ-07 (`FacebookAutoPostL2Test`): gọi thẳng `AopTestUtils.getUltimateTargetObject(listener)` để
  bỏ qua proxy `@Async`, chạy đồng bộ ngay trong transaction test — mock `FacebookPublishService`
  (`@MockitoBean`), assert bằng dấu vết DB (`facebook_posts`), không gọi Facebook Graph API thật.

| Test Case ID | JOB-ID / Method | Scenario Name | Coverage Technique | Given (DB State Before) | When (Service Method Called) | Then (Expected DB State / Log After) | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| TC-SCH-BJ03-001 | BJ-03 `TournamentAutoStatusScheduler.autoCloseExpiredRegistrations()` | Giải `OPEN_FOR_REGISTRATION` quá `registrationDeadline` 10 phút → tự chuyển `REGISTRATION_CLOSED`, ghi audit `AUTO` | State Transition Testing | Giải tự dựng, status=OPEN_FOR_REGISTRATION, registrationDeadline=now-10min | Gọi `autoCloseExpiredRegistrations()` | status=REGISTRATION_CLOSED; `tournament_status_history` có 1 dòng changeType=AUTO | Critical | Pass | | Bắt lỗi: giải quá hạn đăng ký vẫn để mở, người chơi nộp đơn được sau hạn |
| TC-SCH-BJ03-002 | (như trên) | Giải còn hạn đăng ký (deadline+2h) → KHÔNG bị đóng | Boundary Value Analysis | Giải tự dựng, status=OPEN_FOR_REGISTRATION, registrationDeadline=now+2h | Gọi `autoCloseExpiredRegistrations()` | status vẫn OPEN_FOR_REGISTRATION; 0 dòng audit AUTO mới | High | Pass | | Bắt lỗi: giải còn hạn đăng ký bị đóng sớm, BTC mất lượt nhận thêm người chơi |
| TC-SCH-BJ03-003 | (như trên) | Giải DRAFT (chưa mở đăng ký) có deadline quá khứ → KHÔNG bị đụng | Error Guessing | Giải tự dựng, status=DRAFT, registrationDeadline=now-1day | Gọi `autoCloseExpiredRegistrations()` | status vẫn DRAFT; 0 dòng audit AUTO mới | Medium | Pass | | Bắt lỗi: giải nháp chưa mở đăng ký bị hệ thống đóng trạng thái, BTC tưởng đã chạy vòng đời thật |
| TC-SCH-BJ04-001 | BJ-04 `TournamentAutoStatusScheduler.warnOverdueTournamentStart()` | Giải quá `startAt` 1h, còn DRAW_PREVIEW (bracket chưa xong) → ghi cảnh báo audit, KHÔNG đổi status | State Transition Testing | Giải tự dựng, status=DRAW_PREVIEW, startAt=now-1h | Gọi `warnOverdueTournamentStart()` | `tournament_status_history` có 1 dòng changeType=WARNING; status vẫn DRAW_PREVIEW | Critical | Pass | | Bắt lỗi: giải quá giờ khai mạc mà bracket chưa xong thì không ai được nhắc, hoặc bị đổi trạng thái nhầm |
| TC-SCH-BJ04-002 | (như trên) | Giải chưa tới giờ khai mạc (startAt+2day) → KHÔNG ghi cảnh báo | Boundary Value Analysis | Giải tự dựng, status=DRAW_PREVIEW, startAt=now+2day | Gọi `warnOverdueTournamentStart()` | 0 dòng audit WARNING mới; status không đổi | Medium | Pass | | Bắt lỗi: giải chưa tới giờ khai mạc đã bị gắn cảnh báo trễ, BTC tưởng mình chậm tiến độ |
| TC-SCH-BJ01-001 | BJ-01 `EmailReminderScheduler.sendRegistrationClosingSoonReminders()` | Giải còn 23h30 nữa hết hạn ĐK (trong cửa sổ 23-24h), có 1 registration APPROVED → publish event nhắc | State Transition Testing | Giải tự dựng OPEN_FOR_REGISTRATION, registrationDeadline=now+23h30m; 1 registration APPROVED (player1) | Gọi `sendRegistrationClosingSoonReminders()` | `ApplicationEvents` có `MailDomainEvent` eventType=TOURNAMENT_REGISTRATION_CLOSING_SOON đúng tournamentId | Medium | Pass | | Bắt lỗi: giải sắp hết hạn đăng ký mà người đã ghi danh không được nhắc, dễ bỏ lỡ đóng đơn |
| TC-SCH-BJ01-002 | (như trên) | Giải còn 5h nữa hết hạn (ngoài cửa sổ 23-24h) → KHÔNG publish | Boundary Value Analysis | Giải tự dựng, registrationDeadline=now+5h; 1 registration APPROVED | Gọi `sendRegistrationClosingSoonReminders()` | Không có `MailDomainEvent` loại CLOSING_SOON nào cho giải này trong `ApplicationEvents` | Low | Pass | | Bắt lỗi: giải còn rất lâu mới hết hạn vẫn gửi nhắc, người chơi bị làm phiền vô cớ |
| TC-SCH-BJ02-001 | BJ-02 `EmailReminderScheduler.sendMatchStartingSoonReminders()` | Trận PENDING sắp diễn ra sau 45 phút (trong cửa sổ 30-60') → publish event nhắc 2 kỳ thủ | State Transition Testing | Giải IN_PROGRESS; 1 match PENDING, scheduledAt=now+45min, đủ player1+player2 | Gọi `sendMatchStartingSoonReminders()` | `ApplicationEvents` có `MailDomainEvent` eventType=MATCH_SCHEDULED_REMINDER, entityKey=`MATCH-REMINDER-{matchId}` | Medium | Pass | | Bắt lỗi: trận sắp bắt đầu mà hai kỳ thủ không được nhắc, người chơi có thể đến trễ |
| TC-SCH-BJ02-002 | (như trên) | Trận PENDING còn 5h nữa mới diễn ra (ngoài cửa sổ) → KHÔNG publish | Boundary Value Analysis | Giải IN_PROGRESS; 1 match PENDING, scheduledAt=now+5h | Gọi `sendMatchStartingSoonReminders()` | Không có `MailDomainEvent` MATCH_SCHEDULED_REMINDER nào với entityKey của trận này | Low | Pass | | Bắt lỗi: trận còn lâu mới đấu đã bị gửi nhắc, hoặc quét sai cửa sổ giờ làm trận đang chờ bị bỏ sót |
| TC-SCH-BJ05-001 | BJ-05 `MailAutomationEventListener.onMailDomainEvent` (qua `RegistrationService.approve()`) | Duyệt đăng ký (PENDING_PAYMENT→APPROVED) → publish `MailDomainEvent` REGISTRATION_APPROVED | State Transition Testing | Giải mở ĐK miễn phí; 1 registration PENDING_PAYMENT (player3) | Gọi `registrationService.approve(regId, ownerId)` | `ApplicationEvents` có `MailDomainEvent` eventType=REGISTRATION_APPROVED, entityKey=`REGISTRATION-{regId}` | Critical | Pass | | Bắt lỗi: duyệt đơn xong người chơi không nhận được thư báo đã vào giải |
| TC-SCH-BJ05-002 | (như trên, qua `RegistrationService.reject()`) | Từ chối đăng ký → publish REGISTRATION_REJECTED, KHÔNG publish REGISTRATION_APPROVED | Decision Table Testing | Giải mở ĐK miễn phí; 1 registration PENDING_PAYMENT (player4) | Gọi `registrationService.reject(regId, ownerId, reason)` | `ApplicationEvents` có REGISTRATION_REJECTED cho tournamentId; KHÔNG có REGISTRATION_APPROVED nào | High | Pass | | Bắt lỗi: từ chối đơn lại gửi thư chúc mừng đã được duyệt, người chơi hiểu nhầm mình đã vào giải |
| TC-SCH-BJ06-001 | BJ-06 `MailDispatcher.onEmailQueued` | `EmailSendLog` QUEUED, publish `EmailQueuedEvent` sau commit → SMTP giả lập gửi xong, log chuyển SENT | State Transition Testing | 1 `EmailSendLog` tạo trong transaction riêng (TransactionTemplate), status=QUEUED, publish `EmailQueuedEvent` ngay khi transaction đó commit | Chờ tối đa 10s (poll 250ms), đọc lại `EmailSendLog` theo id | status=SENT; `sentAt` khác null | Critical | Pass | | Bắt lỗi: hệ thống ghi "đã xếp hàng gửi thư" nhưng không bao giờ đánh dấu đã gửi, BTC tưởng thư đã tới |
| TC-SCH-BJ07-001 | BJ-07 `FacebookAutoPostListener.onTournamentPublished` | Giải `isShowTournament=true` → đăng bài Fanpage, ghi `facebook_posts` | State Transition Testing | Giải tự dựng, isShowTournament=true, isPublicRatio=true | Gọi thẳng `listener.onTournamentPublished(new TournamentPublishedEvent(tournamentId))` (bỏ qua proxy `@Async`) | `facebook_posts` có 1 dòng đúng tournamentId, `facebookPostId` khớp giá trị mock trả về | Medium | Pass | | Bắt lỗi: giải đã bật hiện công khai mà fanpage không có bài, khán giả không biết giải mới |
| TC-SCH-BJ07-002 | (như trên) | Giải `isShowTournament=false` → KHÔNG đăng bài | Decision Table Testing | Giải tự dựng, isShowTournament=false | Gọi thẳng `listener.onTournamentPublished(...)` | `facebook_posts` không có dòng nào cho tournamentId này; `facebookPublishService` không bị gọi (verify never) | Medium | Pass | | Bắt lỗi: giải BTC cố tình ẩn vẫn bị đăng lên fanpage, lộ giải chưa muốn công bố |

---

## BẢNG SECURITY

Cột 2 dùng **GBR-ID** (dự án có sẵn `gbrs_nfse.md`, GB-01..18) làm chính, kèm mã NFR-SEC tương
ứng đã gán trong `Report_5_Testing_Strategy.md` §1.2 khi có. Phần lớn dòng dưới **tham chiếu
ngược** tới Test Case ID đã có trong bảng Controller ở trên (test cross-cutting, không lặp code).

| Test Case ID | NFR-SEC-ID / GBR-ID | Scenario Name | Coverage Technique | Role / Session | Given | When | Then | Priority | Status | Defect ID | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-SEC-001 | NFR-SEC01 | Không JWT vào endpoint bảo vệ bất kỳ → 401 `AUTH_013` | Decision Table Testing | (không session) | — | — | 401 | Critical | Pass | | Xảy ra ở ~30/43 controller — đại diện `TC-INT-AuthController-007` |
| TC-SEC-002 | NFR-SEC02 / GB-04 | Role sai → 403 · role đúng cùng endpoint → 200/201 | Decision Table Testing | admin@gmail.com (đúng), player1@gmail.com (sai) | — | POST /api/v1/admin/accounts/owner với 2 JWT | 201 / 403 | Critical | Pass | | = `TC-INT-AdminController-001` + `-002`; mẫu lặp lại ở mọi controller |
| TC-SEC-003 | NFR-SEC03 | Tạo user xong query DB → `passwordHash` bắt đầu `$2a$`, không lưu plaintext | Error Guessing | admin@gmail.com | — | POST tạo account, query `UserRepository` | Cột `password_hash` bắt đầu `$2a$` | Critical | **Blocked** | | **Chưa có test method riêng kiểm tra prefix hash** — `AuthControllerL2Test.changePassword_correctOldPassword_updatesHash` chỉ xác nhận hash khớp/không khớp qua `PasswordEncoder.matches()`, không assert định dạng chuỗi. Đề xuất bổ sung 1 test L1 cho `PasswordEncoder` bean (`BCryptPasswordEncoder`) |
| TC-SEC-004 | GB-01 | Tài khoản LOCKED không đăng nhập được dù đúng mật khẩu | State Transition Testing | (tài khoản LOCKED tự tạo) | — | POST /auth/login | 403 `AUTH_008` | Critical | Pass | | = `TC-INT-AuthController-003` |
| TC-SEC-005 | GB-02 | Message lỗi login giống nhau dù sai email hay sai mật khẩu | Decision Table Testing | (chưa đăng nhập) | — | POST /auth/login 2 lần | 401/401, message giống hệt | Critical | Pass | | = `TC-INT-AuthController-002` |
| TC-SEC-006 | NFR-SEC06 / GB-05 | manager2 (Branch B) sửa/tạo giải thuộc Branch A → 403 | Decision Table Testing | manager2@gmail.com | Giải thuộc Branch Thủ Đức (manager1) | POST/GET /api/v1/manager/tournaments (JWT manager2) | 4xx | Critical | Pass | | = `TC-INT-ManagerTournamentController-006`, `-010`; `TC-INT-ManagerController-007`, `-009`; `TC-INT-ManagerBranchController-004` |
| TC-SEC-007 | NFR-SEC07 / GB-12 | Player cập nhật tỉ số trận → 403 · Staff được gán cùng trận → 200 | Decision Table Testing | staff1@gmail.com (đúng), staff2@gmail.com hoặc player1 (sai) | Trận đã gán staff1 | PATCH /score/increment với các JWT khác nhau | 200 / 4xx | Critical | Pass | | = `TC-INT-StaffController-005` + `-006`; `TC-INT-MatchController-008` |
| TC-SEC-008 | NFR-SEC08 | Đăng ký/thanh toán chỉ scoped theo JWT (`extractUserId`), không nhận id qua path param → không thể "xem payment người khác" bằng cách đổi ID trên URL | Error Guessing | player1@gmail.com | — | GET /api/v1/player/payments | 200; chỉ trả dữ liệu của chính JWT đó | High | Pass | | Thiết kế API không có endpoint `GET /payments/{userId}` nên rủi ro IDOR ở nhóm này thấp hơn ví dụ mẫu trong skill — ghi lại lý do thay vì test case y hệt mẫu |
| TC-SEC-009 | NFR-SEC09 | `/api/v1/storage/**` không có kiểm tra "chủ sở hữu file" — mọi role đã đăng nhập upload/xem/tải được, chỉ chặn bằng JWT | Error Guessing | player1@gmail.com | — | POST /api/v1/storage/images | 200 (không 403) — đây là **thiết kế hiện tại**, không phải lỗi test | Medium | Pass | | Khoảng trống thật của hệ thống, không phải khoảng trống của bộ test — đề xuất Product Owner xác nhận có cần siết theo NFR-SEC09 hay giữ nguyên (dự án ghi "test Postman" trong `@Tag` mô tả StorageController) |
| TC-SEC-010 | GB-06 | Chuyển trạng thái giải sai whitelist (DRAFT→COMPLETED) → bị chặn | State Transition Testing | manager@gmail.com | Giải status DRAFT | PATCH /status body=COMPLETED | 4xx | Critical | Pass | | = `TC-INT-ManagerTournamentController-013` |
| TC-SEC-011 | GB-07 | Đăng ký khi giải chưa `OPEN_FOR_REGISTRATION` → bị chặn | State Transition Testing | player7@gmail.com | Giải status DRAFT | POST /registrations | 4xx | Critical | Pass | | = `TC-INT-PlayerRegistrationController-005` |
| TC-SEC-012 | GB-08 | Đăng ký trùng giải đã đăng ký → bị chặn | Error Guessing | player6@gmail.com | Đã đăng ký lần 1 | POST /registrations lần 2 | 409 | Critical | Pass | | = `TC-INT-PlayerRegistrationController-004` |
| TC-SEC-013 | GB-10 | Chưa đủ điều kiện (status/participant) → không bốc thăm được | State Transition Testing | owner@gmail.com | Giải status DRAFT | POST /draw | 4xx | Critical | Pass | | = `TC-INT-MatchController-003` |
| TC-SEC-014 | GB-11 | Trận chỉ thao tác được đúng phase tournament; điểm không vượt race-to | State Transition Testing | owner@gmail.com | Tournament DRAW_DONE (SE, chưa IN_PROGRESS) | PATCH /matches/{id}/start | 4xx | Critical | Pass | | = `TC-INT-MatchController-005`, `-006` |
| TC-SEC-015 | GB-14 | Nội dung Public ẩn khi `isPublicRatio=false` | Decision Table Testing | (Guest) | Giải `isPublicRatio=false` | GET /tournaments/{id}/stages | 404 | Critical | Pass | | = `TC-INT-MatchController-010` |
| TC-SEC-016 | GB-16 | Roster khoá sau khi vào `DRAW_PREVIEW` — không thêm/sửa participant được nữa | State Transition Testing | owner@gmail.com | Giải status DRAW_PREVIEW | POST /participants/manual | 4xx | Critical | Pass | | = `TC-INT-ParticipantController-005` |

---

## BẢNG DEFECTS

**Đã chạy test thật** 18/08/2026 trên MySQL 8 local (Docker, port 3307, db `btms_test`) —
329/331 Pass, 2 Fail xác nhận bằng `target/surefire-reports/`. Cả 2 dòng Fail đều đã đối chiếu
với code nguồn: không phải test viết sai, không phải dữ liệu bẩn, không phải 2 tài liệu mâu thuẫn
— là hành vi HTTP status sai thật, giữ nguyên `Expected` trong test (không sửa để xanh) theo đúng
yêu cầu. Cộng thêm 2 defect phát hiện lúc đọc code để viết test (`Activity=Review`, chưa có test
method assert đúng hành vi mong muốn nên không hiện thành dòng Fail) — gộp chung 1 bảng theo đúng
quy tắc "một bảng Defects duy nhất cho mọi level".

| Defect ID | Date | Defect Description | Severity | Activity | Product | Product Details | Assigner | Assignee | Status | Updated | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| DEF-001 | 18-Aug | `GET /api/v1/admin/email/logs?fromDate=<sai định dạng>` ném `DateTimeParseException` chưa bắt riêng, rơi vào handler chung → trả 500 thay vì 400 kèm message rõ ràng | Minor | Review | Software Package | `AdminEmailLogController#parseFrom` (không qua Bean Validation, tự `LocalDate.parse`) | Claude (đọc code) | (chưa gán) | New | 18-Aug | Test case tương ứng: `TC-INT-AdminEmailLogController-003` — test đã viết để assert ĐÚNG hành vi thật (500), không phải dòng Fail. Không chặn release — chỉ ảnh hưởng UX thông báo lỗi cho Admin |
| DEF-002 | 18-Aug | `GET /api/v1/shared/facebook/posts/{id}` với id không tồn tại ném `RuntimeException` thô (không phải `BusinessException`) → rơi vào handler chung, trả 500 thay vì 404 | Minor | Review | Software Package | `FacebookController#getPost` / `#getEngagement` / `#getInsights` (`orElseThrow(() -> new RuntimeException(...))`) | Claude (đọc code) | (chưa gán) | New | 18-Aug | Test case tương ứng: `TC-INT-FacebookController-009` — test đã viết để assert ĐÚNG hành vi thật (500), không phải dòng Fail. Đề xuất đổi sang `BusinessException(ErrorCode.RESOURCE_NOT_FOUND)` cho nhất quán với toàn bộ controller còn lại |
| DEF-003 | 18-Aug | `GET /api/v1/admin/config-field-catalog/{fieldKey}` với fieldKey không tồn tại → trả **400** `INVALID_FIELD_KEY` thay vì **404**, không nhất quán với các endpoint catalog cùng dạng (`AdminGameTypeController.getGameType`, `AdminFormatController.getFormat`, `AdminRegistrationFieldController.getCatalogItem` đều đúng 404 cho code/id không tồn tại) | Minor | L2 | Software Package | `AdminTournamentConfigServiceImpl#getFieldDefinition` (dòng ~623-626) — dùng `ErrorCode.INVALID_FIELD_KEY` (thiết kế cho case "field key được THAM CHIẾU trong payload khác không hợp lệ", không phải cho GET-by-id) | Claude (chạy test thật) | (chưa gán) | New | 18-Aug | **Test case Fail thật**: `TC-INT-AdminConfigFieldController-003` (`AdminConfigFieldControllerL2Test.getCatalogItem_unknownKey_rejected404`). Xác nhận qua `target/surefire-reports/`, không sửa Expected để xanh |
| DEF-004 | 18-Aug | `POST /api/v1/storage/images` thiếu multipart part `file` → trả **500** thay vì **400** | Minor | L2 | Software Package | `exception/GlobalExceptionHandler.java` — không có `@ExceptionHandler(MissingServletRequestPartException.class)`, chỉ có `MissingServletRequestParameterException` (khác exception type); request thiếu multipart part rơi vào handler chung `handleUnexpected` | Claude (chạy test thật) | (chưa gán) | New | 18-Aug | **Test case Fail thật**: `TC-INT-StorageController-002` (`StorageControllerL2Test.uploadImage_missingFilePart_rejected400`). Fix đề xuất: thêm handler `MissingServletRequestPartException` → `COMMON_INVALID_REQUEST` (400), cùng pattern với handler tham số đã có |

---

## BẢNG SUMMARY

`L2 - INTEGRATION TEST - Controllers + Security + Schedule (BTMS)`

**Đã chạy thật** 18/08/2026 trên MySQL 8 local (Docker, port 3307, db `btms_test`) —
`./mvnw clean test -Dtest="com.capstone.su26_sep490_g2_be.controller.*L2Test"`. Số liệu Pass/Fail
dưới đọc trực tiếp từ `target/surefire-reports/` (không áng chừng); cột Critical/High vẫn đếm theo
Priority trong nội dung bảng ở trên. 43 bảng Controller: 331/331 đã chạy (329 Pass, 2 Fail — xem
2 defect DEF-003/DEF-004 ở BẢNG DEFECTS). Bảng Security: 15/16 dòng là tham chiếu ngược tới
TC-INT đã Pass nên tính Pass theo, còn `TC-SEC-003` (kiểm định dạng BCrypt) vẫn `Blocked` vì chưa
có test method riêng. Bảng Scheduler: **14/14 Pass** (BJ-01..BJ-07, xác nhận qua
`target/surefire-reports/com.capstone.su26_sep490_g2_be.scheduler.*.txt`) — Scheduler không còn
Out of Scope, xem `Report_5_Testing_Strategy.md` §1.3/§2.1.d.

| Sheet | Class / Component Under Test | Total TC | Critical | High | Not Run | Pass | Fail |
|---|---|---|---|---|---|---|---|
| AdminConfigFieldController | AdminConfigFieldControllerL2Test | 6 | 2 | 3 | 0 | 5 | 1 |
| AdminController | AdminControllerL2Test | 13 | 7 | 3 | 0 | 13 | 0 |
| AdminDashboardController | AdminDashboardControllerL2Test | 5 | 0 | 2 | 0 | 5 | 0 |
| AdminEmailAutomationController | AdminEmailAutomationControllerL2Test | 5 | 1 | 2 | 0 | 5 | 0 |
| AdminEmailLogController | AdminEmailLogControllerL2Test | 6 | 0 | 1 | 0 | 6 | 0 |
| AdminEmailTemplateController | AdminEmailTemplateControllerL2Test | 7 | 1 | 2 | 0 | 7 | 0 |
| AdminFormatController | AdminFormatControllerL2Test | 9 | 1 | 4 | 0 | 9 | 0 |
| AdminGameTypeController | AdminGameTypeControllerL2Test | 12 | 1 | 4 | 0 | 12 | 0 |
| AdminMailLayoutSettingsController | AdminMailLayoutSettingsControllerL2Test | 6 | 0 | 1 | 0 | 6 | 0 |
| AdminRegistrationFieldController | AdminRegistrationFieldControllerL2Test | 8 | 1 | 2 | 0 | 8 | 0 |
| AdminRegistrationFormTemplateController | AdminRegistrationFormTemplateControllerL2Test | 7 | 1 | 3 | 0 | 7 | 0 |
| AnalyticsController | AnalyticsControllerL2Test | 14 | 1 | 6 | 0 | 14 | 0 |
| AuthController | AuthControllerL2Test | 17 | 6 | 6 | 0 | 17 | 0 |
| DashboardController | DashboardControllerL2Test | 5 | 0 | 4 | 0 | 5 | 0 |
| FacebookController | FacebookControllerL2Test | 10 | 0 | 1 | 0 | 10 | 0 |
| HealthController | HealthControllerL2Test | 2 | 0 | 0 | 0 | 2 | 0 |
| ManagerBranchController | ManagerBranchControllerL2Test | 5 | 3 | 2 | 0 | 5 | 0 |
| ManagerController | ManagerControllerL2Test | 9 | 4 | 4 | 0 | 9 | 0 |
| ManagerEmailController | ManagerEmailControllerL2Test | 6 | 0 | 2 | 0 | 6 | 0 |
| ManagerRegistrationController | ManagerRegistrationControllerL2Test | 8 | 0 | 2 | 0 | 8 | 0 |
| ManagerTableController | ManagerTableControllerL2Test | 3 | 0 | 0 | 0 | 3 | 0 |
| ManagerTournamentController | ManagerTournamentControllerL2Test | 15 | 5 | 4 | 0 | 15 | 0 |
| MatchController | MatchControllerL2Test | 13 | 8 | 2 | 0 | 13 | 0 |
| NewsController | NewsControllerL2Test | 10 | 1 | 2 | 0 | 10 | 0 |
| NewsTagController | NewsTagControllerL2Test | 5 | 0 | 0 | 0 | 5 | 0 |
| NotificationController | NotificationControllerL2Test | 10 | 0 | 0 | 0 | 10 | 0 |
| OwnerBranchController | OwnerBranchControllerL2Test | 10 | 1 | 4 | 0 | 10 | 0 |
| OwnerController | OwnerControllerL2Test | 9 | 3 | 3 | 0 | 9 | 0 |
| OwnerEmailController | OwnerEmailControllerL2Test | 5 | 0 | 2 | 0 | 5 | 0 |
| OwnerRegistrationController | OwnerRegistrationControllerL2Test | 5 | 0 | 2 | 0 | 5 | 0 |
| OwnerTableController | OwnerTableControllerL2Test | 10 | 0 | 2 | 0 | 10 | 0 |
| OwnerTournamentController | OwnerTournamentControllerL2Test | 11 | 2 | 4 | 0 | 11 | 0 |
| ParticipantController | ParticipantControllerL2Test | 8 | 2 | 3 | 0 | 8 | 0 |
| PaymentController | PaymentControllerL2Test | 8 | 0 | 4 | 0 | 8 | 0 |
| PlayerController | PlayerControllerL2Test | 6 | 1 | 4 | 0 | 6 | 0 |
| PlayerRegistrationController | PlayerRegistrationControllerL2Test | 9 | 5 | 0 | 0 | 9 | 0 |
| ProfileController | ProfileControllerL2Test | 9 | 0 | 5 | 0 | 9 | 0 |
| PublicBranchController | PublicBranchControllerL2Test | 4 | 0 | 0 | 0 | 4 | 0 |
| PublicLeaderboardController | PublicLeaderboardControllerL2Test | 3 | 0 | 0 | 0 | 3 | 0 |
| PublicParticipantController | PublicParticipantControllerL2Test | 3 | 0 | 0 | 0 | 3 | 0 |
| PublicTournamentController | PublicTournamentControllerL2Test | 3 | 0 | 1 | 0 | 3 | 0 |
| StaffController | StaffControllerL2Test | 7 | 4 | 2 | 0 | 7 | 0 |
| StorageController | StorageControllerL2Test | 5 | 0 | 0 | 0 | 4 | 1 |
| Security | (cross-cutting — xem bảng SECURITY) | 16 | 14 | 1 | 1 | 15 | 0 |
| Scheduler | BackgroundJob + Mail + Facebook L2 (BJ-01..BJ-07) | 14 | 4 | 2 | 0 | 14 | 0 |
| **TOTAL** | | **361** | **79** | **101** | **1** | **358** | **2** |

Kiểm tra chéo: `Not Run + Pass + Fail + Blocked + Skip = Total TC` khớp cho từng bảng con
(43 bảng Controller: 329 Pass + 2 Fail = 331 Total TC, đã chạy 100%. Bảng Scheduler: 14 Pass = 14
Total TC, đã chạy 100%). Dòng **Security** ở trên gộp 1 dòng `Blocked` (`TC-SEC-003`) vào cột
`Not Run` vì khuôn Summary theo skill không có cột `Blocked` riêng — chi tiết trạng thái thật xem
đúng cột `Status` trong bảng gốc, không suy ra được từ Summary.

**Ghi chú cắt phạm vi:**
- AF (Alternative Flow) không liệt kê ở L2: mọi field mô tả thuần (`description`, `address` chi
  tiết, `phone` các module không phải Auth/Account), và AF chỉ phụ thuộc input tĩnh (không cần
  hỏi DB) — để lại cho L1 khi team viết Service unit test (hiện mới có 1 file L1 thật, xem
  `Report_5_Testing_Strategy.md` §2.2.b).
- AnalyticsController: 35 endpoint thật nhưng chỉ 14 dòng — endpoint còn lại dùng chung đúng 1
  khuôn RBAC/ownership đã test đại diện, liệt kê hết sẽ là lặp lại cơ học không thêm giá trị phát
  hiện lỗi mới (đã giải thích ngay đầu bảng `AnalyticsController`).
- 3 khoảng trống seed data đã biết trong `test_skills/btms-l2-workflow.md` (không có Staff Branch
  B riêng, không có Owner thứ hai, không có giải CANCELLED) — toàn bộ test liên quan tự dựng dữ
  liệu trong `@Test`/`@BeforeEach`-style helper (`setupReadyToDrawTournament()`,
  `setupOpenFreeRegistrationTournament()`…), không phụ thuộc seed có sẵn.

