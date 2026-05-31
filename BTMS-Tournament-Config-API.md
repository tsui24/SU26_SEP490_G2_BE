# BTMS — Tournament Config API (Final)

> Spec triển khai module **cấu hình thể thức + config giải**. Chỉ mô tả **path, params, body request/response**.
>
> **Luồng:** Admin setup default format → Owner tạo giải + copy default → mở đăng ký.

---

## Thứ tự gọi API

**Admin — setup 1 thể thức (vd Single Elim):**
```
POST /admin/formats
PUT  /admin/formats/{code}/config-fields
PUT  /admin/formats/{code}/race-to-rules
POST /admin/formats/{code}/activate
```

**Owner — tạo 1 giải:**
```
GET  /owner/formats
GET  /owner/game-types
POST /owner/tournaments
GET  /owner/tournaments/{id}/config-form
PUT  /owner/tournaments/{id}/config
PATCH /owner/tournaments/{id}/status
```

---

# ADMIN API

---

## GET `/admin/config-field-catalog`

Catalog field (Developer seed) — Admin chọn field gán cho thể thức.

**Query**

| Param | Type | Required | Mô tả |
|-------|------|----------|-------|
| `scope` | string | | `COMMON,KNOCKOUT,DOUBLE_ELIM,GROUP,PLAYOFF` |
| `isActive` | boolean | | default `true` |

**Response 200**

```json
{
  "items": [
    {
      "fieldKey": "bracket_size",
      "label": "Số slot bracket",
      "description": "Số slot trên nhánh đấu.",
      "dataType": "INT",
      "fieldScope": "KNOCKOUT",
      "uiComponent": "NUMBER",
      "enumOptions": null,
      "minValue": 8,
      "maxValue": 64,
      "isActive": true
    },
    {
      "fieldKey": "break_rule",
      "label": "Luật break",
      "dataType": "ENUM",
      "fieldScope": "COMMON",
      "uiComponent": "SELECT",
      "enumOptions": ["ALTERNATE_BREAK", "WINNER_BREAK", "LOSER_BREAK"],
      "minValue": null,
      "maxValue": null,
      "isActive": true
    }
  ],
  "total": 17
}
```

---

## GET `/admin/config-field-catalog/{fieldKey}`

**Path:** `fieldKey` — vd `bracket_size`

**Response 200**

```json
{
  "fieldKey": "bracket_size",
  "label": "Số slot bracket",
  "description": "Số slot trên nhánh đấu.",
  "dataType": "INT",
  "fieldScope": "KNOCKOUT",
  "uiComponent": "NUMBER",
  "enumOptions": null,
  "minValue": 8,
  "maxValue": 64,
  "isActive": true
}
```

---

## GET `/admin/formats`

Danh sách thể thức.

**Query**

| Param | Type | Mô tả |
|-------|------|-------|
| `isActive` | boolean | Lọc đang kích hoạt |
| `setupStatus` | string | `DRAFT` \| `INFO_DONE` \| `CONFIG_FIELDS_DONE` \| `RACE_TO_DONE` \| `READY_TO_ACTIVATE` \| `ACTIVE` |

**Response 200**

```json
{
  "items": [
    {
      "code": "SINGLE_ELIMINATION",
      "name": "Loại trực tiếp (1 lần thua)",
      "description": "Thua 1 trận là bị loại.",
      "handlerKey": "pool_single_elimination_handler",
      "schemaVersion": "1.0",
      "sortOrder": 1,
      "isActive": true,
      "setupStatus": "ACTIVE",
      "configFieldCount": 7,
      "raceToRuleCount": 5
    }
  ],
  "total": 1
}
```

---

## GET `/admin/formats/{code}`

**Path:** `code` — vd `SINGLE_ELIMINATION`

**Response 200**

