# BTMS — Entities List

Danh sách toàn bộ JPA entity trong `SU26_SEP490_G2_BE/src/main/java/com/capstone/su26_sep490_g2_be/entity/`, nhóm theo domain (đồng bộ với cấu trúc [`Datamodel.md`](../Datamodel.md)).

**Tổng:** 40 file `.java` trong package `entity` → 37 bảng thật, 2 `@Embeddable` (composite key), 1 `@MappedSuperclass`. Tài liệu này **loại `BilliardTable` khỏi phạm vi theo dõi** (theo quyết định của nhóm) → còn **36 entity** được liệt kê bên dưới.

---

## Feature List (FT-ID Reference)

| FT-ID | Feature Name | Priority | Major Feature(s) | Module(s) | UC-ID(s) | Screen(s) |
|---|---|---|---|---|---|---|
| FT-01 | Authentication & Session | Must | User Identity | Module 1 — Authentication & User Management | UC-01, UC-02, UC-03 | SCR-02, SCR-03 |
| FT-02 | Credential & Password Management | Must | User Identity | Module 1 — Authentication & User Management | UC-04, UC-06 | SCR-04, SCR-58, SCR-05(S) |
| FT-03 | Personal Profile Management | Should | User Identity | Module 1 — Authentication & User Management | UC-05 | SCR-05, SCR-16(S) |
| FT-04 | Platform Account Provisioning | Must | Account Provisioning | Module 1 — Authentication & User Management | UC-07 | SCR-21 |
| FT-05 | Employee Account Management | Must | Account Provisioning | Module 1 — Authentication & User Management | UC-08, UC-09 | SCR-42, SCR-43 |
| FT-06 | Game Type Configuration | Must | Tournament Configuration | Module 2 — Admin Platform Configuration | UC-11 | SCR-25 |
| FT-07 | Tournament Format Management | Must | Tournament Configuration | Module 2 — Admin Platform Configuration | UC-12, UC-13 | SCR-22, SCR-23, SCR-24 |
| FT-08 | Custom Field & Form Template Configuration | Must | Tournament Configuration | Module 2 — Admin Platform Configuration | UC-10, UC-14, UC-15 | SCR-26, SCR-27, SCR-28, SCR-29, SCR-30 |
| FT-09 | Branch Management | Must | Tournament Setup | Module 3 — Tournament Management | UC-39 | SCR-51 |
| FT-10 | Tournament Creation & Configuration | Must | Tournament Setup | Module 3 — Tournament Management | UC-16, UC-17 | SCR-33, SCR-34, SCR-35, SCR-36 |
| FT-11 | Tournament Lifecycle & Status Automation | Must | Tournament Lifecycle & Bracket Operations | Module 3 — Tournament Management | UC-18, UC-49, UC-55, UC-56 | SCR-32(S), SCR-33(S), SCR-36 |
| FT-12 | Bracket Draw & Stage Progression | Must | Tournament Lifecycle & Bracket Operations | Module 3 — Tournament Management | UC-19, UC-20 | SCR-39 |
| FT-13 | Match Referee Assignment | Should | Tournament Lifecycle & Bracket Operations | Module 3 — Tournament Management | UC-41 | SCR-39(S) |
| FT-14 | Tournament Registration & Roster Management | Must | Online Registration & Fee Collection | Module 4 — Registration & Payment | UC-23, UC-28, UC-54 | SCR-16, SCR-17, SCR-37, SCR-38 |
| FT-15 | Payment Processing | Must | Online Registration & Fee Collection | Module 4 — Registration & Payment | UC-24, UC-25 | SCR-12, SCR-16(S), SCR-59 |
| FT-16 | Payment History & Records | Should | Registration Administration | Module 4 — Registration & Payment | UC-26, UC-27 | SCR-18, SCR-37(S) |
| FT-17 | Tournament Discovery & Browsing | Must | Public Visibility | Module 5 — Public Discovery & Visibility | UC-21, UC-22 | SCR-01(S), SCR-08, SCR-09, SCR-15 |
| FT-18 | Bracket, Standings & Live Visibility | Must | Public Visibility | Module 5 — Public Discovery & Visibility | UC-31, UC-32, UC-34, UC-52 | SCR-09(S), SCR-15(S), SCR-54 |
| FT-19 | Participant Directory & Public Profile | Should | Public Visibility | Module 5 — Public Discovery & Visibility | UC-29, UC-30 | SCR-09(S), SCR-10, SCR-11 |
| FT-20 | Public News & Blog Viewing | Should | Public Visibility | Module 5 — Public Discovery & Visibility | UC-37 | SCR-01(S), SCR-06, SCR-07 |
| FT-21 | Public Branch Directory | Could | Public Visibility | Module 5 — Public Discovery & Visibility | UC-40 | SCR-52, SCR-53 |
| FT-22 | Live Match Operations | Must | Live Match Execution | Module 6 — Match Operations & Live Scoring | UC-33, UC-53 | SCR-31, SCR-39(S), SCR-55, SCR-56 |
| FT-23 | My Match Schedule | Should | Live Match Execution | Module 6 — Match Operations & Live Scoring | UC-35 | SCR-19 |
| FT-24 | News & Blog Content Management | Should | Content Publishing | Module 7 — Content & Publishing | UC-36, UC-57 | SCR-40, SCR-41, SCR-14 |
| FT-25 | Facebook Publishing & Insights | Should | Content Publishing | Module 7 — Content & Publishing | UC-50, UC-51 | SCR-49, SCR-50 |
| FT-26 | Email Template & Automation Setup | Must | Email Configuration | Module 8 — Email & Notification Management | UC-42, UC-43, UC-44 | SCR-44, SCR-45, SCR-46 |
| FT-27 | Email Delivery & Monitoring | Must | Email Delivery | Module 8 — Email & Notification Management | UC-45, UC-46, UC-47, UC-48 | SCR-47, SCR-48 |
| FT-28 | Operational Dashboard | Should | Business Intelligence | Module 9 — Reporting & Analytics | UC-38 | SCR-20, SCR-32 |

