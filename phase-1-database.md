# Phase 1 Database — Billiard Club Tournament System

## Danh sách entities

| # | Entity | Module | Vai trò |
|---|---|---|---|
| 1 | `roles` | Auth | Danh sách role trong hệ thống |
| 2 | `users` | Auth | Tài khoản đăng nhập: Admin, Staff, Player |
| 3 | `user_profiles` | Auth | Hồ sơ cá nhân, ELO, avatar |
| 4 | `tournament_format_definitions` | Config | Định nghĩa thể thức thi đấu — metadata & handler |
| 5 | `config_field_definitions` | Config | Catalog toàn bộ tham số cấu hình có thể có (metadata + kiểu) |
| 6 | `format_config_fields` | Config | Map field ↔ format + giá trị default (thay `default_config_json`) |
| 7 | `format_race_to_rules` | Config | Race-to theo vòng mặc định của từng format |
| 8 | `game_type_definitions` | Config | Định nghĩa loại bi — admin quản lý |
| 9 | `tournaments` | Tournament | Giải đấu |
| 10 | `tournament_configs` | Tournament | Header cấu hình giải (format, seeding) |
| 11 | `tournament_config_values` | Tournament | Giá trị config thực tế theo từng field của giải |
| 12 | `tournament_race_to_rules` | Tournament | Race-to theo vòng override cho giải cụ thể |
| 13 | `registrations` | Registration | Đơn đăng ký của player |
| 14 | `payments` | Payment | Giao dịch thanh toán lệ phí |
| 15 | `participants` | Match | Đối tượng chính thức tham gia thi đấu (đơn, đôi, đội) |
| 16 | `participant_members` | Match | Thành viên của participant (đấu đôi/đội) |
| 17 | `tournament_stages` | Match | Giai đoạn thi đấu trong giải |
| 18 | `matches` | Match | Trận đấu |
| 19 | `match_score_events` | Match | Lịch sử cập nhật tỷ số |
| 20 | `tournament_results` | Ranking | Xếp hạng chung cuộc và tiền thưởng của participant theo từng giải |
| 21 | `player_yearly_summaries` | Ranking | Tổng hợp tiền thưởng và thành tích theo năm của từng player |
| 22 | `news_categories` | News | Chuyên mục tin tức |
| 23 | `news_posts` | News | Bài viết tin tức |
| 24 | `news_tags` | News | Tag bài viết |
| 25 | `news_post_tags` | News | Bảng trung gian bài viết - tag |

---

## Chi tiết fields

### `roles`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `code` | VARCHAR(50) UNIQUE NOT NULL | `ADMIN` / `STAFF` / `PLAYER` |
| `name` | VARCHAR(100) NOT NULL | Tên hiển thị |
| `description` | TEXT NULL | Mô tả quyền hạn |
| `is_active` | BOOLEAN DEFAULT true | |
| `created_at` | DATETIME NOT NULL | |

---

### `users`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `email` | VARCHAR(255) UNIQUE NOT NULL | Đăng nhập |
| `phone` | VARCHAR(20) NULL | |
| `password_hash` | VARCHAR(500) NOT NULL | |
| `role_id` | BIGINT FK→roles NOT NULL | |
| `status` | VARCHAR(30) NOT NULL | `ACTIVE` / `LOCKED` / `DELETED` |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

---

### `user_profiles`
| Field | Type | Ghi chú |
|---|---|---|
| `user_id` | BIGINT PK FK→users | |
| `full_name` | VARCHAR(255) NOT NULL | |
| `display_name` | VARCHAR(255) NULL | Tên hiển thị trên bracket |
| `avatar_url` | VARCHAR(1000) NULL | |
| `date_of_birth` | DATE NULL | |
| `gender` | VARCHAR(20) NULL | |
| `elo` | INT DEFAULT 1000 | Điểm xếp hạng nội bộ (global) |
| `bio` | TEXT NULL | |

---

### `tournament_format_definitions`
Metadata thể thức. **Default config không còn lưu JSON** — chuyển sang `format_config_fields` + `format_race_to_rules`.