```json
{
  "code": "SINGLE_ELIMINATION",
  "name": "Loại trực tiếp (1 lần thua)",
  "description": "Thua 1 trận là bị loại. Race-to theo vòng.",
  "handlerKey": "pool_single_elimination_handler",
  "schemaVersion": "1.0",
  "sortOrder": 1,
  "isActive": true,
  "setupStatus": "ACTIVE"
}
```

---

## POST `/admin/formats`

Wizard Màn 1 — tạo thể thức (tên, mô tả…).

**Body**

```json
{
  "code": "SINGLE_ELIMINATION",
  "name": "Loại trực tiếp (1 lần thua)",
  "description": "Thua 1 trận là bị loại. Mỗi trận race-to X game. Alternate break.",
  "handlerKey": "pool_single_elimination_handler",
  "schemaVersion": "1.0",
  "sortOrder": 1,
  "isActive": false
}
```

| Field | Required | Ghi chú |
|-------|----------|---------|
| `code` | ✅ | UPPER_SNAKE, unique |
| `name` | ✅ | |
| `description` | ✅ | |
| `handlerKey` | ✅ | |
| `schemaVersion` | | default `1.0` |
| `sortOrder` | | default `0` |
| `isActive` | | wizard: `false` đến khi activate |

**Response 201**

```json
{
  "code": "SINGLE_ELIMINATION",
  "name": "Loại trực tiếp (1 lần thua)",
  "setupStatus": "INFO_DONE",
  "nextStep": "config-fields"
}
```

---

## PUT `/admin/formats/{code}`

Sửa metadata thể thức.

**Body**

```json
{
  "name": "Loại trực tiếp (1 lần thua)",
  "description": "Mô tả cập nhật.",
  "handlerKey": "pool_single_elimination_handler",
  "schemaVersion": "1.0",
  "sortOrder": 1
}
```

**Response 200** — cùng shape `GET /admin/formats/{code}`

---

## PATCH `/admin/formats/{code}`

Bật/tắt nhanh.

**Body**

```json
{
  "isActive": false
}
```

**Response 200**

```json
{
  "code": "SINGLE_ELIMINATION",
  "isActive": false
}
```

---

## GET `/admin/formats/{code}/setup-status`

**Response 200**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "setupStatus": "ACTIVE",
  "bootstrapped": true,
  "configFieldCount": 7,
  "raceToRuleCount": 5,
  "isActive": true,
  "canActivate": false,
  "missingSteps": []
}
```

```json
{
  "formatCode": "DOUBLE_ELIMINATION",
  "setupStatus": "CONFIG_FIELDS_DONE",
  "bootstrapped": false,
  "configFieldCount": 7,
  "raceToRuleCount": 0,
  "isActive": false,
  "canActivate": false,
  "missingSteps": ["race-to-rules", "activate"]
}
```

---

## GET `/admin/formats/{code}/config-fields`

Wizard Màn 2 — load form default field.

**Response 200 (đã có default)**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "formatName": "Loại trực tiếp (1 lần thua)",
  "setupStatus": "CONFIG_FIELDS_DONE",
  "fields": [
    {
      "fieldKey": "bracket_size",
      "label": "Số slot bracket",
      "description": "Số slot trên nhánh đấu.",
      "dataType": "INT",
      "fieldScope": "KNOCKOUT",
      "uiComponent": "NUMBER",
      "enumOptions": null,
      "minValue": 8,
      "maxValue": 64,
      "defaultValue": "16",
      "isRequired": true,
      "isVisibleToOwner": true,
      "sortOrder": 1
    },
    {
      "fieldKey": "break_rule",
      "label": "Luật break",
      "dataType": "ENUM",
      "fieldScope": "COMMON",
      "uiComponent": "SELECT",
      "enumOptions": ["ALTERNATE_BREAK", "WINNER_BREAK", "LOSER_BREAK"],
      "defaultValue": "ALTERNATE_BREAK",
      "isRequired": true,
      "isVisibleToOwner": true,
      "sortOrder": 10
    }
  ],
  "availableFields": []
}
```

