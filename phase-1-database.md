# Phase 1 Database — Billiard Club Tournament System

Tài liệu schema dựng theo JPA entities thực tế trong `src/main/java/com/capstone/su26_sep490_g2_be/entity/`.

**Quy ước**

| Ký hiệu | Ý nghĩa |
|---|---|
| `BIGINT PK` | `Long` + `@GeneratedValue(IDENTITY)` |
| `DATETIME` | Kiểu `Instant` trong entity |
| `DATE` | `LocalDate` |
| `DECIMAL(15,2)` | `BigDecimal` |
| `JSON` / `TEXT` / `LONGTEXT` | Theo `columnDefinition` khai trên field |
| Enum | Đa số lưu **String** = `enum.name()`. Chỉ `users.status`, `branches.status`, `billiard_tables.status`, `billiard_tables.table_type` dùng `@Enumerated(STRING)` |

**BaseEntity** (`@MappedSuperclass`) — cấp 2 cột `created_at` (`@CreationTimestamp`, không update) và `updated_at` (`@UpdateTimestamp`).

Entity kế thừa `BaseEntity`: `User`, `Branch`, `BilliardTable`, `DeviceToken`, `RegistrationFormTemplate`, `Tournament`, `Registration`, `Match`, `NewsCategory`, `NewsPost`, `EmailTemplate`, `EmailAutomationRule`, `EmailSendLog`, `MailLayoutSettings`, `FacebookPost`.

---

## Danh sách bảng

Tổng **40 bảng** = 39 entity + 1 join table (`news_post_tags`).

| # | Bảng | Module | Vai trò |
|---|---|---|---|
| 1 | `roles` | Auth | Danh mục vai trò hệ thống |
| 2 | `users` | Auth | Tài khoản đăng nhập |
| 3 | `user_profiles` | Auth | Hồ sơ cá nhân, hạng cơ thủ |
| 4 | `device_tokens` | Auth | Token Expo Push của từng thiết bị |
| 5 | `branches` | Branch | Chi nhánh thuộc chuỗi của Owner |
| 6 | `branch_managers` | Branch | Gán Manager ↔ Branch (N-N) |
| 7 | `billiard_tables` | Branch | Bàn bi-a thuộc Owner / chi nhánh |
| 8 | `game_type_definitions` | Catalog | Danh mục loại bi |
| 9 | `tournament_format_definitions` | Catalog | Danh mục thể thức + handler key |
| 10 | `config_field_definitions` | Catalog | Catalog toàn bộ field cấu hình |
| 11 | `format_config_fields` | Catalog | Field nào áp dụng cho format nào + default |
| 12 | `format_race_to_rules` | Catalog | Race-to mặc định theo vòng của format |
| 13 | `registration_form_templates` | RegForm | Template form đăng ký |
| 14 | `registration_field_definitions` | RegForm | Catalog field của form đăng ký |
| 15 | `registration_form_template_fields` | RegForm | Field thuộc một template |
| 16 | `tournaments` | Tournament | Giải đấu |
| 17 | `tournament_status_histories` | Tournament | Audit log đổi trạng thái giải |
| 18 | `tournament_configs` | Tournament | Header cấu hình giải (1-1) |
| 19 | `tournament_config_values` | Tournament | Giá trị config theo từng field |
| 20 | `tournament_race_to_rules` | Tournament | Race-to override cho giải |
| 21 | `tournament_stages` | Tournament | Giai đoạn thi đấu trong giải |
| 22 | `registrations` | Registration | Đơn đăng ký |
| 23 | `registration_field_values` | Registration | Câu trả lời form đăng ký |
| 24 | `payments` | Payment | Giao dịch thanh toán (PayOS) |
| 25 | `participants` | Match | Đơn vị thi đấu (đơn / đôi) |
| 26 | `participant_members` | Match | Thành viên của participant |
| 27 | `matches` | Match | Trận đấu |
| 28 | `match_score_events` | Match | Lịch sử ghi điểm |
| 29 | `tournament_results` | Ranking | Xếp hạng chung cuộc + tiền thưởng |
| 30 | `player_yearly_summaries` | Ranking | Tổng hợp thành tích theo năm |
| 31 | `news_categories` | News | Chuyên mục tin tức |
| 32 | `news_tags` | News | Tag bài viết |
| 33 | `news_posts` | News | Bài viết tin tức |
| 34 | `news_post_tags` | News | Join table bài viết ↔ tag |
| 35 | `email_templates` | Email | Template email HTML |
| 36 | `email_automation_rules` | Email | Rule gửi email theo sự kiện |
| 37 | `email_send_logs` | Email | Log gửi email |
| 38 | `mail_layout_settings` | Email | Header/footer HTML dùng chung (1 dòng) |
| 39 | `analytics_saved_views` | Analytics | Báo cáo tùy chỉnh đã lưu |
| 40 | `facebook_posts` | Social | Bài Facebook đã đăng + cache tương tác |

---

## 1. Auth & User

### `roles`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `code` | VARCHAR(50) UNIQUE NOT NULL | `ADMIN` / `OWNER` / `MANAGER` / `STAFF` / `PLAYER` |
| `name` | VARCHAR(100) NOT NULL | Tên hiển thị |
| `description` | TEXT NULL | |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `created_at` | DATETIME NOT NULL | Chỉ có `created_at`, **không** kế thừa BaseEntity |

---

### `users`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `email` | VARCHAR(255) UNIQUE NOT NULL | Định danh đăng nhập |
| `phone` | VARCHAR(20) NULL | |
| `password_hash` | VARCHAR(500) NOT NULL | BCrypt |
| `role_id` | BIGINT FK→roles NOT NULL | ManyToOne **EAGER** |
| `status` | VARCHAR(30) NOT NULL | `@Enumerated(STRING)`: `ACTIVE` / `LOCKED` / `DELETED` |
| `owner_id` | BIGINT FK→users NULL | Owner đã tạo tài khoản Manager/Staff này |
| `manage_all_branches` | BOOLEAN NULL DEFAULT false | Chỉ có nghĩa với role `MANAGER` |
| `branch_id` | BIGINT FK→branches NULL | **Chỉ dùng cho STAFF** — 1 staff = 1 chi nhánh. Manager dùng `branch_managers` |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `user_profiles`
PK = `user_id` (`@MapsId`, shared PK với `users`).

| Field | Type | Ghi chú |
|---|---|---|
| `user_id` | BIGINT PK FK→users | |
| `full_name` | VARCHAR(255) NOT NULL | |
| `display_name` | VARCHAR(255) NULL | Tên hiển thị trên bracket |
| `avatar_url` | VARCHAR(1000) NULL | |
| `date_of_birth` | DATE NULL | |
| `gender` | VARCHAR(20) NULL | |
| `billiard_rank` | VARCHAR(20) NULL | Enum `BilliardRank`: `CHAMPION`, `A`–`L`, `UNKNOWN` |
| `bio` | TEXT NULL | |

> Cột `elo` ở bản thiết kế cũ **đã bị bỏ** — thay bằng `billiard_rank`.

---

