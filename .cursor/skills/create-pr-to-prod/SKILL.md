---
name: create-pr-to-prod
description: >-
  Creates a pull request from a feature branch into prod for SU26_SEP490_G2_BE
  after verifying rebase on origin/prod. Use when the user wants a PR to prod,
  release to production branch, or merge feature after dev testing passed.
---

# Create PR to prod

Chỉ thực hiện khi **đã test OK trên dev** và nhánh **đã rebase `origin/prod`**.

Nhánh theo format: `<action>/<tên-dev>/<tên-feature>` — ví dụ `feature/nguyen-van-a/user-login`.

## Pre-flight

```bash
git fetch origin
git checkout <branch>
git rebase origin/prod
mvn test
git status
```

- Working tree sạch (hoặc user biết còn thay đổi chưa commit).
- Không có file secrets trong diff.

## Push nhánh feature

```bash
git push -u origin HEAD
# Sau rebase rewrite history trên nhánh đã push:
# git push --force-with-lease origin <branch>
```

## Tạo PR (GitHub CLI)

```bash
gh pr create --base prod --head <branch> --title "feat(scope): mô tả ngắn" --body "$(cat <<'EOF'
## Summary
- ...

## Test plan
- [ ] Đã test trên môi trường dev
- [ ] `mvn test` pass sau rebase prod
- [ ] ...

EOF
)"
```

Thay `<branch>` (ví dụ `feature/nguyen-van-a/user-login`) và nội dung body cho đúng task.

## PR body template

```markdown
## Summary
- Mô tả thay đổi (1–3 bullet)

## Test plan
- [ ] Test local: `mvn test`
- [ ] Đã verify trên môi trường dev
- [ ] API/flow chính: ...

## Notes
- Ticket / link liên quan (nếu có)
```

## Sau merge PR

Nhắc user: pull `prod`, sync `dev` theo quy ước team, xóa nhánh feature.

## Agent

1. `git status`, `git diff prod...HEAD`, `git log prod..HEAD` để tóm tắt PR.
2. Xác nhận đã rebase prod (hoặc chạy rebase nếu user đồng ý).
3. Dùng `gh pr create` khi user yêu cầu tạo PR.
4. Trả URL PR cho user.