*Nguồn: danh sách feature do nhóm cung cấp — không suy ra từ code, chỉ dùng để đối chiếu/tham chiếu.*

---

## Base

| Entity | Table | Loại | Ghi chú |
|---|---|---|---|
| `BaseEntity` | — | `@MappedSuperclass` | Cấp `created_at` / `updated_at` cho entity kế thừa |

**Kế thừa `BaseEntity`:** `User`, `Branch`, `Tournament`, `Match`, `Registration`, `RegistrationFormTemplate`, `NewsPost`, `NewsCategory`, `EmailTemplate`, `EmailAutomationRule`, `EmailSendLog`, `FacebookPost`, `MailLayoutSettings`.

---

## 1. Auth & User (3)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `Role` | `roles` | Danh mục vai trò hệ thống (ADMIN, OWNER, MANAGER, STAFF, PLAYER) | FT-01, FT-04, FT-05 |
| `User` | `users` | Tài khoản đăng nhập | FT-01, FT-02, FT-04, FT-05 |
| `UserProfile` | `user_profiles` | Hồ sơ người dùng (PK chia sẻ với `users`) | FT-03, FT-19 |

## 2. Branch / Facility (2)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `Branch` | `branches` | Chi nhánh CLB thuộc một Owner | FT-05, FT-09, FT-21 |
| `BranchManager` | `branch_managers` | Gán Manager quản lý chi nhánh (N:N) | FT-05 |

## 3. Tournament Catalog — Admin config (5)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `GameTypeDefinition` | `game_type_definitions` | Danh mục loại bi (9-Ball, 8-Ball, 10-Ball) | FT-06 |
| `TournamentFormatDefinition` | `tournament_format_definitions` | Danh mục thể thức đấu (Single/Double Elim, Group Playoff, Progressive) | FT-07 |
| `ConfigFieldDefinition` | `config_field_definitions` | Catalog field cấu hình dùng chung mọi format | FT-08 |
| `FormatConfigField` | `format_config_fields` | Default field cấu hình gắn theo từng format | FT-07 |
| `FormatRaceToRule` | `format_race_to_rules` | Race-to mặc định theo vòng đấu / format | FT-07 |