**Response 200 (chưa insert — `fields` rỗng, trả `availableFields` từ catalog)**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "formatName": "Loại trực tiếp (1 lần thua)",
  "setupStatus": "INFO_DONE",
  "fields": [],
  "availableFields": [
    {
      "fieldKey": "bracket_size",
      "label": "Số slot bracket",
      "dataType": "INT",
      "fieldScope": "KNOCKOUT",
      "uiComponent": "NUMBER",
      "minValue": 8,
      "maxValue": 64
    }
  ]
}
```

---

## PUT `/admin/formats/{code}/config-fields`

Wizard Màn 2 — INSERT/UPDATE default field.

**Body — SINGLE_ELIMINATION**

```json
{
  "fields": [
    { "fieldKey": "bracket_size", "defaultValue": "16", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 1 },
    { "fieldKey": "allow_bye", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 2 },
    { "fieldKey": "seeding_enabled", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 3 },
    { "fieldKey": "third_place_match", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 4 },
    { "fieldKey": "break_rule", "defaultValue": "ALTERNATE_BREAK", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 10 },
    { "fieldKey": "lag_for_break", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 11 },
    { "fieldKey": "scoring_unit", "defaultValue": "GAME", "isRequired": true, "isVisibleToOwner": false, "sortOrder": 12 }
  ]
}
```

**Body — DOUBLE_ELIMINATION**

```json
{
  "fields": [
    { "fieldKey": "bracket_size", "defaultValue": "16", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 1 },
    { "fieldKey": "allow_bye", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 2 },
    { "fieldKey": "seeding_enabled", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 3 },
    { "fieldKey": "grand_final_bracket_reset", "defaultValue": "false", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 4 },
    { "fieldKey": "break_rule", "defaultValue": "ALTERNATE_BREAK", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 10 },
    { "fieldKey": "lag_for_break", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true, "sortOrder": 11 },
    { "fieldKey": "scoring_unit", "defaultValue": "GAME", "isRequired": true, "isVisibleToOwner": false, "sortOrder": 12 }
  ]
}
```

**Response 200**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "fieldsSaved": 7,
  "setupStatus": "CONFIG_FIELDS_DONE",
  "nextStep": "race-to-rules"
}
```

---

## GET `/admin/formats/{code}/race-to-rules`

Wizard Màn 3 — load default race-to.