| Field | Type | Ghi chú |
|---|---|---|
| `code` | VARCHAR(50) PK | `SINGLE_ELIMINATION` / `DOUBLE_ELIMINATION` / `ROUND_ROBIN` / `GROUP_PLAYOFF` / `PROGRESSIVE_ELIMINATION` |
| `name` | VARCHAR(255) NOT NULL | Tên hiển thị cho chủ quán |
| `description` | TEXT NULL | Mô tả thể thức |
| `handler_key` | VARCHAR(100) UNIQUE NOT NULL | Key backend map sang class xử lý (`pool_single_elimination_handler`, …) |
| `schema_version` | VARCHAR(20) DEFAULT '1.0' | Phiên bản validate logic trong code |
| `is_active` | BOOLEAN DEFAULT true | Admin bật/tắt |
| `sort_order` | INT DEFAULT 0 | Thứ tự hiển thị trong dropdown |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

---

### `config_field_definitions`
**Catalog master** — Developer seed một lần. Mô tả mọi `field_key` có thể dùng trong hệ thống. Admin **không** tự tạo field mới tùy ý (tránh phình schema).

| Field | Type | Ghi chú |
|---|---|---|
| `field_key` | VARCHAR(80) PK | `bracket_size`, `break_rule`, `group_count`, … |
| `label` | VARCHAR(255) NOT NULL | Nhãn hiển thị tiếng Việt trên form |
| `description` | TEXT NULL | Gợi ý cho Admin/Owner |
| `data_type` | VARCHAR(20) NOT NULL | `INT` / `BOOLEAN` / `ENUM` / `STRING` |
| `field_scope` | VARCHAR(30) NOT NULL | `COMMON` / `KNOCKOUT` / `GROUP` / `DOUBLE_ELIM` / `PLAYOFF` |
| `enum_options` | JSON NULL | Với `ENUM`: `["ALTERNATE_BREAK","WINNER_BREAK"]` |
| `ui_component` | VARCHAR(30) NOT NULL | `NUMBER` / `SELECT` / `CHECKBOX` / `TEXT` |
| `min_value` | INT NULL | Giới hạn dưới (INT) |
| `max_value` | INT NULL | Giới hạn trên (INT) |
| `is_active` | BOOLEAN DEFAULT true | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

**`field_scope` — nhóm field cho code handler chung:**

| Scope | Handler / validate chung | Ví dụ field |
|-------|--------------------------|-------------|
| `COMMON` | `BasePoolFormatHandler` | `break_rule`, `lag_for_break`, `scoring_unit` |
| `KNOCKOUT` | Single elim, playoff | `bracket_size`, `third_place_match`, `allow_bye` |
| `DOUBLE_ELIM` | Double elim | `grand_final_bracket_reset` |
| `GROUP` | Vòng bảng | `group_count`, `group_race_to`, `group_points_win` |
| `PLAYOFF` | Knockout sau bảng | `playoff_bracket_size`, `playoff_bye_top_seeds` |

---

### `format_config_fields`
**Default config theo thể thức** — thay cho `default_config_json`. Admin mở từng format → API trả các dòng này → FE render đúng cột.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `format_code` | VARCHAR(50) FK→tournament_format_definitions | |
| `field_key` | VARCHAR(80) FK→config_field_definitions | |
| `default_value` | VARCHAR(500) NOT NULL | Lưu dạng text, parse theo `data_type` của field |
| `is_required` | BOOLEAN DEFAULT true | Bắt buộc khi tạo giải |
| `is_visible_to_owner` | BOOLEAN DEFAULT true | `false` = chỉ Admin sửa default, Owner không thấy |
| `sort_order` | INT DEFAULT 0 | Thứ tự trên form |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`format_code`, `field_key`)

**Luồng Admin cấu hình default format:**

```
GET /admin/formats/SINGLE_ELIMINATION/config-fields
  → JOIN format_config_fields + config_field_definitions
  → FE render: bracket_size (NUMBER), third_place_match (CHECKBOX), break_rule (SELECT), ...
PUT /admin/formats/SINGLE_ELIMINATION/config-fields
  → Cập nhật default_value từng dòng (không đụng field không thuộc format)
```

---

