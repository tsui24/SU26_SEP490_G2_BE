# SU26_SEP490_G2_BE — Agent guide

Spring Boot backend (Java 17, Maven). Git workflow **dev** (test) / **prod** (deploy).

## Git workflow (tóm tắt)

1. `prod` mới nhất → nhánh `<action>/<dev>/<feature>` (vd. `feature/nguyen-van-a/user-login`)
2. Test local → merge/PR vào `dev` (sau `pull origin dev`)
3. Test trên dev → rebase `origin/prod` → PR vào `prod`

Chi tiết: `.cursor/skills/git-feature-workflow/SKILL.md`

## Cursor rules

| Rule | Mục đích |
|------|----------|
| `git-branch-workflow.mdc` | Nhánh dev/prod, cấm thao tác nguy hiểm |
| `commit-message.mdc` | Conventional Commits |
| `spring-boot-java.mdc` | Convention Java/Spring (khi sửa `*.java`) |
| `vietnamese-messages.mdc` | Message lỗi / validation / API success — tiếng Việt |

## Skills (gọi khi cần)

- `git-feature-workflow` — từng bước branch/merge/rebase
- `create-pr-to-prod` — PR feature → prod sau khi dev OK

## Backend structure

- `entity/` — `BaseEntity` (createdAt/updatedAt), `Role`, `User`, `UserProfile`
- `controller/` — `ResponseEntity<ApiResponse<T>>`, Swagger `@Tag` / `@Operation`
- `dto/request/` — input: `LoginRequest`, `RegisterRequest`, `CreateAccountRequest`, … (`*Request`)
- `dto/response/` — output: `UserResponse`, `LoginResponse`, … (`*Response`); `ApiResponse<T>`
- `enums/` — `ErrorCode`, `RoleCode`, `UserStatus`
- `service/` — `AuthService`, `AccountService`, `OtpService`, `EmailService`
- `config/` — `SecurityConfig`, `JwtAuthenticationFilter`, `JwtProperties`, `DataInitializer`, `OpenApiConfig`
- `util/` — `JwtUtil`, `WebClientUriBuilder`
- `api/` — gọi API ngoài bằng `WebClient`
- Swagger UI: http://localhost:8080/swagger-ui.html

## Roles

| Code | Mô tả | Ai tạo |
|------|--------|--------|
| ADMIN | Quản trị hệ thống | ADMIN tạo (seed ban đầu) |
| OWNER | Chủ chuỗi quán bi-a | ADMIN tạo |
| MANAGER | Quản lý cơ sở | OWNER tạo |
| STAFF | Nhân viên / trọng tài | OWNER tạo |
| PLAYER | Người chơi | Tự đăng ký |

## URL Authorization (SecurityConfig)

| URL pattern | Role |
|-------------|------|
| `/api/v1/admin/**` | ADMIN |
| `/api/v1/owner/**` | OWNER |
| `/api/v1/manager/**` | MANAGER |
| `/api/v1/staff/**` | STAFF |
| `/api/v1/player/**` | PLAYER |
| `/api/v1/auth/**` | Public (login, register, ...) + Authenticated (change-password) |

## Lệnh hữu ích

```bash
mvn test
mvn spring-boot:run
```