### `device_tokens`
Địa chỉ nhận push notification của một thiết bị. Một người nhiều máy → 1-N. `expo_token` UNIQUE: máy đổi chủ thì bản ghi được gán lại cho user mới thay vì tạo dòng mới.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK→users NOT NULL | INDEX `idx_device_tokens_user` |
| `expo_token` | VARCHAR(255) UNIQUE NOT NULL | Dạng `ExponentPushToken[...]` |
| `platform` | VARCHAR(20) NOT NULL | `android` / `ios` |
| `last_seen_at` | DATETIME NOT NULL | Dọn token của máy lâu không dùng |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

## 2. Branch / Facility

### `branches`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) NOT NULL | |
| `address` | VARCHAR(500) NOT NULL | |
| `phone` | VARCHAR(20) NULL | |
| `description` | TEXT NULL | |
| `status` | VARCHAR(20) NOT NULL DEFAULT `ACTIVE` | `@Enumerated(STRING)`: `ACTIVE` / `INACTIVE` |
| `owner_id` | BIGINT FK→users NOT NULL | Chủ chuỗi |
| `image_keys` | JSON NULL | Mảng MinIO object key |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `branch_managers`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `branch_id` | BIGINT FK→branches NOT NULL | |
| `manager_id` | BIGINT FK→users NOT NULL | User role `MANAGER` |
| `assigned_by` | BIGINT FK→users NULL | Owner thực hiện gán |
| `assigned_at` | DATETIME NOT NULL | `@CreationTimestamp` |

> UNIQUE(`branch_id`, `manager_id`)
>
> Manager có `manage_all_branches = true` không cần bản ghi ở đây.

---

### `billiard_tables`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `owner_id` | BIGINT FK→users NOT NULL | |
| `branch_id` | BIGINT FK→branches NULL | Chỉ là **gợi ý** chi nhánh, không ràng buộc occupancy. Null = dùng chung cả chuỗi |
| `name` | VARCHAR(100) NOT NULL | |
| `table_number` | INT NULL | |
| `table_type` | VARCHAR(20) NULL | `@Enumerated(STRING)`: `POOL` / `CAROM` / `SNOOKER` / `OTHER` |
| `status` | VARCHAR(20) NOT NULL DEFAULT `ACTIVE` | `@Enumerated(STRING)`: `ACTIVE` / `INACTIVE` |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

## 3. Tournament Catalog (Admin config)

### `game_type_definitions`
| Field | Type | Ghi chú |
|---|---|---|
| `code` | VARCHAR(50) PK | Seed: `9_BALL`, `8_BALL`, `10_BALL` |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `default_race_to` | INT NULL | |
| `compatible_table_types` | JSON NULL | vd `["POOL"]` |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `sort_order` | INT NOT NULL DEFAULT 0 | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

---

### `tournament_format_definitions`
| Field | Type | Ghi chú |
|---|---|---|
| `code` | VARCHAR(50) PK | `SINGLE_ELIMINATION` / `DOUBLE_ELIMINATION` / `PROGRESSIVE_ROUND_ROBIN` |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `handler_key` | VARCHAR(100) UNIQUE NOT NULL | `pool_single_elimination_handler`, … |
| `schema_version` | VARCHAR(20) NULL DEFAULT `'1.0'` | |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `sort_order` | INT NOT NULL DEFAULT 0 | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

> **Chỉ còn 3 thể thức.** `ROUND_ROBIN`, `GROUP_PLAYOFF`, `PROGRESSIVE_ELIMINATION` ở bản thiết kế cũ đã bị gỡ khỏi enum `TournamentFormat` và khỏi seed.

---

### `config_field_definitions`
Catalog master — Developer seed. Admin không tự tạo field mới.

| Field | Type | Ghi chú |
|---|---|---|
| `field_key` | VARCHAR(80) PK | |
| `label` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `data_type` | VARCHAR(20) NOT NULL | `INT` / `BOOLEAN` / `ENUM` / `STRING` |
| `field_scope` | VARCHAR(30) NOT NULL | `COMMON` / `KNOCKOUT` / `PROGRESSIVE` |
| `enum_options` | JSON NULL | Khi `data_type = ENUM` |
| `ui_component` | VARCHAR(30) NOT NULL | `NUMBER` / `SELECT` / `CHECKBOX` / `TEXT` / `RADIO_GROUP` |
| `min_value` | INT NULL | |
| `max_value` | INT NULL | |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

**Catalog đang seed (9 field):**

| field_key | scope | data_type | Default / Options |
|---|---|---|---|
| `break_rule` | COMMON | ENUM | `ALTERNATE_BREAK` / `WINNER_BREAK` / `LOSER_BREAK` |
| `lag_for_break` | COMMON | BOOLEAN | |
| `scoring_unit` | COMMON | ENUM | `GAME` / `FRAME` |
| `bracket_size` | KNOCKOUT | INT | 8–64 |
| `third_place_match` | KNOCKOUT | BOOLEAN | |
| `de_mode` | KNOCKOUT | ENUM | `FULL_DE` / `CUT_TO_SE` |
| `se_phase_size` | KNOCKOUT | INT | 4–256, lũy thừa 2 |
| `pe_survivors_per_stage` | PROGRESSIVE | STRING | vd `10,6,4` — chẵn, giảm dần |
| `final_playoff_size` | PROGRESSIVE | INT | 4 hoặc 8 |

---

### `format_config_fields`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `format_code` | VARCHAR(50) FK→tournament_format_definitions NOT NULL | |
| `field_key` | VARCHAR(80) FK→config_field_definitions NOT NULL | |
| `default_value` | VARCHAR(500) NOT NULL | Text, parse theo `data_type` |
| `is_required` | BOOLEAN NOT NULL DEFAULT true | |
| `is_visible_to_owner` | BOOLEAN NOT NULL DEFAULT true | `false` = chỉ Admin thấy |
| `sort_order` | INT NOT NULL DEFAULT 0 | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`format_code`, `field_key`)

---

### `format_race_to_rules`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `format_code` | VARCHAR(50) FK→tournament_format_definitions NOT NULL | |
| `round_key` | VARCHAR(50) NOT NULL | |
| `label` | VARCHAR(255) NULL | |
| `bracket_phase` | VARCHAR(30) NOT NULL | `KNOCKOUT` / `WINNERS` / `LOSERS` / `GRAND_FINAL` / `FINAL_BRACKET` / `PROGRESSIVE_ROUND` / `PROGRESSIVE_PLAYOFF` |
| `race_to` | INT NOT NULL | |
| `sort_order` | INT NOT NULL DEFAULT 0 | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`format_code`, `round_key`)

**Round key đang seed:**

| Format | round_key |
|---|---|
| `SINGLE_ELIMINATION` | `round_1`, `quarter_final`, `semi_final`, `third_place`, `final` |
| `DOUBLE_ELIMINATION` | `winners_r1`, `winners_qf`, `winners_sf`, `winners_final`, `losers_r1`, `losers_r2`, `losers_r3`, `losers_final`, `grand_final`, `se_round_1`, `se_quarter_final`, `se_semi_final`, `se_final` |
| `PROGRESSIVE_ROUND_ROBIN` | `league_stage`, `playoff` |

> Nhóm `se_*` (phase `FINAL_BRACKET`) chỉ dùng cho bracket Last-X khi `de_mode = CUT_TO_SE`.

---

## Kiến trúc cấu hình (3 tầng)