## 4. Registration Form Templates (3)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `RegistrationFormTemplate` | `registration_form_templates` | Mẫu form đăng ký giải | FT-08 |
| `RegistrationFieldDefinition` | `registration_field_definitions` | Catalog field cho form đăng ký | FT-08 |
| `RegistrationFormTemplateField` | `registration_form_template_fields` | Field cụ thể gắn vào từng template | FT-08 |

## 5. Tournament Core (6)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `Tournament` | `tournaments` | Giải đấu | FT-10, FT-11, FT-17, FT-28 |
| `TournamentStatusHistory` | `tournament_status_histories` | Lịch sử đổi trạng thái giải | FT-11 |
| `TournamentConfig` | `tournament_configs` | Cấu hình 1:1 của giải | FT-10 |
| `TournamentConfigValue` | `tournament_config_values` | Giá trị override config theo giải (composite PK) | FT-10 |
| `TournamentRaceToRule` | `tournament_race_to_rules` | Override race-to riêng cho giải | FT-10 |
| `TournamentStage` | `tournament_stages` | Giai đoạn thi đấu trong giải (vòng bảng, playoff...) | FT-12, FT-18 |

## 6. Registration & Payment (3)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `Registration` | `registrations` | Đăng ký tham gia giải của user | FT-14, FT-15 |
| `RegistrationFieldValue` | `registration_field_values` | Giá trị field form đăng ký (composite PK) | FT-14 |
| `Payment` | `payments` | Giao dịch thanh toán lệ phí | FT-15, FT-16, FT-28 |

## 7. Participants & Matches (6)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `Participant` | `participants` | Người/đội tham gia thi đấu (sau khi đăng ký được duyệt) | FT-12, FT-14, FT-18, FT-19, FT-23 |
| `ParticipantMember` | `participant_members` | Thành viên trong participant kiểu DOUBLE | FT-14 |
| `Match` | `matches` | Trận đấu | FT-12, FT-13, FT-18, FT-22, FT-23 |
| `MatchScoreEvent` | `match_score_events` | Sự kiện ghi điểm trong trận | FT-22 |
| `TournamentResult` | `tournament_results` | Kết quả/xếp hạng cuối giải | FT-18 |
| `PlayerYearlySummary` | `player_yearly_summaries` | Thống kê thành tích cơ thủ theo năm | FT-19, FT-28 |

## 8. News CMS (4)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `NewsCategory` | `news_categories` | Chuyên mục tin tức | FT-24 |
| `NewsTag` | `news_tags` | Tag bài viết | FT-24 |
| `NewsPost` | `news_posts` | Bài viết tin tức | FT-20, FT-24 |
| *(không có entity riêng — `@ManyToMany` `@JoinTable` trên `NewsPost.tags`)* | `news_post_tags` | Bảng M2M post ↔ tag | FT-24 |

## 9. Email Automation (4)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `EmailTemplate` | `email_templates` | Mẫu email | FT-26, FT-27 |
| `EmailAutomationRule` | `email_automation_rules` | Quy tắc tự động gửi email theo event | FT-26, FT-27 |
| `EmailSendLog` | `email_send_logs` | Log lịch sử gửi email | FT-27 |
| `MailLayoutSettings` | `mail_layout_settings` | Cấu hình layout/branding email chung | FT-26 |

## 10. Facebook Integration (1)

| Entity | Table | Mục đích | FT-ID ref |
|---|---|---|---|
| `FacebookPost` | `facebook_posts` | Bài đăng đồng bộ lên Fanpage Facebook | FT-25 |

---

## Embeddable / Composite Key classes (không phải bảng)