### `format_race_to_rules`
Race-to theo vòng — tách riêng vì là **cấu trúc 1-N**, không gom vào 1 cột JSON.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `format_code` | VARCHAR(50) FK→tournament_format_definitions | |
| `round_key` | VARCHAR(50) NOT NULL | `round_1`, `quarter_final`, `final`, `winners_r1`, `losers_r1`, `grand_final`, `group_default`, `playoff_qf` |
| `bracket_phase` | VARCHAR(30) NOT NULL | `KNOCKOUT` / `WINNERS` / `LOSERS` / `GRAND_FINAL` / `GROUP` / `PLAYOFF` |
| `race_to` | INT NOT NULL | Số game cần thắng để thắng trận |
| `sort_order` | INT DEFAULT 0 | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`format_code`, `round_key`)

Backend map `matches.round_no` + `stage_type` + `bracket_type` → tra `round_key` → gán `matches.race_to` khi gen bracket.

---

## Kiến trúc cấu hình (3 tầng)

```text
[Tầng 1] config_field_definitions      ← catalog field (Developer seed)
[Tầng 2] format_config_fields          ← format nào dùng field nào + default
         format_race_to_rules          ← race-to mặc định theo vòng / format
[Tầng 3] tournament_config_values      ← Owner/Admin điền khi tạo giải
         tournament_race_to_rules       ← override race-to cho giải cụ thể
         tournament_configs             ← header: format_code, seeding_method
```

| Ai thao tác | Bảng | Việc làm |
|-------------|------|----------|
| Developer | `config_field_definitions` | Thêm field mới khi release |
| Admin | `format_config_fields`, `format_race_to_rules` | Sửa default từng thể thức; form động theo format |
| Admin / Owner | `tournament_config_values`, `tournament_race_to_rules` | Copy default → chỉnh cho giải |
| System | `matches.race_to` | Sinh từ `tournament_race_to_rules` (fallback `format_race_to_rules`) |

**Code backend:**

```text
BasePoolFormatHandler
  validateCommon()      ← field_scope = COMMON
  resolveRaceTo()       ← format_race_to_rules / tournament_race_to_rules

SingleEliminationHandler extends Base
DoubleEliminationHandler extends Base
GroupPlayoffHandler extends Base
```

Chi tiết ví dụ record: [`billiards-tournament-formats-guide.md`](./billiards-tournament-formats-guide.md) — Phụ lục D.

---

### `game_type_definitions`
Admin quản lý danh sách loại bi. Chủ quán chỉ thấy loại đang `is_active = true`.

| Field | Type | Ghi chú |
|---|---|---|
| `code` | VARCHAR(50) PK | `9_BALL` / `8_BALL` / `10_BALL` / `CAROM_3C` / `SNOOKER`... |
| `name` | VARCHAR(255) NOT NULL | Tên hiển thị |
| `description` | TEXT NULL | |
| `default_race_to` | INT NULL | Race-to mặc định cho loại bi này |
| `compatible_table_types` | JSON NULL | `["POOL"]` / `["CAROM"]` / `["SNOOKER"]` |
| `is_active` | BOOLEAN DEFAULT true | |
| `sort_order` | INT DEFAULT 0 | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

---

### `tournaments`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT NULL | |
| `thumbnail_url` | VARCHAR(1000) NULL | Ảnh đại diện giải |
| `banner_url` | VARCHAR(1000) NULL | Ảnh banner giải |
| `game_type` | VARCHAR(50) NOT NULL | FK→`game_type_definitions.code` |
| `participant_type` | VARCHAR(30) NOT NULL | `SINGLE` / `DOUBLE` / `TEAM` |
| `format` | VARCHAR(50) NOT NULL | FK→`tournament_format_definitions.code` |
| `status` | VARCHAR(50) NOT NULL | `DRAFT` / `OPEN_FOR_REGISTRATION` / `REGISTRATION_CLOSED` / `DRAW_DONE` / `IN_PROGRESS` / `COMPLETED` / `CANCELLED` |
| `max_participants` | INT NOT NULL | |
| `entry_fee` | DECIMAL DEFAULT 0 | |
| `prize_pool` | DECIMAL NULL | Tổng giải thưởng |
| `prize_description` | TEXT NULL | Mô tả cơ cấu giải thưởng |
| `registration_deadline` | DATETIME NULL | Deadline đăng ký |
| `start_at` | DATETIME NULL | |
| `end_at` | DATETIME NULL | |
| `created_by` | BIGINT FK→users | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

