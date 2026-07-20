# Data Init — Dữ liệu sinh khi tạo Database

> Phân loại: **bắt buộc init** vs **khuyến nghị init** vs **không init** (runtime).
>
> Tham chiếu seed Single Elim: [`seed-SINGLE_ELIMINATION-db.md`](./seed-SINGLE_ELIMINATION-db.md)

---

## Tóm tắt nhanh

| Loại | Bảng | Số dòng gợi ý | Ai tạo sau nếu không init |
|------|------|---------------|---------------------------|
| **Bắt buộc** | `roles` | 3 | — |
| **Bắt buộc** | `config_field_definitions` | ~20 | Developer release mới |
| **Bắt buộc** | `game_type_definitions` | 3+ | Admin UC-13 |
| **Khuyến nghị** | `tournament_format_definitions` | 3 | Admin wizard Màn 1 |
| **Khuyến nghị** | `format_config_fields` | ~35 | Admin wizard Màn 2 |
| **Khuyến nghị** | `format_race_to_rules` | ~18 | Admin wizard Màn 3 |
| **Tùy chọn** | `users` + `user_profiles` | 1 admin | Đăng ký / tạo tay |
| **Không init** | `tournaments`, `tournament_*`, `matches`… | 0 | Owner / runtime |

---

## 1. BẮT BUỘC init (thiếu → app lỗi FK hoặc form trống)

### 1.1. `roles`

Hệ thống auth không chạy nếu thiếu.

| code | name |
|------|------|
| `ADMIN` | Quản trị viên |
| `STAFF` | Nhân viên / trọng tài |
| `PLAYER` | Cơ thủ |

**Số dòng:** 3

---

### 1.2. `config_field_definitions`

**Catalog field** — mọi `format_config_fields.field_key` và `tournament_config_values.field_key` đều FK vào đây.

Admin có thể **thêm field qua API** (`POST /api/v1/admin/config-field-catalog`) hoặc developer seed lần đầu; sau đó gán vào từng format qua wizard `PUT /admin/formats/{code}/config-fields`.

| field_key | field_scope | Ghi chú |
|-----------|-------------|---------|
| `break_rule` | COMMON | ENUM / SELECT |
| `lag_for_break` | COMMON | BOOLEAN / CHECKBOX |
| `scoring_unit` | COMMON | ENUM / SELECT |
| `bracket_size` | KNOCKOUT | Single/Double elim |
| `allow_bye` | KNOCKOUT | BOOLEAN / CHECKBOX |
| `seeding_enabled` | KNOCKOUT | BOOLEAN / CHECKBOX |
| `third_place_match` | KNOCKOUT | BOOLEAN / CHECKBOX — Single elim |
| `grand_final_bracket_reset` | DOUBLE_ELIM | BOOLEAN / CHECKBOX — Double elim |
| `group_count` | GROUP | Group playoff |
| `players_per_group` | GROUP | |
| `advance_per_group` | GROUP | |
| `group_assignment` | GROUP | |
| `group_points_win` | GROUP | |
| `group_points_loss` | GROUP | |
| `group_tiebreaker_order` | GROUP | |
| `playoff_bracket_size` | PLAYOFF | |
| `playoff_bye_top_seeds` | PLAYOFF | |

**Số dòng:** **17** (seed **đủ catalog** một lần, dù phase 1 chỉ dùng 2–3 format)

> `is_show_tournament`, `is_public_ratio`, `is_register` đã bỏ khỏi catalog này — giờ là cột trực tiếp trên `tournaments`, set trong bước đăng ký/tạo giải (Owner), không còn là config field động.

**Thêm field mới (không sửa `DatabaseSeedData`):**

```http
POST /api/v1/admin/config-field-catalog
{
  "fieldKey": "example_field",
  "label": "Nhãn hiển thị",
  "description": "Mô tả field",
  "dataType": "BOOLEAN",
  "fieldScope": "COMMON",
  "uiComponent": "CHECKBOX",
  "isActive": true
}
```

Sau đó gán default cho từng format: `PUT /api/v1/admin/formats/{code}/config-fields` (thêm item vào mảng `fields`).

Chi tiết JSON từng field: xem [`seed-SINGLE_ELIMINATION-db.md`](./seed-SINGLE_ELIMINATION-db.md) mục ① + bổ sung field GROUP/DOUBLE trong [`billiards-tournament-formats-guide.md`](./billiards-tournament-formats-guide.md) §3.3.

---

### 1.3. `game_type_definitions`

`tournaments.game_type` FK vào đây. Owner dropdown loại bi cần có sẵn.

| code | name | default_race_to |
|------|------|-----------------|
| `9_BALL` | 9-Ball | 7 |
| `8_BALL` | 8-Ball | 5 |
| `10_BALL` | 10-Ball | 7 |

**Số dòng:** 3 (tối thiểu phase 1)

```json
{
  "code": "9_BALL",
  "name": "9-Ball (Bida lỗ 9 bi)",
  "description": "Race-to, alternate break.",
  "default_race_to": 7,
  "compatible_table_types": ["POOL"],
  "is_active": true,
  "sort_order": 1
}
```

---

## 2. KHUYẾN NGHỊ init (capstone — Owner dùng ngay, không cần Admin wizard)