```text
[Tầng 1] config_field_definitions      ← catalog field (Developer seed)
[Tầng 2] format_config_fields          ← format nào dùng field nào + default
         format_race_to_rules          ← race-to mặc định theo vòng / format
[Tầng 3] tournament_configs            ← header: format_code, seeding_method
         tournament_config_values      ← Owner/Admin điền khi tạo giải
         tournament_race_to_rules      ← override race-to cho giải cụ thể
```

| Ai thao tác | Bảng | Việc làm |
|---|---|---|
| Developer | `config_field_definitions` | Thêm field mới khi release |
| Admin | `format_config_fields`, `format_race_to_rules` | Sửa default từng thể thức |
| Admin / Owner | `tournament_config_values`, `tournament_race_to_rules` | Copy default → chỉnh cho giải |
| System | `matches.race_to` | Sinh từ `tournament_race_to_rules`, fallback `format_race_to_rules` |

---

## 4. Registration Form Templates

### `registration_form_templates`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `code` | VARCHAR(50) UNIQUE NOT NULL | vd `PLAYER_REG_BASIC` |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `sort_order` | INT NOT NULL DEFAULT 0 | |
| `created_by` | BIGINT FK→users NOT NULL | |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `registration_field_definitions`
| Field | Type | Ghi chú |
|---|---|---|
| `field_key` | VARCHAR(80) PK | vd `player_full_name`, `player_phone` |
| `label` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `data_type` | VARCHAR(20) NOT NULL | `STRING` / `PHONE` / … |
| `enum_options` | JSON NULL | |
| `ui_component` | VARCHAR(30) NOT NULL | `TEXT` / `PHONE_INPUT` / … |
| `min_value` | INT NULL | |
| `max_value` | INT NULL | |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

---