---

### `tournament_configs`
Header cấu hình giải. **Giá trị từng tham số** nằm ở `tournament_config_values`.

| Field | Type | Ghi chú |
|---|---|---|
| `tournament_id` | BIGINT PK FK→tournaments | |
| `format_code` | VARCHAR(50) FK→tournament_format_definitions.code | |
| `seeding_method` | VARCHAR(30) NOT NULL | `RANDOM` / `MANUAL` / `ELO` |
| `config_snapshot_json` | JSON NULL | *(Tùy chọn)* Cache gom key→value khi lưu giải, handler đọc nhanh. Nguồn đúng vẫn là `tournament_config_values` |
| `updated_at` | DATETIME NOT NULL | |

**Luồng tạo config giải:**

```
1. Owner chọn format → GET config-fields (copy từ format_config_fields)
2. Owner điền form → INSERT tournament_config_values (+ tournament_race_to_rules)
3. (Optional) Backend build config_snapshot_json
4. Bốc thăm → handler đọc values + race_to_rules → gen matches
```

---

### `tournament_config_values`
Giá trị config **theo từng giải**, cấu trúc giống `format_config_fields`.

| Field | Type | Ghi chú |
|---|---|---|
| `tournament_id` | BIGINT FK→tournaments | |
| `field_key` | VARCHAR(80) FK→config_field_definitions | |
| `value` | VARCHAR(500) NOT NULL | Parse theo `data_type` |
| `updated_at` | DATETIME NOT NULL | |

> PK composite (`tournament_id`, `field_key`)

---

### `tournament_race_to_rules`
Override race-to theo vòng cho **giải cụ thể**. Nếu không có dòng nào → fallback `format_race_to_rules` của `format_code`.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments | |
| `round_key` | VARCHAR(50) NOT NULL | Cùng quy ước `format_race_to_rules` |
| `bracket_phase` | VARCHAR(30) NOT NULL | |
| `race_to` | INT NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`tournament_id`, `round_key`)

---

### `registrations`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments | |
| `user_id` | BIGINT FK→users | |
| `registration_type` | VARCHAR(30) NOT NULL | `SINGLE` / `DOUBLE` / `TEAM` |
| `player_full_name` | VARCHAR(255) NOT NULL | Snapshot họ tên lúc đăng ký |
| `player_phone` | VARCHAR(20) NOT NULL | Snapshot SĐT lúc đăng ký |
| `status` | VARCHAR(50) NOT NULL | `PENDING_PAYMENT` / `PAID` / `PENDING_REVIEW` / `APPROVED` / `REJECTED` / `CANCELLED` |
| `note` | TEXT NULL | Ghi chú của player |
| `rejected_reason` | TEXT NULL | Lý do từ chối |
| `approved_by` | BIGINT FK→users NULL | Admin/Staff duyệt |
| `approved_at` | DATETIME NULL | |
| `rejected_at` | DATETIME NULL | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

> UNIQUE(`tournament_id`, `user_id`)

---

### `payments`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK→users | |
| `registration_id` | BIGINT FK→registrations NULL | |
| `amount` | DECIMAL NOT NULL | |
| `payment_method` | VARCHAR(30) NOT NULL | `VNPAY` / `MOMO` / `BANK_TRANSFER` / `CASH` |
| `status` | VARCHAR(30) NOT NULL | `PENDING` / `SUCCESS` / `FAILED` / `REFUNDED` |
| `transaction_code` | VARCHAR(255) NULL | Mã từ cổng thanh toán |
| `paid_at` | DATETIME NULL | |
| `created_at` | DATETIME NOT NULL | |

---

### `participants`
Đại diện cho một đơn vị thi đấu — đơn, đôi, hoặc đội. Mọi logic `matches` chỉ làm việc với `participant`.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments | |
| `registration_id` | BIGINT FK→registrations NULL | |
| `participant_type` | VARCHAR(30) NOT NULL | `SINGLE` / `DOUBLE` / `TEAM` |
| `display_name` | VARCHAR(255) NOT NULL | Tên hiển thị trên bracket / bảng xếp hạng |
| `seed_no` | INT NULL | Số hạt giống sau bốc thăm |
| `status` | VARCHAR(30) NOT NULL | `ACTIVE` / `ELIMINATED` / `WITHDRAWN` |