**Response 200**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "setupStatus": "RACE_TO_DONE",
  "rules": [
    { "id": 1, "roundKey": "round_1", "label": "Vòng 1", "bracketPhase": "KNOCKOUT", "raceTo": 5, "sortOrder": 1 },
    { "id": 2, "roundKey": "quarter_final", "label": "Tứ kết", "bracketPhase": "KNOCKOUT", "raceTo": 7, "sortOrder": 2 },
    { "id": 3, "roundKey": "semi_final", "label": "Bán kết", "bracketPhase": "KNOCKOUT", "raceTo": 7, "sortOrder": 3 },
    { "id": 4, "roundKey": "third_place", "label": "Tranh hạng 3", "bracketPhase": "KNOCKOUT", "raceTo": 7, "sortOrder": 4 },
    { "id": 5, "roundKey": "final", "label": "Chung kết", "bracketPhase": "KNOCKOUT", "raceTo": 9, "sortOrder": 5 }
  ]
}
```

---

## PUT `/admin/formats/{code}/race-to-rules`

Wizard Màn 3 — INSERT/UPDATE default race-to.

**Body — SINGLE_ELIMINATION**

```json
{
  "rules": [
    { "roundKey": "round_1", "label": "Vòng 1", "bracketPhase": "KNOCKOUT", "raceTo": 5, "sortOrder": 1 },
    { "roundKey": "quarter_final", "label": "Tứ kết", "bracketPhase": "KNOCKOUT", "raceTo": 7, "sortOrder": 2 },
    { "roundKey": "semi_final", "label": "Bán kết", "bracketPhase": "KNOCKOUT", "raceTo": 7, "sortOrder": 3 },
    { "roundKey": "third_place", "label": "Tranh hạng 3", "bracketPhase": "KNOCKOUT", "raceTo": 7, "sortOrder": 4 },
    { "roundKey": "final", "label": "Chung kết", "bracketPhase": "KNOCKOUT", "raceTo": 9, "sortOrder": 5 }
  ]
}
```

**Body — DOUBLE_ELIMINATION**

```json
{
  "rules": [
    { "roundKey": "winners_r1", "label": "NT — Vòng 1", "bracketPhase": "WINNERS", "raceTo": 5, "sortOrder": 1 },
    { "roundKey": "winners_qf", "label": "NT — Tứ kết", "bracketPhase": "WINNERS", "raceTo": 7, "sortOrder": 2 },
    { "roundKey": "winners_sf", "label": "NT — Bán kết", "bracketPhase": "WINNERS", "raceTo": 7, "sortOrder": 3 },
    { "roundKey": "losers_r1", "label": "NTh — Vòng 1", "bracketPhase": "LOSERS", "raceTo": 5, "sortOrder": 4 },
    { "roundKey": "losers_r2", "label": "NTh — Vòng 2", "bracketPhase": "LOSERS", "raceTo": 7, "sortOrder": 5 },
    { "roundKey": "losers_r3", "label": "NTh — Vòng 3", "bracketPhase": "LOSERS", "raceTo": 7, "sortOrder": 6 },
    { "roundKey": "losers_final", "label": "NTh — Chung kết nhánh", "bracketPhase": "LOSERS", "raceTo": 7, "sortOrder": 7 },
    { "roundKey": "grand_final", "label": "Chung kết lớn", "bracketPhase": "GRAND_FINAL", "raceTo": 9, "sortOrder": 8 }
  ]
}
```

**Response 200**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "rulesSaved": 5,
  "setupStatus": "RACE_TO_DONE",
  "nextStep": "review"
}
```

---

## GET `/admin/formats/{code}/setup-summary`

Wizard Màn 4 — preview trước kích hoạt.

**Response 200**

```json
{
  "format": {
    "code": "SINGLE_ELIMINATION",
    "name": "Loại trực tiếp (1 lần thua)",
    "description": "Thua 1 trận là bị loại.",
    "handlerKey": "pool_single_elimination_handler",
    "schemaVersion": "1.0",
    "isActive": false,
    "sortOrder": 1
  },
  "configFields": [
    { "fieldKey": "bracket_size", "label": "Số slot bracket", "defaultValue": "16", "isRequired": true, "isVisibleToOwner": true },
    { "fieldKey": "third_place_match", "label": "Trận tranh hạng 3", "defaultValue": "true", "isRequired": true, "isVisibleToOwner": true }
  ],
  "raceToRules": [
    { "roundKey": "round_1", "label": "Vòng 1", "bracketPhase": "KNOCKOUT", "raceTo": 5 },
    { "roundKey": "final", "label": "Chung kết", "bracketPhase": "KNOCKOUT", "raceTo": 9 }
  ],
  "setupStatus": "READY_TO_ACTIVATE",
  "canActivate": true,
  "validationErrors": []
}
```

---

## POST `/admin/formats/{code}/activate`

Wizard Màn 4 — kích hoạt (Owner mới chọn được format).

**Body:** `{}` hoặc không body