| Class | Dùng cho | Compose từ |
|---|---|---|
| `TournamentConfigValueId` | `TournamentConfigValue` (`@EmbeddedId`) | `tournament_id` + `field_key` |
| `RegistrationFieldValueId` | `RegistrationFieldValue` (`@EmbeddedId`) | `registration_id` + `field_key` |

---

## Appendix — Full Entity → Table map (A-Z theo domain)

| Entity | Table | FT-ID ref |
|---|---|---|
| Role | `roles` | FT-01, FT-04, FT-05 |
| User | `users` | FT-01, FT-02, FT-04, FT-05 |
| UserProfile | `user_profiles` | FT-03, FT-19 |
| Branch | `branches` | FT-05, FT-09, FT-21 |
| BranchManager | `branch_managers` | FT-05 |
| GameTypeDefinition | `game_type_definitions` | FT-06 |
| TournamentFormatDefinition | `tournament_format_definitions` | FT-07 |
| ConfigFieldDefinition | `config_field_definitions` | FT-08 |
| FormatConfigField | `format_config_fields` | FT-07 |
| FormatRaceToRule | `format_race_to_rules` | FT-07 |
| RegistrationFormTemplate | `registration_form_templates` | FT-08 |
| RegistrationFieldDefinition | `registration_field_definitions` | FT-08 |
| RegistrationFormTemplateField | `registration_form_template_fields` | FT-08 |
| Tournament | `tournaments` | FT-10, FT-11, FT-17, FT-28 |
| TournamentStatusHistory | `tournament_status_histories` | FT-11 |
| TournamentConfig | `tournament_configs` | FT-10 |
| TournamentConfigValue | `tournament_config_values` | FT-10 |
| TournamentRaceToRule | `tournament_race_to_rules` | FT-10 |
| TournamentStage | `tournament_stages` | FT-12, FT-18 |
| Registration | `registrations` | FT-14, FT-15 |
| RegistrationFieldValue | `registration_field_values` | FT-14 |
| Payment | `payments` | FT-15, FT-16, FT-28 |
| Participant | `participants` | FT-12, FT-14, FT-18, FT-19, FT-23 |
| ParticipantMember | `participant_members` | FT-14 |
| Match | `matches` | FT-12, FT-13, FT-18, FT-22, FT-23 |
| MatchScoreEvent | `match_score_events` | FT-22 |
| TournamentResult | `tournament_results` | FT-18 |
| PlayerYearlySummary | `player_yearly_summaries` | FT-19, FT-28 |
| NewsCategory | `news_categories` | FT-24 |
| NewsTag | `news_tags` | FT-24 |
| NewsPost | `news_posts` | FT-20, FT-24 |
| *(JoinTable trên NewsPost.tags)* | `news_post_tags` | FT-24 |
| EmailTemplate | `email_templates` | FT-26, FT-27 |
| EmailAutomationRule | `email_automation_rules` | FT-26, FT-27 |
| EmailSendLog | `email_send_logs` | FT-27 |
| MailLayoutSettings | `mail_layout_settings` | FT-26 |
| FacebookPost | `facebook_posts` | FT-25 |

*Nguồn: scan trực tiếp `entity/` package ngày 2026-08-02, đối chiếu [`Datamodel.md`](../Datamodel.md). FT-ID ref do suy luận từ Module/UC-ID trong bảng Feature List ở trên đối chiếu với vai trò thực tế của từng entity trong code — không phải trích xuất trực tiếp từ tài liệu Functional Spec gốc. Đã rà soát lại lần 2 (2026-08-02): chỉ gắn FT-ID khi entity là đối tượng chính của ít nhất 1 UC thuộc FT đó (không tính quan hệ FK gián tiếp). Còn 2 chỗ suy luận chưa chắc chắn, cần nhóm xác nhận: `Payment` → FT-28 (dashboard có hiển thị tổng doanh thu không?) và `PlayerYearlySummary` → FT-19 (public profile có hiển thị thống kê năm không?).*