---

### `participant_members`
Thành viên của participant. Đấu đơn: 1 dòng. Đấu đôi: 2 dòng. Đấu đội: n dòng.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `participant_id` | BIGINT FK→participants | |
| `user_id` | BIGINT FK→users NULL | Nếu thành viên có tài khoản |
| `full_name` | VARCHAR(255) NOT NULL | Snapshot họ tên |
| `phone` | VARCHAR(20) NULL | Snapshot SĐT |
| `role` | VARCHAR(30) NOT NULL | `SINGLE` / `CAPTAIN` / `MEMBER` |

> UNIQUE(`participant_id`, `user_id`)

---

### `tournament_stages`
Mỗi giải sinh 1 hoặc nhiều stages tùy thể thức. Stage có lifecycle riêng.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments | |
| `name` | VARCHAR(255) NOT NULL | |
| `stage_type` | VARCHAR(50) NOT NULL | `KNOCKOUT` / `WINNERS_BRACKET` / `LOSERS_BRACKET` / `GRAND_FINAL` / `GROUP` / `PLAYOFF` / `PROGRESSIVE_ROUND` / `PROGRESSIVE_PLAYOFF` |
| `order_no` | INT NOT NULL | Thứ tự trong giải |
| `status` | VARCHAR(30) NOT NULL | `PENDING` / `IN_PROGRESS` / `COMPLETED` |
| `pe_round_no` | INT NULL | [Progressive] Vòng thứ mấy |
| `pe_active_count` | INT NULL | [Progressive] Số người còn lại đầu vòng |
| `pe_eliminate_count` | INT NULL | [Progressive] Số người bị loại sau vòng |

> UNIQUE(`tournament_id`, `order_no`)

**Số stages theo format:**

| Format | Stages |
|---|---|
| `SINGLE_ELIMINATION` | 1: `KNOCKOUT` |
| `DOUBLE_ELIMINATION` | 3: `WINNERS_BRACKET` + `LOSERS_BRACKET` + `GRAND_FINAL` |
| `ROUND_ROBIN` | 1: `GROUP` |
| `GROUP_PLAYOFF` | 2: `GROUP` + `PLAYOFF` |
| `PROGRESSIVE_ELIMINATION` | n × `PROGRESSIVE_ROUND` + 1 × `PROGRESSIVE_PLAYOFF` |

---

### `matches`
Toàn bộ trận đấu — gen sẵn từ đầu, có lịch cụ thể. Vòng sau để player null, tự động điền khi có kết quả.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments | |
| `stage_id` | BIGINT FK→tournament_stages | |
| `bracket_type` | VARCHAR(30) NOT NULL | `WINNERS` / `LOSERS` / `GRAND_FINAL` / `GROUP` / `PROGRESSIVE` / `PLAYOFF` |
| `round_no` | INT NOT NULL | Vòng số mấy trong stage |
| `position_no` | INT NOT NULL | Vị trí trong vòng |
| `match_code` | VARCHAR(50) NULL | R1-M1, QF1, SF1, FINAL... |
| `player1_participant_id` | BIGINT FK→participants NULL | |
| `player2_participant_id` | BIGINT FK→participants NULL | |
| `player1_score` | INT DEFAULT 0 | |
| `player2_score` | INT DEFAULT 0 | |
| `winner_participant_id` | BIGINT FK→participants NULL | |
| `loser_participant_id` | BIGINT FK→participants NULL | |
| `next_match_win_id` | BIGINT FK→matches NULL | Trận tiếp người thắng |
| `next_match_lose_id` | BIGINT FK→matches NULL | Trận tiếp người thua (Double Elim) |
| `win_slot` | VARCHAR(20) NULL | `PLAYER1` / `PLAYER2` |
| `lose_slot` | VARCHAR(20) NULL | `PLAYER1` / `PLAYER2` |
| `scheduled_at` | DATETIME NULL | Lịch thi đấu — gen sẵn từ đầu |
| `race_to` | INT NOT NULL | |
| `status` | VARCHAR(30) NOT NULL | `PENDING` / `SCHEDULED` / `IN_PROGRESS` / `COMPLETED` / `CANCELLED` / `WALKOVER` |
| `is_bye` | BOOLEAN DEFAULT false | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