**Response 200**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "isActive": true,
  "setupStatus": "ACTIVE"
}
```

---

## POST `/admin/formats/{code}/bootstrap-defaults`

*(Tùy chọn)* Insert nhanh default mẫu quán bi-a — thay cho PUT config-fields + race-to-rules thủ công.

**Body**

```json
{
  "overwrite": false
}
```

**Response 201**

```json
{
  "formatCode": "SINGLE_ELIMINATION",
  "configFieldsInserted": 7,
  "raceToRulesInserted": 5,
  "setupStatus": "READY_TO_ACTIVATE"
}
```

---

## GET `/admin/game-types`

**Response 200**

```json
{
  "items": [
    {
      "code": "9_BALL",
      "name": "9-Ball",
      "description": "Đánh bi 1→9.",
      "defaultRaceTo": 7,
      "compatibleTableTypes": ["POOL"],
      "isActive": true,
      "sortOrder": 1
    }
  ],
  "total": 1
}
```

---

## PUT `/admin/game-types/{code}`

**Body**

```json
{
  "name": "9-Ball",
  "description": "Giải quán phổ biến.",
  "defaultRaceTo": 7,
  "isActive": true,
  "sortOrder": 1
}
```

**Response 200** — object game type đã cập nhật

---

# OWNER API

---

## GET `/owner/formats`

Dropdown thể thức — chỉ format `isActive=true` và đã setup default (`isReady=true`).

**Response 200**

```json
{
  "items": [
    {
      "code": "SINGLE_ELIMINATION",
      "name": "Loại trực tiếp (1 lần thua)",
      "description": "Thua 1 trận là bị loại.",
      "sortOrder": 1,
      "isReady": true
    },
    {
      "code": "DOUBLE_ELIMINATION",
      "name": "Loại kép",
      "description": "Nhánh thắng + nhánh thua.",
      "sortOrder": 2,
      "isReady": true
    }
  ],
  "total": 2
}
```

---

## GET `/owner/game-types`

**Response 200**

```json
{
  "items": [
    { "code": "9_BALL", "name": "9-Ball", "defaultRaceTo": 7, "sortOrder": 1 },
    { "code": "8_BALL", "name": "8-Ball", "defaultRaceTo": 5, "sortOrder": 2 }
  ],
  "total": 2
}
```

---

## POST `/owner/tournaments`

Wizard Step 1 — tạo giải (chưa có config).

**Body**

```json
{
  "name": "CLB Bi-a FPT — Mở rộng 9-Ball 2026",
  "description": "Giải đơn 9-Ball, alternate break.",
  "gameType": "9_BALL",
  "format": "SINGLE_ELIMINATION",
  "participantType": "SINGLE",
  "maxParticipants": 16,
  "entryFee": 200000,
  "prizePool": 8000000,
  "prizeDescription": "Vô địch 4tr | Á quân 2.4tr | Hạng 3 1.6tr",
  "registrationDeadline": "2026-05-20T23:59:59+07:00",
  "startAt": "2026-05-25T08:00:00+07:00",
  "endAt": "2026-05-25T20:00:00+07:00"
}
```

**Response 201**

```json
{
  "id": 1001,
  "name": "CLB Bi-a FPT — Mở rộng 9-Ball 2026",
  "gameType": "9_BALL",
  "format": "SINGLE_ELIMINATION",
  "participantType": "SINGLE",
  "status": "DRAFT",
  "maxParticipants": 16,
  "configComplete": false
}
```

---

## PUT `/owner/tournaments/{id}`

Sửa thông tin cơ bản (chỉ khi `status=DRAFT` hoặc trước draw).

**Path:** `id` — tournament id

**Body** *(các field optional, gửi field cần sửa)*

```json
{
  "name": "CLB Bi-a FPT — Mở rộng 9-Ball 2026 (v2)",
  "maxParticipants": 16,
  "entryFee": 250000,
  "prizePool": 10000000,
  "registrationDeadline": "2026-05-22T23:59:59+07:00"
}
```

**Response 200**

```json
{
  "id": 1001,
  "status": "DRAFT"
}
```

> Đổi `format` khi `status=DRAFT` → BE xóa `tournament_config_values` + load lại default format mới.

---

## GET `/owner/tournaments/{id}/config-form`

Wizard Step 2 — load form, **pre-fill từ Admin default**.

**Response 200 (chưa lưu config giải — toàn bộ từ Admin default)**

```json
{
  "tournamentId": 1001,
  "tournamentName": "CLB Bi-a FPT — Mở rộng 9-Ball 2026",
  "formatCode": "SINGLE_ELIMINATION",
  "formatName": "Loại trực tiếp (1 lần thua)",
  "formatDescription": "Thua 1 trận là bị loại.",
  "gameType": "9_BALL",
  "seedingMethod": null,
  "isConfigComplete": false,
  "fields": [
    {
      "fieldKey": "bracket_size",
      "label": "Số slot bracket",
      "dataType": "INT",
      "uiComponent": "NUMBER",
      "minValue": 8,
      "maxValue": 64,
      "value": "16",
      "defaultValue": "16",
      "isRequired": true,
      "source": "ADMIN_DEFAULT"
    },
    {
      "fieldKey": "allow_bye",
      "label": "Cho phép BYE",
      "dataType": "BOOLEAN",
      "uiComponent": "CHECKBOX",
      "value": "true",
      "defaultValue": "true",
      "isRequired": true,
      "source": "ADMIN_DEFAULT"
    },
    {
      "fieldKey": "third_place_match",
      "label": "Trận tranh hạng 3",
      "dataType": "BOOLEAN",
      "uiComponent": "CHECKBOX",
      "value": "true",
      "defaultValue": "true",
      "isRequired": true,
      "source": "ADMIN_DEFAULT"
    },
    {
      "fieldKey": "break_rule",
      "label": "Luật break",
      "dataType": "ENUM",
      "uiComponent": "SELECT",
      "enumOptions": ["ALTERNATE_BREAK", "WINNER_BREAK", "LOSER_BREAK"],
      "value": "ALTERNATE_BREAK",
      "defaultValue": "ALTERNATE_BREAK",
      "isRequired": true,
      "source": "ADMIN_DEFAULT"
    },
    {
      "fieldKey": "lag_for_break",
      "label": "Lag giành break đầu",
      "dataType": "BOOLEAN",
      "uiComponent": "CHECKBOX",
      "value": "true",
      "defaultValue": "true",
      "isRequired": true,
      "source": "ADMIN_DEFAULT"
    }
  ],
  "raceToRules": [
    { "roundKey": "round_1", "label": "Vòng 1", "bracketPhase": "KNOCKOUT", "raceTo": 5, "defaultRaceTo": 5, "isOverridden": false, "source": "ADMIN_DEFAULT" },
    { "roundKey": "quarter_final", "label": "Tứ kết", "bracketPhase": "KNOCKOUT", "raceTo": 7, "defaultRaceTo": 7, "isOverridden": false, "source": "ADMIN_DEFAULT" },
    { "roundKey": "semi_final", "label": "Bán kết", "bracketPhase": "KNOCKOUT", "raceTo": 7, "defaultRaceTo": 7, "isOverridden": false, "source": "ADMIN_DEFAULT" },
    { "roundKey": "third_place", "label": "Tranh hạng 3", "bracketPhase": "KNOCKOUT", "raceTo": 7, "defaultRaceTo": 7, "isOverridden": false, "source": "ADMIN_DEFAULT" },
    { "roundKey": "final", "label": "Chung kết", "bracketPhase": "KNOCKOUT", "raceTo": 9, "defaultRaceTo": 9, "isOverridden": false, "source": "ADMIN_DEFAULT" }
  ],
  "seedingOptions": ["RANDOM", "MANUAL", "ELO"]
}
```

**Response 200 (đã lưu — có giá trị override)**

```json
{
  "tournamentId": 1001,
  "formatCode": "SINGLE_ELIMINATION",
  "seedingMethod": "ELO",
  "isConfigComplete": true,
  "fields": [
    { "fieldKey": "bracket_size", "value": "16", "defaultValue": "16", "source": "TOURNAMENT" },
    { "fieldKey": "allow_bye", "value": "false", "defaultValue": "true", "source": "TOURNAMENT" }
  ],
  "raceToRules": [
    { "roundKey": "final", "raceTo": 11, "defaultRaceTo": 9, "isOverridden": true, "source": "TOURNAMENT" }
  ],
  "seedingOptions": ["RANDOM", "MANUAL", "ELO"]
}
```

---

## PUT `/owner/tournaments/{id}/config`

Wizard Step 2 — lưu config giải (tầng 3).

**Body**

```json
{
  "seedingMethod": "ELO",
  "fields": [
    { "fieldKey": "bracket_size", "value": "16" },
    { "fieldKey": "allow_bye", "value": "false" },
    { "fieldKey": "seeding_enabled", "value": "true" },
    { "fieldKey": "third_place_match", "value": "true" },
    { "fieldKey": "break_rule", "value": "ALTERNATE_BREAK" },
    { "fieldKey": "lag_for_break", "value": "true" }
  ],
  "raceToOverrides": [
    { "roundKey": "final", "raceTo": 11 }
  ]
}
```

| Field | Required | Ghi chú |
|-------|----------|---------|
| `seedingMethod` | ✅ | `RANDOM` \| `MANUAL` \| `ELO` |
| `fields` | ✅ | Mọi field `isRequired` của format |
| `fields[].fieldKey` | ✅ | Phải thuộc `format_config_fields` |
| `fields[].value` | ✅ | string |
| `raceToOverrides` | | Chỉ gửi vòng **đổi** so với Admin default |

**Response 200**

```json
{
  "tournamentId": 1001,
  "formatCode": "SINGLE_ELIMINATION",
  "seedingMethod": "ELO",
  "isConfigComplete": true,
  "validationErrors": []
}
```

**Response 400**

```json
{
  "code": "CONFIG_VALIDATION_FAILED",
  "message": "Config giải không hợp lệ",
  "details": [
    { "field": "bracket_size", "message": "Thiếu giá trị bắt buộc" }
  ]
}
```

---

## GET `/owner/tournaments/{id}/config`

Wizard Step 3 — config đã resolve (preview trước mở đăng ký).

**Response 200**

```json
{
  "tournamentId": 1001,
  "formatCode": "SINGLE_ELIMINATION",
  "formatName": "Loại trực tiếp (1 lần thua)",
  "gameType": "9_BALL",
  "seedingMethod": "ELO",
  "isConfigComplete": true,
  "fields": {
    "bracket_size": 16,
    "allow_bye": false,
    "seeding_enabled": true,
    "third_place_match": true,
    "break_rule": "ALTERNATE_BREAK",
    "lag_for_break": true,
    "scoring_unit": "GAME"
  },
  "raceToRules": {
    "round_1": 5,
    "quarter_final": 7,
    "semi_final": 7,
    "third_place": 7,
    "final": 11
  },
  "overriddenRounds": ["final"]
}
```

---

## POST `/owner/tournaments/{id}/config/validate`

**Body:** `{}`

**Response 200 — pass**

```json
{
  "tournamentId": 1001,
  "isValid": true,
  "isConfigComplete": true,
  "errors": [],
  "warnings": []
}
```

**Response 200 — fail**

```json
{
  "tournamentId": 1001,
  "isValid": false,
  "isConfigComplete": false,
  "errors": [
    { "fieldKey": "seedingMethod", "message": "Chưa chọn phương thức xếp hạt giống" }
  ],
  "warnings": []
}
```

---

## PATCH `/owner/tournaments/{id}/status`

Mở đăng ký / đổi trạng thái giải.

**Body**

```json
{
  "status": "OPEN_FOR_REGISTRATION"
}
```

**Response 200**

```json
{
  "id": 1001,
  "status": "OPEN_FOR_REGISTRATION",
  "previousStatus": "DRAFT"
}
```

**Response 400 — thiếu config**

```json
{
  "code": "CONFIG_INCOMPLETE",
  "message": "Config giải chưa hoàn tất",
  "details": [
    { "fieldKey": "bracket_size", "message": "Thiếu giá trị bắt buộc" }
  ]
}
```

---

## GET `/owner/tournaments/{id}`

Chi tiết giải.

**Response 200**

```json
{
  "id": 1001,
  "name": "CLB Bi-a FPT — Mở rộng 9-Ball 2026",
  "description": "Giải đơn 9-Ball.",
  "gameType": "9_BALL",
  "format": "SINGLE_ELIMINATION",
  "formatName": "Loại trực tiếp (1 lần thua)",
  "participantType": "SINGLE",
  "status": "OPEN_FOR_REGISTRATION",
  "maxParticipants": 16,
  "entryFee": 200000,
  "prizePool": 8000000,
  "registrationDeadline": "2026-05-20T23:59:59+07:00",
  "startAt": "2026-05-25T08:00:00+07:00",
  "endAt": "2026-05-25T20:00:00+07:00",
  "configComplete": true,
  "configSummary": {
    "seedingMethod": "ELO",
    "bracketSize": 16,
    "thirdPlaceMatch": true,
    "breakRule": "ALTERNATE_BREAK",
    "finalRaceTo": 11
  }
}
```

---

# ENUM & ERROR CODE

## Enum

| Key | Values |
|-----|--------|
| `setupStatus` | `DRAFT`, `INFO_DONE`, `CONFIG_FIELDS_DONE`, `RACE_TO_DONE`, `READY_TO_ACTIVATE`, `ACTIVE` |
| `seedingMethod` | `RANDOM`, `MANUAL`, `ELO` |
| `bracketPhase` | `KNOCKOUT`, `WINNERS`, `LOSERS`, `GRAND_FINAL`, `GROUP`, `PLAYOFF` |
| `fieldSource` | `ADMIN_DEFAULT`, `TOURNAMENT` |
| `tournamentStatus` | `DRAFT`, `OPEN_FOR_REGISTRATION`, `REGISTRATION_CLOSED`, `DRAW_DONE`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `breakRule` | `ALTERNATE_BREAK`, `WINNER_BREAK`, `LOSER_BREAK` |
| `dataType` | `INT`, `BOOLEAN`, `ENUM`, `STRING` |
| `handlerKey` | `pool_single_elimination_handler`, `pool_double_elimination_handler`, `pool_group_playoff_handler` |

## Error code

| Code | Khi nào |
|------|---------|
| `FORMAT_CODE_EXISTS` | POST format trùng code |
| `FORMAT_NOT_FOUND` | Không có format |
| `FORMAT_NOT_READY` | Admin chưa setup default — Owner tạo giải |
| `INVALID_FIELD_KEY` | field_key không trong catalog |
| `INVALID_FIELD_FOR_FORMAT` | field không thuộc format |
| `SETUP_INCOMPLETE` | Activate thiếu config/race-to |
| `ALREADY_BOOTSTRAPPED` | Bootstrap lại format đã có default |
| `CONFIG_VALIDATION_FAILED` | Owner PUT config sai |
| `CONFIG_INCOMPLETE` | Mở đăng ký khi thiếu config |
| `INVALID_STATUS_TRANSITION` | Chuyển status không hợp lệ |

---

# DB mapping (implement backend)

| API ghi | Bảng |
|---------|------|
| POST/PUT `/admin/formats` | `tournament_format_definitions` |
| PUT `.../config-fields` | `format_config_fields` |
| PUT `.../race-to-rules` | `format_race_to_rules` |
| POST `/owner/tournaments` | `tournaments` |
| PUT `.../config` | `tournament_configs`, `tournament_config_values`, `tournament_race_to_rules` |

Schema chi tiết: `phase-1-database.md`