### `registration_form_template_fields`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `template_id` | BIGINT FK→registration_form_templates NOT NULL | |
| `field_key` | VARCHAR(80) FK→registration_field_definitions NOT NULL | |
| `label_override` | VARCHAR(255) NULL | |
| `description_override` | TEXT NULL | |
| `placeholder` | VARCHAR(200) NULL | |
| `validation_regex` | VARCHAR(500) NULL | |
| `default_value` | VARCHAR(500) NULL | |
| `is_required` | BOOLEAN NOT NULL DEFAULT true | |
| `sort_order` | INT NOT NULL DEFAULT 0 | |
| `created_at` / `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`template_id`, `field_key`)

---

## 5. Tournament Core

### `tournaments`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `thumbnail_url` | VARCHAR(1000) NULL | |
| `banner_url` | VARCHAR(1000) NULL | |
| `game_type` | VARCHAR(50) FK→game_type_definitions NOT NULL | |
| `participant_type` | VARCHAR(30) NOT NULL | `SINGLE` / `DOUBLE` |
| `format` | VARCHAR(50) FK→tournament_format_definitions NOT NULL | |
| `status` | VARCHAR(50) NOT NULL DEFAULT `DRAFT` | Xem state machine bên dưới |
| `max_participants` | INT NOT NULL | |
| `table_count` | INT NULL DEFAULT 1 | Auto-scheduler gán bàn `1..table_count` |
| `entry_fee` | DECIMAL(15,2) NULL DEFAULT 0 | |
| `prize_pool` | DECIMAL(15,2) NULL | |
| `prize_description` | TEXT NULL | |
| `registration_deadline` | DATETIME NULL | |
| `start_at` | DATETIME NULL | |
| `end_at` | DATETIME NULL | |
| `created_by` | BIGINT FK→users NOT NULL | |
| `is_show_tournament` | BOOLEAN NULL DEFAULT false | Hiện ở trang công khai |
| `is_public_ratio` | BOOLEAN NULL DEFAULT false | Công khai tỉ lệ / thống kê |
| `is_register` | BOOLEAN NULL DEFAULT false | Bật đăng ký online |
| `registration_form_template_id` | BIGINT FK→registration_form_templates NULL | |
| `branch_id` | BIGINT FK→branches NULL | Địa điểm tổ chức |
| `venue_name` | VARCHAR(255) NULL | Snapshot tên chi nhánh |
| `venue_address` | VARCHAR(500) NULL | Snapshot địa chỉ |
| `version` | BIGINT NULL | `@Version` — optimistic lock |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

**Index:**
- `idx_tournaments_public_list` (`is_show_tournament`, `status`, `created_at`)
- `idx_tournaments_owner_list` (`created_by`, `created_at`)

---

### `tournament_status_histories`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `from_status` | VARCHAR(50) NOT NULL | |
| `to_status` | VARCHAR(50) NOT NULL | |
| `change_type` | VARCHAR(20) NOT NULL | `MANUAL` / `AUTO` / `WARNING` |
| `changed_by` | BIGINT FK→users NULL | Null khi do job hệ thống |
| `note` | TEXT NULL | |
| `created_at` | DATETIME NOT NULL | `@CreationTimestamp` |

---

### `tournament_configs`
PK = `tournament_id` (`@MapsId`, 1-1 với `tournaments`).

| Field | Type | Ghi chú |
|---|---|---|
| `tournament_id` | BIGINT PK FK→tournaments | |
| `format_code` | VARCHAR(50) FK→tournament_format_definitions NULL | |
| `seeding_method` | VARCHAR(30) NOT NULL DEFAULT `RANDOM` | `RANDOM` / `RANK` |
| `config_snapshot_json` | JSON NULL | Cache key→value; nguồn đúng vẫn là `tournament_config_values` |
| `version` | BIGINT NULL | `@Version` — optimistic lock |
| `updated_at` | DATETIME NOT NULL | `@UpdateTimestamp` |

> `MANUAL` và `ELO` ở bản cũ **đã gỡ**. `RANK` xếp theo `BilliardRank`; muốn can thiệp tay thì đổi chỗ ở màn Bốc thăm sau khi sinh bracket.

---

### `tournament_config_values`
| Field | Type | Ghi chú |
|---|---|---|
| `tournament_id` | BIGINT FK→tournaments | PK composite |
| `field_key` | VARCHAR(80) FK→config_field_definitions | PK composite |
| `value` | VARCHAR(500) NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> PK(`tournament_id`, `field_key`) — `@EmbeddedId TournamentConfigValueId`

---

### `tournament_race_to_rules`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `round_key` | VARCHAR(50) NOT NULL | Cùng quy ước `format_race_to_rules` |
| `bracket_phase` | VARCHAR(30) NOT NULL | |
| `race_to` | INT NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`tournament_id`, `round_key`)

---

### `tournament_stages`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `name` | VARCHAR(255) NOT NULL | |
| `stage_type` | VARCHAR(50) NOT NULL | `KNOCKOUT` / `WINNERS` / `LOSERS` / `GRAND_FINAL` / `PROGRESSIVE_ROUND` / `PROGRESSIVE_PLAYOFF` / `FINAL_BRACKET` |
| `order_no` | INT NOT NULL | |
| `status` | VARCHAR(30) NOT NULL DEFAULT `PENDING` | `PENDING` / `IN_PROGRESS` / `COMPLETED` |
| `pe_round_no` | INT NULL | [Progressive] Vòng thứ mấy |
| `pe_active_count` | INT NULL | [Progressive] Số người còn lại đầu vòng |
| `pe_eliminate_count` | INT NULL | [Progressive] Số người bị loại sau vòng |

> UNIQUE(`tournament_id`, `order_no`)

**Số stages theo format:**

| Format | Stages (theo `BracketGenerationServiceImpl`) |
|---|---|
| `SINGLE_ELIMINATION` | 1 × `KNOCKOUT` |
| `DOUBLE_ELIMINATION` — `de_mode = FULL_DE` | `WINNERS` (1) + `LOSERS` (2) + `GRAND_FINAL` (3) |
| `DOUBLE_ELIMINATION` — `de_mode = CUT_TO_SE` | `WINNERS` (1) + `LOSERS` (2) + `FINAL_BRACKET` (3, "Last X — Loại trực tiếp") — **không có** `GRAND_FINAL` |
| `PROGRESSIVE_ROUND_ROBIN` | n × `PROGRESSIVE_ROUND` + 1 × `PROGRESSIVE_PLAYOFF` |

> ⚠️ `FINAL_BRACKET` được service ghi thẳng dạng chuỗi và **chưa có** trong enum `TournamentStageType`. Cột là `String` nên không lỗi runtime, nhưng code nào duyệt bằng `TournamentStageType.valueOf()` sẽ ném exception với stage này.

---

## 6. Registration & Payment

### `registrations`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `user_id` | BIGINT FK→users NULL | **Null** với đăng ký `MANUAL` (import Excel / walk-in chưa có tài khoản) |
| `registration_type` | VARCHAR(30) NOT NULL | `SINGLE` / `DOUBLE` / `TEAM` / `MANUAL` / `ONLINE_REGISTRATION` |
| `player_full_name` | VARCHAR(255) NOT NULL | Snapshot |
| `player_phone` | VARCHAR(20) NULL | Snapshot |
| `status` | VARCHAR(50) NOT NULL DEFAULT `PENDING_PAYMENT` | `PENDING_PAYMENT` / `PAID` / `APPROVED` / `REJECTED` / `CANCELLED` |
| `note` | TEXT NULL | |
| `rejected_reason` | TEXT NULL | |
| `approved_by` | BIGINT FK→users NULL | |
| `approved_at` | DATETIME NULL | |
| `rejected_at` | DATETIME NULL | |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

> UNIQUE(`tournament_id`, `user_id`)
>
> `PENDING_REVIEW` ở bản cũ **không còn** trong enum `RegistrationStatus`.

---

### `registration_field_values`
| Field | Type | Ghi chú |
|---|---|---|
| `registration_id` | BIGINT FK→registrations | PK composite |
| `field_key` | VARCHAR(80) FK→registration_field_definitions | PK composite |
| `value` | TEXT NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> PK(`registration_id`, `field_key`) — `@EmbeddedId RegistrationFieldValueId`

---

### `payments`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK→users NOT NULL | |
| `registration_id` | BIGINT FK→registrations NULL | |
| `amount` | DECIMAL(15,2) NOT NULL | |
| `payment_method` | VARCHAR(30) NOT NULL | Hiện hard-code `PAYOS` |
| `status` | VARCHAR(30) NOT NULL DEFAULT `PENDING` | `PENDING` / `SUCCESS` / `FAILED` / `CANCELLED` |
| `transaction_code` | VARCHAR(255) NULL | |
| `checkout_url` | VARCHAR(1000) NULL | Link thanh toán PayOS |
| `paid_at` | DATETIME NULL | |
| `created_at` | DATETIME NOT NULL | `@CreationTimestamp` |

> `REFUNDED` ở bản cũ **không còn**; các phương thức `VNPAY` / `MOMO` / `BANK_TRANSFER` / `CASH` chưa triển khai.

---

## 7. Participants & Matches

### `participants`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `registration_id` | BIGINT FK→registrations NULL | Null = participant tạo thẳng, không qua đăng ký |
| `participant_type` | VARCHAR(30) NOT NULL | `SINGLE` / `DOUBLE` |
| `display_name` | VARCHAR(255) NOT NULL | |
| `billiard_rank` | VARCHAR(20) NULL | **Snapshot** hạng lúc tạo participant — cơ thủ đổi hạng giữa giải không làm đổi bracket. Null/lạ = `UNKNOWN` |
| `status` | VARCHAR(30) NOT NULL DEFAULT `ACTIVE` | `ACTIVE` / `INACTIVE` / `WITHDRAWN` |
| `avtar_url` | VARCHAR(1000) NULL | **Tên cột sai chính tả trong code** (`avtar_url`, không phải `avatar_url`) |

**Index:** `idx_participants_tournament_status` (`tournament_id`, `status`)

> Cột `seed_no` ở bản cũ **đã bị gỡ** (cùng lúc gỡ `SeedingMethod.MANUAL`). `ELIMINATED` đổi thành `INACTIVE`.

---

### `participant_members`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `participant_id` | BIGINT FK→participants NOT NULL | |
| `user_id` | BIGINT FK→users NULL | Null nếu thành viên chưa có tài khoản |
| `full_name` | VARCHAR(255) NOT NULL | Snapshot |
| `phone` | VARCHAR(20) NULL | Snapshot |
| `role` | VARCHAR(30) NOT NULL | `CAPTAIN` / `PARTNER` |

> UNIQUE(`participant_id`, `user_id`)

---

### `matches`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `stage_id` | BIGINT FK→tournament_stages NOT NULL | |
| `bracket_type` | VARCHAR(30) NOT NULL | `KNOCKOUT` / `WINNERS` / `LOSERS` / `GRAND_FINAL` / `PROGRESSIVE_ROUND` / `PROGRESSIVE_PLAYOFF` |
| `round_no` | INT NOT NULL | |
| `position_no` | INT NOT NULL | |
| `match_code` | VARCHAR(50) NULL | Enum `MatchCode` lưu **code**: `3RD` (tranh hạng 3) |
| `player1_participant_id` | BIGINT FK→participants NULL | |
| `player2_participant_id` | BIGINT FK→participants NULL | |
| `player1_score` | INT NOT NULL DEFAULT 0 | |
| `player2_score` | INT NOT NULL DEFAULT 0 | |
| `winner_participant_id` | BIGINT FK→participants NULL | |
| `loser_participant_id` | BIGINT FK→participants NULL | |
| `next_match_win_id` | BIGINT FK→matches NULL | |
| `next_match_lose_id` | BIGINT FK→matches NULL | Double elim |
| `win_slot` | VARCHAR(20) NULL | Slot người thắng vào ở trận kế — **chữ thường**: `player1` / `player2` |
| `lose_slot` | VARCHAR(20) NULL | Slot người thua vào ở trận kế — **chữ thường**: `player1` / `player2` |
| `scheduled_at` | DATETIME NULL | |
| `race_to` | INT NOT NULL | |
| `status` | VARCHAR(30) NOT NULL DEFAULT `PENDING` | `PENDING` / `IN_PROGRESS` / `COMPLETED` / `BYE` / `WALKOVER` |
| `is_bye` | BOOLEAN NOT NULL DEFAULT false | |
| `assigned_staff_id` | BIGINT FK→users NULL | Trọng tài điều hành |
| `table_no` | INT NULL | Số bàn tự do — legacy fallback, đồng bộ từ `table.table_number` |
| `table_id` | BIGINT FK→billiard_tables NULL | Bàn thật trong pool |
| `estimated_end_at` | DATETIME NULL | Auto-scheduler tính theo race-to + loại bi |
| `schedule_locked` | BOOLEAN NOT NULL DEFAULT false | true = owner gán tay → auto-scheduler bỏ qua |
| `version` | BIGINT NULL | `@Version` — optimistic lock |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

> Trạng thái `SCHEDULED` và `CANCELLED` ở bản cũ **không còn**; thay bằng `BYE` / `WALKOVER`.
> Trận coi là đã xong (resolved) khi status ∈ {`COMPLETED`, `BYE`, `WALKOVER`}.
>
> `bracket_type` **không** luôn trùng `stage.stage_type`: các trận trong stage `FINAL_BRACKET`
> (Last-X của `CUT_TO_SE`) mang `bracket_type = KNOCKOUT`.

**Quy tắc liên kết vòng sau:**

| bracket_type | `next_match_win_id` | `next_match_lose_id` |
|---|---|---|
| `WINNERS` | ✅ | ✅ → sang nhánh thua |
| `LOSERS` | ✅ | ❌ NULL |
| `KNOCKOUT` | ✅ | ❌ NULL (trừ liên kết trận tranh hạng 3) |
| `GRAND_FINAL` | ❌ NULL | ❌ NULL |
| `PROGRESSIVE_ROUND` / `PROGRESSIVE_PLAYOFF` | ❌ NULL | ❌ NULL |

---

### `match_score_events`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `match_id` | BIGINT FK→matches NOT NULL | |
| `scored_by_participant_id` | BIGINT FK→participants NULL | |
| `player1_score_after` | INT NOT NULL | |
| `player2_score_after` | INT NOT NULL | |
| `event_type` | VARCHAR(30) NOT NULL | |
| `created_by` | BIGINT FK→users NOT NULL | |
| `created_at` | DATETIME NOT NULL | `@CreationTimestamp` |

---

## 8. Ranking

### `tournament_results`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NOT NULL | |
| `participant_id` | BIGINT FK→participants NOT NULL | |
| `final_rank` | INT NOT NULL | |
| `prize_amount` | DECIMAL(15,2) NULL DEFAULT 0 | |
| `points_earned` | INT NOT NULL DEFAULT 0 | |
| `note` | TEXT NULL | Lưu **displayName** tiếng Việt của `RankingPlacementNote`: `Vô địch`, `Á quân`, `Hạng 3`, `Hạng 4`, `Bán kết`, `Dẫn đầu bảng` |
| `recorded_at` | DATETIME NOT NULL | |
| `recorded_by` | BIGINT FK→users NULL | |

> UNIQUE(`tournament_id`, `participant_id`) · UNIQUE(`tournament_id`, `final_rank`)

---

### `player_yearly_summaries`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK→users NOT NULL | |
| `year` | INT NOT NULL | |
| `tournaments_played` | INT NOT NULL DEFAULT 0 | |
| `champion_count` | INT NOT NULL DEFAULT 0 | |
| `runner_up_count` | INT NOT NULL DEFAULT 0 | |
| `top3_count` | INT NOT NULL DEFAULT 0 | |
| `total_prize_amount` | DECIMAL(15,2) NOT NULL DEFAULT 0 | |
| `total_points` | INT NOT NULL DEFAULT 0 | |
| `updated_at` | DATETIME NOT NULL | `@UpdateTimestamp` |

> UNIQUE(`user_id`, `year`)

---

## 9. News CMS

### `news_categories`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) NOT NULL | |
| `slug` | VARCHAR(255) UNIQUE NOT NULL | |
| `status` | VARCHAR(30) NOT NULL DEFAULT `ACTIVE` | `ACTIVE` / `INACTIVE` |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `news_tags`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(100) NOT NULL | |
| `slug` | VARCHAR(100) UNIQUE NOT NULL | |

---

### `news_posts`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `category_id` | BIGINT FK→news_categories NOT NULL | |
| `title` | VARCHAR(255) NOT NULL | |
| `slug` | VARCHAR(255) UNIQUE NOT NULL | |
| `thumbnail_url` | VARCHAR(1000) NULL | |
| `content` | TEXT NOT NULL | |
| `status` | VARCHAR(30) NOT NULL DEFAULT `DRAFT` | `DRAFT` / `PUBLISHED` / `HIDDEN` |
| `published_at` | DATETIME NULL | |
| `created_by` | BIGINT FK→users NOT NULL | |
| `deleted` | BOOLEAN NOT NULL DEFAULT false | Soft delete |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

> Quan hệ tag: `@ManyToMany` + `@BatchSize(50)` qua `news_post_tags`.

---

### `news_post_tags`
| Field | Type | Ghi chú |
|---|---|---|
| `post_id` | BIGINT FK→news_posts | PK composite |
| `tag_id` | BIGINT FK→news_tags | PK composite |

---

## 10. Email Automation

### `email_templates`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `code` | VARCHAR(100) UNIQUE NOT NULL | |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `subject_template` | VARCHAR(500) NOT NULL | |
| `body_html_template` | LONGTEXT NOT NULL | |
| `category` | VARCHAR(30) NOT NULL | `SYSTEM` / `TOURNAMENT` / `MARKETING` / `TRANSACTIONAL` |
| `scope` | VARCHAR(20) NOT NULL DEFAULT `GLOBAL` | `GLOBAL` / `OWNER` / `TOURNAMENT` |
| `owner_id` | BIGINT FK→users NULL | |
| `is_active` | BOOLEAN NOT NULL DEFAULT true | |
| `created_by` | BIGINT FK→users NULL | |
| `available_variables` | JSON NULL | Placeholder cho UI |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `email_automation_rules`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `code` | VARCHAR(100) UNIQUE NOT NULL | |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `event_type` | VARCHAR(50) NOT NULL | Enum `EmailEventType` (19 giá trị) |
| `template_id` | BIGINT FK→email_templates NOT NULL | |
| `scope` | VARCHAR(20) NOT NULL DEFAULT `GLOBAL` | `GLOBAL` / `OWNER` / `TOURNAMENT` |
| `tournament_id` | BIGINT FK→tournaments NULL | Khi scope = TOURNAMENT |
| `recipient_type` | VARCHAR(30) NOT NULL | `PLAYER` / `ALL_PARTICIPANTS` / `MATCH_PLAYERS` / `REGISTRATION_USER` / `CUSTOM_LIST` / `ROLE_STAFF` |
| `is_enabled` | BOOLEAN NOT NULL DEFAULT true | |
| `delay_minutes` | INT NOT NULL DEFAULT 0 | |
| `conditions` | JSON NULL | Filter tùy chọn |
| `created_by` | BIGINT FK→users NULL | |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `email_send_logs`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `template_id` | BIGINT FK→email_templates NULL | |
| `rule_id` | BIGINT FK→email_automation_rules NULL | |
| `tournament_id` | BIGINT FK→tournaments NULL | |
| `trigger_type` | VARCHAR(20) NOT NULL | `MANUAL` / `AUTOMATION` / `SYSTEM` |
| `recipient_email` | VARCHAR(255) NOT NULL | |
| `recipient_user_id` | BIGINT FK→users NULL | |
| `subject_rendered` | VARCHAR(500) NOT NULL | |
| `body_rendered` | LONGTEXT NULL | |
| `status` | VARCHAR(20) NOT NULL | `QUEUED` / `SENT` / `FAILED` / `CANCELLED` |
| `error_message` | TEXT NULL | |
| `sent_at` | DATETIME NULL | |
| `idempotency_key` | VARCHAR(150) NULL | Chống gửi trùng |
| `created_by` | BIGINT FK→users NULL | |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

### `mail_layout_settings`
Bảng cấu hình **1 dòng duy nhất** — header/footer HTML dùng chung cho mọi email, sửa qua Admin UI thay vì hardcode. Nội dung render bằng cùng engine với `body_html_template`, hỗ trợ placeholder `{{system.*}}`.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `header_html` | LONGTEXT NOT NULL | |
| `footer_html` | LONGTEXT NOT NULL | |
| `updated_by` | BIGINT FK→users NULL | |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

## 11. Analytics & Social

### `analytics_saved_views`
Báo cáo tùy chỉnh Owner/Manager lưu từ tab "Khám phá". Thuộc riêng người tạo — Owner và Manager không dùng chung.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK→users NOT NULL | |
| `name` | VARCHAR(120) NOT NULL | |
| `config_json` | TEXT NOT NULL | JSON hóa nguyên trạng `AnalyticsQueryRequest` |
| `created_at` | DATETIME NOT NULL | `@CreationTimestamp` |

---

### `facebook_posts`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments NULL | |
| `facebook_post_id` | VARCHAR(100) NOT NULL | ID bài trên Facebook |
| `content` | TEXT NULL | |
| `post_type` | VARCHAR(30) NOT NULL | |
| `posted_by` | BIGINT FK→users NULL | |
| `posted_at` | DATETIME NOT NULL | |
| `permalink_url` | VARCHAR(1000) NULL | |
| `full_picture_url` | VARCHAR(2000) NULL | |
| `likes_count` | INT NULL | Cache — list không cần gọi Graph API realtime |
| `comments_count` | INT NULL | |
| `shares_count` | INT NULL | |
| `impressions` | INT NULL | |
| `reach` | INT NULL | |
| `engaged_users` | INT NULL | |
| `stats_synced_at` | DATETIME NULL | |
| `created_at` / `updated_at` | DATETIME NOT NULL | BaseEntity |

---

## Mô tả quan hệ giữa các bảng

Đọc bảng theo hướng: **1 dòng ở bảng cha ↔ N dòng ở bảng con**, quan hệ được mang bởi cột nêu ở cột "Cột nối". Cột nối `NULL` = quan hệ tùy chọn (0..N), `NOT NULL` = bắt buộc (1..N).

### Bảng gốc — không tham chiếu bảng nào

`roles`, `game_type_definitions`, `tournament_format_definitions`, `config_field_definitions`, `registration_field_definitions`, `news_categories`, `news_tags`.

Đây là các bảng danh mục do Developer/Admin seed, nằm ở đáy đồ thị phụ thuộc — chèn dữ liệu trước tất cả các bảng còn lại.

---

### Auth & phân quyền

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `roles` | `users` | `role_id` | NOT NULL | Mỗi tài khoản giữ đúng một vai trò |
| `users` | `user_profiles` | `user_id` | PK chung | **1-1**: hồ sơ tách khỏi bảng đăng nhập, xóa user là mất hồ sơ |
| `users` | `users` | `owner_id` | NULL | **Tự tham chiếu**: Owner là người tạo ra tài khoản Manager/Staff |
| `users` | `device_tokens` | `user_id` | NOT NULL | Một người đăng nhập nhiều máy → nhiều token push |

### Chi nhánh & cơ sở vật chất

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `users` | `branches` | `owner_id` | NOT NULL | Một Owner sở hữu cả chuỗi chi nhánh |
| `branches` | `users` | `branch_id` | NULL | **Chỉ áp dụng cho STAFF** — một nhân viên gắn với đúng một chi nhánh |
| `branches` | `branch_managers` | `branch_id` | NOT NULL | Một chi nhánh có nhiều quản lý |
| `users` | `branch_managers` | `manager_id` | NOT NULL | Một quản lý phụ trách nhiều chi nhánh |
| `users` | `billiard_tables` | `owner_id` | NOT NULL | Bàn thuộc quyền sở hữu của Owner |
| `branches` | `billiard_tables` | `branch_id` | NULL | Chỉ là **gợi ý** chi nhánh; null = bàn dùng chung cả chuỗi |

> `branch_managers` là bảng nối **N-N** giữa `branches` và `users`. Manager có `manage_all_branches = true` phụ trách toàn chuỗi nên **không** cần dòng nào ở bảng này.

### Catalog cấu hình thể thức

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `tournament_format_definitions` | `format_config_fields` | `format_code` | NOT NULL | Thể thức dùng những field cấu hình nào |
| `config_field_definitions` | `format_config_fields` | `field_key` | NOT NULL | Một field có thể dùng lại ở nhiều thể thức |
| `tournament_format_definitions` | `format_race_to_rules` | `format_code` | NOT NULL | Race-to mặc định theo từng vòng của thể thức |

> `format_config_fields` là bảng nối **N-N** giữa thể thức và field, đồng thời mang thuộc tính riêng của cặp đó (`default_value`, `is_required`, `is_visible_to_owner`, `sort_order`).

### Template form đăng ký

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `registration_form_templates` | `registration_form_template_fields` | `template_id` | NOT NULL | Một template gồm nhiều field |
| `registration_field_definitions` | `registration_form_template_fields` | `field_key` | NOT NULL | Một field dùng lại ở nhiều template |

> Cũng là bảng nối **N-N** mang thuộc tính: `label_override`, `placeholder`, `validation_regex`, `is_required`, `sort_order`.

### Giải đấu

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `game_type_definitions` | `tournaments` | `game_type` | NOT NULL | Giải đánh loại bi nào |
| `tournament_format_definitions` | `tournaments` | `format` | NOT NULL | Giải theo thể thức nào |
| `branches` | `tournaments` | `branch_id` | NULL | Nơi tổ chức; `venue_name`/`venue_address` là bản snapshot |
| `registration_form_templates` | `tournaments` | `registration_form_template_id` | NULL | Form áp dụng khi bật đăng ký online |
| `tournaments` | `tournament_configs` | `tournament_id` | PK chung | **1-1**: header cấu hình của giải |
| `tournament_format_definitions` | `tournament_configs` | `format_code` | NULL | Thể thức chốt tại thời điểm cấu hình |
| `tournaments` | `tournament_config_values` | `tournament_id` | PK ghép | Giá trị từng tham số của giải |
| `config_field_definitions` | `tournament_config_values` | `field_key` | PK ghép | Tham số đó là field nào trong catalog |
| `tournaments` | `tournament_race_to_rules` | `tournament_id` | NOT NULL | Override race-to riêng cho giải |
| `tournaments` | `tournament_stages` | `tournament_id` | NOT NULL | Giải chia thành các giai đoạn |
| `tournaments` | `tournament_status_histories` | `tournament_id` | NOT NULL | Nhật ký đổi trạng thái |

> `tournament_config_values` nối **N-N** giữa `tournaments` và `config_field_definitions`, khóa chính ghép `(tournament_id, field_key)`.

### Đăng ký & thanh toán

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `tournaments` | `registrations` | `tournament_id` | NOT NULL | Đơn đăng ký thuộc một giải |
| `users` | `registrations` | `user_id` | **NULL** | Null với đăng ký `MANUAL` — người chơi walk-in chưa có tài khoản |
| `registrations` | `registration_field_values` | `registration_id` | PK ghép | Câu trả lời form của đơn |
| `registration_field_definitions` | `registration_field_values` | `field_key` | PK ghép | Trả lời cho field nào |
| `users` | `payments` | `user_id` | NOT NULL | Người trả tiền |
| `registrations` | `payments` | `registration_id` | NULL | Một đơn có thể phát sinh nhiều lần thanh toán (retry) |

### Thi đấu

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `tournaments` | `participants` | `tournament_id` | NOT NULL | Danh sách dự thi của giải |
| `registrations` | `participants` | `registration_id` | NULL | **1 ↔ 0..1**: đơn được duyệt mới sinh participant; null = thêm thẳng không qua đăng ký |
| `participants` | `participant_members` | `participant_id` | NOT NULL | Đơn: 1 dòng · Đôi: 2 dòng |
| `users` | `participant_members` | `user_id` | NULL | Null nếu thành viên chưa có tài khoản |
| `tournaments` | `matches` | `tournament_id` | NOT NULL | |
| `tournament_stages` | `matches` | `stage_id` | NOT NULL | Trận nằm trong giai đoạn nào |
| `participants` | `matches` | `player1_participant_id`, `player2_participant_id`, `winner_participant_id`, `loser_participant_id` | NULL | **4 cột cùng trỏ về `participants`**; null ở vòng sau, điền dần khi có kết quả |
| `matches` | `matches` | `next_match_win_id`, `next_match_lose_id` | NULL | **Tự tham chiếu** — dựng cây bracket: người thắng/thua đi tiếp trận nào |
| `billiard_tables` | `matches` | `table_id` | NULL | Bàn được xếp cho trận |
| `users` | `matches` | `assigned_staff_id` | NULL | Trọng tài điều hành |
| `matches` | `match_score_events` | `match_id` | NOT NULL | Nhật ký ghi điểm từng ván |
| `participants` | `match_score_events` | `scored_by_participant_id` | NULL | Ai vừa ghi điểm |

> `matches` là bảng có nhiều quan hệ nhất: 4 cột về `participants`, 2 cột tự tham chiếu, cộng `tournaments`, `tournament_stages`, `billiard_tables`, `users`.

### Xếp hạng

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `tournaments` | `tournament_results` | `tournament_id` | NOT NULL | Bảng kết quả chung cuộc |
| `participants` | `tournament_results` | `participant_id` | NOT NULL | Mỗi participant đúng một hạng |
| `users` | `player_yearly_summaries` | `user_id` | NOT NULL | Tổng hợp theo năm cho từng cơ thủ |

### Tin tức

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `news_categories` | `news_posts` | `category_id` | NOT NULL | Bài thuộc một chuyên mục |
| `news_posts` | `news_post_tags` | `post_id` | PK ghép | |
| `news_tags` | `news_post_tags` | `tag_id` | PK ghép | |

> `news_post_tags` là bảng nối **N-N thuần** — chỉ có 2 cột khóa, không mang thuộc tính nào.

### Email

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `email_templates` | `email_automation_rules` | `template_id` | NOT NULL | Rule dùng template nào để render |
| `tournaments` | `email_automation_rules` | `tournament_id` | NULL | Chỉ set khi `scope = TOURNAMENT` |
| `users` | `email_templates` | `owner_id` | NULL | Chỉ set khi `scope = OWNER` |
| `email_templates` | `email_send_logs` | `template_id` | NULL | |
| `email_automation_rules` | `email_send_logs` | `rule_id` | NULL | Null khi gửi thủ công |
| `tournaments` | `email_send_logs` | `tournament_id` | NULL | |
| `users` | `email_send_logs` | `recipient_user_id` | NULL | Null khi gửi tới email ngoài hệ thống |

> `mail_layout_settings` đứng gần như độc lập — chỉ 1 dòng dữ liệu, liên kết duy nhất là `updated_by`.

### Analytics & mạng xã hội

| Cha (1) | Con (N) | Cột nối | Null | Diễn giải |
|---|---|---|---|---|
| `users` | `analytics_saved_views` | `user_id` | NOT NULL | Báo cáo lưu riêng theo người tạo |
| `tournaments` | `facebook_posts` | `tournament_id` | NULL | Bài đăng gắn với giải |

---

### Tham chiếu "người thao tác" về `users`

Nhóm quan hệ này chỉ ghi nhận **ai đã thực hiện hành động**, không mang ý nghĩa nghiệp vụ về cấu trúc dữ liệu. Khi vẽ ERD nên tách riêng hoặc lược bỏ, nếu không toàn bộ sơ đồ sẽ bị `users` kéo thành mạng nhện.

| Bảng | Cột | Null | Vai trò |
|---|---|---|---|
| `tournaments` | `created_by` | NOT NULL | Người tạo giải |
| `registration_form_templates` | `created_by` | NOT NULL | |
| `news_posts` | `created_by` | NOT NULL | Tác giả bài viết |
| `match_score_events` | `created_by` | NOT NULL | Người bấm điểm |
| `registrations` | `approved_by` | NULL | Người duyệt đơn |
| `tournament_results` | `recorded_by` | NULL | Người chốt kết quả |
| `tournament_status_histories` | `changed_by` | NULL | Null = job hệ thống tự đổi |
| `branch_managers` | `assigned_by` | NULL | Owner thực hiện gán |
| `facebook_posts` | `posted_by` | NULL | |
| `mail_layout_settings` | `updated_by` | NULL | |
| `email_templates` | `created_by` | NULL | |
| `email_automation_rules` | `created_by` | NULL | |
| `email_send_logs` | `created_by` | NULL | |

---

### Tổng hợp quan hệ đặc biệt

| Loại | Vị trí |
|---|---|
| **1-1 (khóa chính dùng chung)** | `users` ↔ `user_profiles` · `tournaments` ↔ `tournament_configs` |
| **Tự tham chiếu** | `users.owner_id` · `matches.next_match_win_id`, `matches.next_match_lose_id` |
| **N-N có thuộc tính** | `branch_managers` · `format_config_fields` · `registration_form_template_fields` · `tournament_config_values` · `registration_field_values` |
| **N-N thuần** | `news_post_tags` |
| **Nhiều cột cùng trỏ một bảng** | `matches` → `participants` (4 cột) · `branch_managers` → `users` (2 cột) |
| **Khóa chính ghép** | `tournament_config_values` · `registration_field_values` · `news_post_tags` |

---

## Constraints

| Bảng | Constraint |
|---|---|
| `roles` | UNIQUE(`code`) |
| `users` | UNIQUE(`email`) |
| `device_tokens` | UNIQUE(`expo_token`) |
| `branch_managers` | UNIQUE(`branch_id`, `manager_id`) |
| `tournament_format_definitions` | UNIQUE(`handler_key`) |
| `format_config_fields` | UNIQUE(`format_code`, `field_key`) |
| `format_race_to_rules` | UNIQUE(`format_code`, `round_key`) |
| `registration_form_templates` | UNIQUE(`code`) |
| `registration_form_template_fields` | UNIQUE(`template_id`, `field_key`) |
| `tournament_config_values` | PK(`tournament_id`, `field_key`) |
| `tournament_race_to_rules` | UNIQUE(`tournament_id`, `round_key`) |
| `tournament_stages` | UNIQUE(`tournament_id`, `order_no`) |
| `registrations` | UNIQUE(`tournament_id`, `user_id`) |
| `registration_field_values` | PK(`registration_id`, `field_key`) |
| `participant_members` | UNIQUE(`participant_id`, `user_id`) |
| `tournament_results` | UNIQUE(`tournament_id`, `participant_id`) · UNIQUE(`tournament_id`, `final_rank`) |
| `player_yearly_summaries` | UNIQUE(`user_id`, `year`) |
| `news_categories` | UNIQUE(`slug`) |
| `news_posts` | UNIQUE(`slug`) |
| `news_tags` | UNIQUE(`slug`) |
| `news_post_tags` | PK(`post_id`, `tag_id`) |
| `email_templates` | UNIQUE(`code`) |
| `email_automation_rules` | UNIQUE(`code`) |

**Optimistic locking (`@Version`):** `tournaments`, `tournament_configs`, `matches`.

**Index bổ sung:**

| Bảng | Index |
|---|---|
| `tournaments` | `idx_tournaments_public_list` (`is_show_tournament`, `status`, `created_at`) |
| `tournaments` | `idx_tournaments_owner_list` (`created_by`, `created_at`) |
| `participants` | `idx_participants_tournament_status` (`tournament_id`, `status`) |
| `device_tokens` | `idx_device_tokens_user` (`user_id`) |

---

## Luồng trạng thái

### Tournament

Map chuyển trạng thái thủ công (`STATUS_TRANSITIONS` trong `OwnerTournamentServiceImpl`):

| From | Allowed To |
|---|---|
| `DRAFT` | `OPEN_FOR_REGISTRATION`, `CANCELLED` |
| `OPEN_FOR_REGISTRATION` | `REGISTRATION_CLOSED`, `CANCELLED` |
| `REGISTRATION_CLOSED` | `CANCELLED` |
| `DRAW_DONE` | `IN_PROGRESS`, `CANCELLED` |
| `FINAL_BRACKET_READY` | `COMPLETED`, `CANCELLED` |
| `IN_PROGRESS` | `COMPLETED`, `CANCELLED` |
| `COMPLETED` | *(terminal)* |
| `CANCELLED` | *(terminal)* |

```
DRAFT → OPEN_FOR_REGISTRATION → REGISTRATION_CLOSED
                                        │
                            (bốc thăm — BracketGenerationService)
                                        ↓
                          DRAW_PREVIEW → DRAW_DONE → IN_PROGRESS → COMPLETED
                                                   FINAL_BRACKET_READY ↗