**Quy tắc `next_match_win_id` / `next_match_lose_id`:**

| bracket_type | next_match_win_id | next_match_lose_id |
|---|---|---|
| `WINNERS` | ✅ | ✅ → nhánh thua |
| `LOSERS` | ✅ | ❌ NULL |
| `PLAYOFF` / `KNOCKOUT` | ✅ | ❌ NULL |
| `GROUP` / `PROGRESSIVE` | ❌ NULL | ❌ NULL |
| `GRAND_FINAL` | ❌ NULL | ❌ NULL |

---

### `match_score_events`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `match_id` | BIGINT FK→matches | |
| `scored_by_participant_id` | BIGINT FK→participants NULL | |
| `player1_score_after` | INT NOT NULL | |
| `player2_score_after` | INT NOT NULL | |
| `event_type` | VARCHAR(30) NOT NULL | `SCORE_ADD` / `UNDO` / `MATCH_END` / `WALKOVER` |
| `created_by` | BIGINT FK→users | |
| `created_at` | DATETIME NOT NULL | |

---

### `tournament_results`
Lưu xếp hạng cuối cùng của participant ở từng giải và số tiền thưởng nhận được.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `tournament_id` | BIGINT FK→tournaments | |
| `participant_id` | BIGINT FK→participants | |
| `final_rank` | INT NOT NULL | Hạng chung cuộc: 1, 2, 3... |
| `prize_amount` | DECIMAL DEFAULT 0 | Số tiền thưởng participant nhận ở giải này |
| `points_earned` | INT DEFAULT 0 | Điểm tích lũy ranking nếu áp dụng |
| `note` | TEXT NULL | Ghi chú thêm về kết quả |
| `recorded_at` | DATETIME NOT NULL | Thời điểm chốt kết quả |
| `recorded_by` | BIGINT FK→users NULL | Người xác nhận kết quả |

> UNIQUE(`tournament_id`, `participant_id`)
>
> UNIQUE(`tournament_id`, `final_rank`)

---

### `player_yearly_summaries`
Bảng tổng hợp theo năm cho mỗi player để tra cứu nhanh thành tích và tiền thưởng tích lũy.

| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `user_id` | BIGINT FK→users | Chỉ áp dụng cho user role Player |
| `year` | INT NOT NULL | Ví dụ 2026 |
| `tournaments_played` | INT DEFAULT 0 | Số giải đã tham gia trong năm |
| `champion_count` | INT DEFAULT 0 | Số lần vô địch (hạng 1) |
| `runner_up_count` | INT DEFAULT 0 | Số lần hạng 2 |
| `top3_count` | INT DEFAULT 0 | Số lần top 3 |
| `total_prize_amount` | DECIMAL DEFAULT 0 | Tổng tiền thưởng tích lũy trong năm |
| `total_points` | INT DEFAULT 0 | Tổng điểm ranking tích lũy |
| `updated_at` | DATETIME NOT NULL | Lần cập nhật gần nhất |

> UNIQUE(`user_id`, `year`)

---

### `news_categories`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(255) NOT NULL | |
| `slug` | VARCHAR(255) UNIQUE NOT NULL | |
| `status` | VARCHAR(30) NOT NULL | `ACTIVE` / `INACTIVE` |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

---

### `news_posts`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `category_id` | BIGINT FK→news_categories | |
| `title` | VARCHAR(255) NOT NULL | |
| `slug` | VARCHAR(255) UNIQUE NOT NULL | |
| `thumbnail_url` | VARCHAR(1000) NULL | |
| `content` | TEXT NOT NULL | |
| `status` | VARCHAR(30) NOT NULL | `DRAFT` / `PUBLISHED` / `HIDDEN` |
| `published_at` | DATETIME NULL | |
| `created_by` | BIGINT FK→users | |
| `created_at` | DATETIME NOT NULL | |
| `updated_at` | DATETIME NOT NULL | |

---

### `news_tags`
| Field | Type | Ghi chú |
|---|---|---|
| `id` | BIGINT PK | |
| `name` | VARCHAR(100) NOT NULL | |
| `slug` | VARCHAR(100) UNIQUE NOT NULL | |

---

