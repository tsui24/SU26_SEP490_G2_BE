---
name: git-feature-workflow
description: >-
  Guides dev/prod branching for SU26_SEP490_G2_BE with branch names
  action/dev/feature (e.g. feature/nguyen-van-a/user-login). Start from prod,
  merge to dev after pull, rebase prod before PR. Use when starting a branch,
  merge to dev, rebase, or team git workflow.
---

# Git feature workflow (dev / prod)

Áp dụng cho **SU26_SEP490_G2_BE**. Nhánh `dev` = test; `prod` = deploy.

## Đặt tên nhánh

```
<action>/<tên-dev>/<tên-feature>
```

- `action`: `feature` | `fix` | `hotfix` | …
- `tên-dev`: username / tên viết tắt, chữ thường, kebab-case — ví dụ `nguyen-van-a`
- `tên-feature`: mô tả task, kebab-case — ví dụ `user-login`

Ví dụ đầy đủ: `feature/nguyen-van-a/user-login`

Trong các lệnh dưới, thay `<branch>` bằng tên nhánh đủ 3 cấp (ví dụ `feature/nguyen-van-a/user-login`).

## 1. Bắt đầu feature mới

```bash
git fetch origin
git checkout prod
git pull origin prod
git checkout -b feature/<tên-dev>/<tên-feature>
```

Ví dụ: `git checkout -b feature/nguyen-van-a/user-login`

## 2. Làm việc trên feature

- Commit theo rule `commit-message` (Conventional Commits).
- Test local: `mvn test` (từ thư mục gốc project).

```bash
git add <files>
git commit -m "feat(scope): mô tả ngắn"
```

## 3. Merge vào dev (sau khi test local OK)

**Bắt buộc pull dev mới nhất trước khi merge.**

```bash
git fetch origin
git checkout <branch>
git pull origin dev
# Giải quyết conflict nếu có, chạy lại mvn test
```

**Cách A — PR vào dev (khuyến nghị):**

```bash
git push -u origin <branch>
# Tạo PR: base = dev, compare = <branch>
```

**Cách B — merge local:**

```bash
git checkout dev
git pull origin dev
git merge <branch>
git push origin dev
```

Sau đó test trên môi trường **dev**; không PR prod nếu dev chưa pass.

## 4. PR vào prod (sau khi test dev OK)

**Bắt buộc rebase lên prod mới nhất trước PR.**

```bash
git fetch origin
git checkout <branch>
git rebase origin/prod
# Giải quyết conflict, mvn test lại
git push -u origin <branch>
# Nếu đã push trước đó và rebase rewrite history: chỉ force-push NHÁNH FEATURE
# git push --force-with-lease origin <branch>
```

Tạo PR: **base = `prod`**, **compare = `<branch>`** (ví dụ `feature/nguyen-van-a/user-login`).

Dùng skill `create-pr-to-prod` hoặc `gh pr create` khi user yêu cầu tạo PR.

## 5. Sau khi PR prod được merge

```bash
git checkout prod
git pull origin prod
git checkout dev
git pull origin dev
# Đồng bộ dev với prod nếu team quy định (merge prod → dev hoặc cherry-pick)
git branch -d <branch>
git push origin --delete <branch>   # nếu remote còn nhánh feature
```

## Checklist nhanh

```
[ ] prod mới nhất khi tạo feature
[ ] Test local trước merge dev
[ ] pull origin dev trước merge vào dev
[ ] Test trên môi trường dev
[ ] rebase origin/prod trước PR prod
[ ] PR prod reviewed & merged
```

## Lỗi thường gặp

| Vấn đề | Xử lý |
|--------|--------|
| Conflict khi pull dev | Sửa file → `git add` → `git merge --continue` hoặc hoàn tất rebase |
| Rebase prod conflict | Sửa → `git rebase --continue` |
| Nhầm merge dev vào prod | Không merge dev→prod; chỉ PR feature đã rebase prod |

## Agent

- Chạy lệnh thật khi user yêu cầu thao tác Git.
- Không force-push `prod`/`dev`.
- Không commit/push trừ khi user yêu cầu.