Mọi trạng thái không terminal → CANCELLED
```

**Lưu ý:**
- `DRAW_PREVIEW` và `DRAW_DONE` **không** nằm trong map chuyển thủ công — do `BracketGenerationService` (`generate` / `confirmDraw`) đặt. Vì vậy `REGISTRATION_CLOSED` chỉ chuyển tay được sang `CANCELLED`.
- Chuyển sang `OPEN_FOR_REGISTRATION` bị chặn nếu config chưa đủ (`CONFIG_INCOMPLETE`) hoặc bật `is_register` mà template form không hợp lệ.
- Transition sai → `ErrorCode.INVALID_STATUS_TRANSITION` (`FORMAT_010`).

### Registration
```
PENDING_PAYMENT → PAID → APPROVED → (tạo participant)
                       → REJECTED
                → CANCELLED
```

### Payment
```
PENDING → SUCCESS
        → FAILED
        → CANCELLED
```

### Match
```
PENDING → IN_PROGRESS → COMPLETED
        → BYE                       (không đủ người — tự động qua vòng)
        → WALKOVER                  (đối thủ bỏ cuộc)
```
Resolved = `COMPLETED` | `BYE` | `WALKOVER`.

### Tournament Stage
```
PENDING → IN_PROGRESS → COMPLETED
```

---

## Phụ lục — Khác biệt so với bản thiết kế cũ

Bản trước của tài liệu này mô tả 25 bảng và đã lệch khá xa so với code. Tóm tắt thay đổi:

**15 bảng mới chưa từng được ghi nhận**

`device_tokens`, `branches`, `branch_managers`, `billiard_tables`, `registration_form_templates`, `registration_field_definitions`, `registration_form_template_fields`, `registration_field_values`, `tournament_status_histories`, `email_templates`, `email_automation_rules`, `email_send_logs`, `mail_layout_settings`, `analytics_saved_views`, `facebook_posts`.

**Enum bị thu hẹp**

| Enum | Bản cũ (doc) | Thực tế trong code |
|---|---|---|
| `TournamentFormat` | SE, DE, ROUND_ROBIN, GROUP_PLAYOFF, PROGRESSIVE_ELIMINATION | **SE, DE, PROGRESSIVE_ROUND_ROBIN** |
| `SeedingMethod` | RANDOM, MANUAL, ELO | **RANDOM, RANK** |
| `ParticipantType` | SINGLE, DOUBLE, TEAM | **SINGLE, DOUBLE** |
| `ParticipantStatus` | ACTIVE, ELIMINATED, WITHDRAWN | **ACTIVE, INACTIVE, WITHDRAWN** |
| `ParticipantMemberRole` | SINGLE, CAPTAIN, MEMBER | **CAPTAIN, PARTNER** |
| `RegistrationStatus` | … PENDING_REVIEW … | bỏ **PENDING_REVIEW** |
| `PaymentStatus` | PENDING, SUCCESS, FAILED, REFUNDED | PENDING, SUCCESS, FAILED, **CANCELLED** |
| `MatchStatus` | PENDING, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, WALKOVER | PENDING, IN_PROGRESS, COMPLETED, **BYE**, WALKOVER |
| `field_scope` | COMMON, KNOCKOUT, GROUP, DOUBLE_ELIM, PLAYOFF | COMMON, KNOCKOUT, **PROGRESSIVE** |
| `stage_type` | … GROUP, PLAYOFF … | bỏ GROUP/PLAYOFF, thêm **FINAL_BRACKET** |

**Cột bị bỏ**

- `user_profiles.elo` → thay bằng `billiard_rank`
- `participants.seed_no` → gỡ cùng lúc với `SeedingMethod.MANUAL`

**Cột mới đáng chú ý**

- `tournaments`: `table_count`, `is_show_tournament`, `is_public_ratio`, `is_register`, `registration_form_template_id`, `branch_id`, `venue_name`, `venue_address`, `version`
- `matches`: `assigned_staff_id`, `table_id`, `table_no`, `estimated_end_at`, `schedule_locked`, `version`
- `participants`: `billiard_rank`, `avtar_url`
- `users`: `owner_id`, `manage_all_branches`, `branch_id`
- `payments`: `checkout_url`
- `news_posts`: `deleted` (soft delete)

**Sai chi tiết đã sửa**

- `win_slot` / `lose_slot` lưu **chữ thường** `player1` / `player2`, không phải `PLAYER1` / `PLAYER2`
- Optimistic locking `@Version` có trên `tournaments`, `tournament_configs`, `matches`
- `registrations.user_id` **nullable** (đăng ký `MANUAL` / walk-in)
- `payments.payment_method` thực tế chỉ ghi `PAYOS`

**Vấn đề còn tồn đọng trong code**

- `participants.avtar_url` sai chính tả (đúng phải là `avatar_url`)
- `FINAL_BRACKET` được ghi thẳng dạng chuỗi vào `tournament_stages.stage_type` nhưng thiếu trong enum `TournamentStageType`