### `news_post_tags`
| Field | Type | Ghi chú |
|---|---|---|
| `post_id` | BIGINT FK→news_posts | |
| `tag_id` | BIGINT FK→news_tags | |

> PK composite (`post_id`, `tag_id`)

---

## Sơ đồ quan hệ

```
roles ──────────────────────── users                        (1-N)

users ──────────────────────── user_profiles                (1-1)
users ──────────────────────── tournaments                  (1-N, created_by)
users ──────────────────────── registrations                (1-N)
users ──────────────────────── payments                     (1-N)
users ──────────────────────── participant_members          (1-N)
users ──────────────────────── match_score_events           (1-N, created_by)
users ──────────────────────── tournament_results           (1-N, recorded_by)
users ──────────────────────── player_yearly_summaries      (1-N)
users ──────────────────────── news_posts                   (1-N, created_by)

config_field_definitions ───── format_config_fields          (1-N, qua field_key)
config_field_definitions ───── tournament_config_values     (1-N, qua field_key)

tournament_format_definitions ─ format_config_fields          (1-N)
tournament_format_definitions ─ format_race_to_rules          (1-N)
tournament_format_definitions ─ tournaments                 (1-N, qua format)
tournament_format_definitions ─ tournament_configs            (1-N, qua format_code)

game_type_definitions ───────── tournaments                 (1-N, qua game_type)

tournaments ────────────────── tournament_configs             (1-1)
tournaments ────────────────── tournament_config_values       (1-N)
tournaments ────────────────── tournament_race_to_rules       (1-N)
tournaments ────────────────── registrations                (1-N)
tournaments ────────────────── participants                 (1-N)
tournaments ────────────────── tournament_stages            (1-N)
tournaments ────────────────── matches                      (1-N)
tournaments ────────────────── tournament_results           (1-N)

registrations ──────────────── payments                     (1-N)
registrations ──────────────── participants                 (1-0..1)

participants ───────────────── participant_members          (1-N)
participants ───────────────── matches                      (N-N qua player1/player2/winner/loser)
participants ───────────────── tournament_results           (1-N)

tournament_stages ──────────── matches                      (1-N)
matches ────────────────────── match_score_events           (1-N)
matches ────────────────────── matches                      (self-ref: next_match_win_id, next_match_lose_id)

news_categories ────────────── news_posts                   (1-N)
news_posts ─────────────────── news_post_tags               (1-N)
news_tags ──────────────────── news_post_tags               (1-N)
```

---

## Constraints

| Entity | Constraint |
|---|---|
| `roles` | UNIQUE(`code`) |
| `users` | UNIQUE(`email`) |
| `registrations` | UNIQUE(`tournament_id`, `user_id`) |
| `participant_members` | UNIQUE(`participant_id`, `user_id`) |
| `tournament_stages` | UNIQUE(`tournament_id`, `order_no`) |
| `tournament_results` | UNIQUE(`tournament_id`, `participant_id`) |
| `tournament_results` | UNIQUE(`tournament_id`, `final_rank`) |
| `player_yearly_summaries` | UNIQUE(`user_id`, `year`) |
| `tournament_format_definitions` | UNIQUE(`handler_key`) |
| `format_config_fields` | UNIQUE(`format_code`, `field_key`) |
| `format_race_to_rules` | UNIQUE(`format_code`, `round_key`) |
| `tournament_config_values` | PK(`tournament_id`, `field_key`) |
| `tournament_race_to_rules` | UNIQUE(`tournament_id`, `round_key`) |
| `news_posts` | UNIQUE(`slug`) |
| `news_tags` | UNIQUE(`slug`) |
| `news_post_tags` | PK composite(`post_id`, `tag_id`) |

---

## Luồng trạng thái

**Registration:**
```
PENDING_PAYMENT → PAID → PENDING_REVIEW → APPROVED → (tạo participant)
                                        → REJECTED
                        → CANCELLED
```

**Tournament:**
```
DRAFT → OPEN_FOR_REGISTRATION → REGISTRATION_CLOSED → DRAW_DONE → IN_PROGRESS → COMPLETED
                                                                              → CANCELLED
```

**Match:**
```
PENDING → SCHEDULED → IN_PROGRESS → COMPLETED
                                  → WALKOVER
        → CANCELLED
```