Nếu **không** init → Admin phải chạy wizard (POST format + PUT config-fields + PUT race-to-rules + activate) trước khi Owner tạo giải.

### 2.1. `tournament_format_definitions`

| code | handler_key | is_active |
|------|-------------|-----------|
| `SINGLE_ELIMINATION` | `pool_single_elimination_handler` | true |
| `DOUBLE_ELIMINATION` | `pool_double_elimination_handler` | true |
| `GROUP_PLAYOFF` | `pool_group_playoff_handler` | true |

**Số dòng:** 3

---

### 2.2. `format_config_fields`

Default field **theo từng format** (FK → `config_field_definitions` + `tournament_format_definitions`).

| format_code | Số dòng |
|-------------|---------|
| `SINGLE_ELIMINATION` | 7 |
| `DOUBLE_ELIMINATION` | 7 |
| `GROUP_PLAYOFF` | 12 |
| **Tổng** | **26** |

Chi tiết Single Elim: [`seed-SINGLE_ELIMINATION-db.md`](./seed-SINGLE_ELIMINATION-db.md) mục ③.

---

### 2.3. `format_race_to_rules`

| format_code | Số dòng |
|-------------|---------|
| `SINGLE_ELIMINATION` | 5 |
| `DOUBLE_ELIMINATION` | 8 |
| `GROUP_PLAYOFF` | ~6 |
| **Tổng** | **~19** |

Chi tiết Single Elim: [`seed-SINGLE_ELIMINATION-db.md`](./seed-SINGLE_ELIMINATION-db.md) mục ④.

---

## 3. TÙY CHỌN init

| Bảng | Mục đích | Ghi chú |
|------|----------|---------|
| `users` + `user_profiles` | 1 tài khoản Admin mặc định | Password hash; hoặc để user tự đăng ký rồi gán role |
| `news_categories` | 1–2 chuyên mục tin | Chỉ nếu module News phase 1 |

---

## 4. KHÔNG init (runtime — để trống lúc tạo DB)

| Bảng | Sinh khi nào |
|------|----------------|
| `users` / `user_profiles` | Đăng ký / Admin tạo user |
| `tournaments` | Owner `POST /owner/tournaments` |
| `tournament_configs` | Owner `PUT .../config` |
| `tournament_config_values` | Owner `PUT .../config` |
| `tournament_race_to_rules` | Owner override race-to |
| `registrations` | Player đăng ký giải |
| `payments` | Thanh toán |
| `participants` / `participant_members` | Sau duyệt đăng ký |
| `tournament_stages` / `matches` | Bốc thăm / gen bracket |
| `match_score_events` | Staff nhập điểm |
| `tournament_results` | Chốt giải |
| `player_yearly_summaries` | Aggregate cuối giải / cron |
| `news_posts` / `news_tags` | Admin đăng tin |

---

## 5. Thứ tự insert trong script Data Init

```text
1. roles
2. config_field_definitions          ← trước format_config_fields
3. game_type_definitions
4. tournament_format_definitions     ← (khuyến nghị) trước format_config_fields
5. format_config_fields
6. format_race_to_rules
7. (optional) users + user_profiles
```

**Ràng buộc FK:**

```text
format_config_fields.field_key        → config_field_definitions.field_key
format_config_fields.format_code      → tournament_format_definitions.code
format_race_to_rules.format_code      → tournament_format_definitions.code
tournaments.game_type                 → game_type_definitions.code
tournaments.format                    → tournament_format_definitions.code
users.role_id                         → roles.id
```

---

## 6. Hai chiến lược triển khai

### Chiến lược A — Init tối thiểu (catalog only)

**Init:** `roles` + `config_field_definitions` + `game_type_definitions`

**Hệ quả:** Admin **bắt buộc** wizard setup từng format trước khi Owner tạo giải.

**Tổng dòng init:** ~26

---

### Chiến lược B — Init đầy đủ (khuyên capstone) ✅

**Init:** mục 1 + mục 2 (format + default + race-to, `is_active=true`)

**Hệ quả:** Deploy xong → Owner tạo giải ngay; Admin chỉ **sửa** default nếu cần.

**Tổng dòng init:** ~26 + 3 + 35 + 19 ≈ **83 dòng**

---

## 7. Checklist copy vào project

```text
[ ] roles (3)
[ ] config_field_definitions (20)
[ ] game_type_definitions (3)
[ ] tournament_format_definitions (3)        ← optional nếu chiến lược A
[ ] format_config_fields (35)                ← optional nếu chiến lược A
[ ] format_race_to_rules (~19)               ← optional nếu chiến lược A
[ ] admin user (optional)
```

---

## 8. SINGLE_ELIMINATION — chỉ phần init

Nếu init **cả catalog + Single Elim sẵn sàng**:

| Bảng | Dòng |
|------|------|
| `config_field_definitions` | 4 field (hoặc 17 nếu seed full catalog) |
| `tournament_format_definitions` | 1 |
| `format_config_fields` | 7 |
| `format_race_to_rules` | 5 |

→ Xem payload đầy đủ: [`seed-SINGLE_ELIMINATION-db.md`](./seed-SINGLE_ELIMINATION-db.md)

---

*Tài liệu BTMS Phase 1 — Data Init.*
