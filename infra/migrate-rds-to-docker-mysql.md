# Runbook — Chuyển database production từ RDS sang MySQL trong Docker

> Soạn 2026-08-11 · Ghi kết quả từng bước vào cuối file để truy vết.

**Lý do chuyển:** RDS instance 1 GiB có buffer pool quá nhỏ, query nặng bị nghẽn, trong khi chi
phí cao hơn tự chạy MySQL trên một EC2 nhiều RAM hơn.

**Phạm vi:**
- ✅ Production (`application-prod.yml`) → MySQL trong Docker
- ❌ Dev (`application-dev.yml`) → **giữ nguyên RDS `capstone26-test`**, không đụng tới
- ❌ **Không migrate dữ liệu** — DB mới tạo trống, Hibernate dựng schema, seeder nạp dữ liệu mẫu

> ⚠️ **Dữ liệu production hiện tại sẽ mất.** Đây là quyết định đã thống nhất. Nếu có đăng ký hoặc
> giao dịch thật cần giữ, dừng lại và dump trước khi làm tiếp.

**Không cần sửa code.** `application-prod.yml` đọc `url: ${DB_URL}` từ biến môi trường; overlay
ghi đè biến đó. Toàn bộ thay đổi nằm ở tầng compose.

---

## Điều kiện tiên quyết

- [ ] **SSH vào được EC2.** Hiện port 22 đang timeout — sửa rule Security Group, Source = *My IP*
- [ ] Xác nhận EC2 đang dùng **Elastic IP** (không phải public IP tự cấp). Nếu là IP tự cấp thì
      stop/start sẽ đổi IP và DNS `api.`/`cdn.` hỏng hết
- [ ] Sinh sẵn 2 mật khẩu mạnh: `openssl rand -base64 32`

---

## Bước 1 — Nâng cấp instance

**Downtime: ~5 phút.**

1. Chụp EBS snapshot làm đường lùi: EC2 → Volumes → chọn volume → *Create snapshot*
2. EC2 → Instances → chọn instance → **Instance state → Stop instance**
3. Chờ trạng thái `stopped` → **Actions → Instance settings → Change instance type**
   → chọn `m7i-flex.large` (2 vCPU / 8 GiB)
4. EC2 → Volumes → chọn volume → **Modify volume** → tăng Size lên **60–80 GB**
5. **Start instance**

Sau khi máy lên, kiểm tra mọi thứ tự phục hồi:

```bash
free -h                    # RAM phải ~8 GiB, Swap 2 GiB (fstab giữ swap qua reboot)
docker ps                  # backend + minio phải tự chạy lại (restart: unless-stopped)
systemctl is-active caddy  # phải active (đã enable)
curl -s -o /dev/null -w '%{http_code}\n' https://api.biliardtournament.cloud/api/v1/health
```

## Bước 2 — Nới filesystem cho khớp EBS mới

Tăng EBS **không tự** nới filesystem — bước này rất hay bị quên.

```bash
df -h /                    # vẫn thấy 28G — đúng như dự kiến, chưa nới
lsblk

sudo growpart /dev/nvme0n1 1     # thay tên thiết bị theo output lsblk
sudo resize2fs /dev/nvme0n1p1    # ext4; nếu là xfs thì dùng: sudo xfs_growfs /

df -h /                    # giờ phải thấy dung lượng mới
```

## Bước 3 — Đưa file cấu hình lên server

```powershell
# chạy trên máy cá nhân, từ thư mục repo BE
cd d:\FUlearn\Ki_9\capstone\SU26_SEP490_G2_BE\infra
scp -i D:\sep490-g2.pem docker-compose.mysql.yml ubuntu@18.138.158.94:/opt/deploy/
scp -i D:\sep490-g2.pem scripts/backup-mysql.sh ubuntu@18.138.158.94:/opt/deploy/scripts/
```

Nếu `/opt/deploy/scripts` chưa có: `ssh ... "mkdir -p /opt/deploy/scripts"`

## Bước 4 — Thêm biến vào `.env`

```bash
cp /opt/deploy/.env /opt/deploy/.env.bak-$(date +%Y%m%d-%H%M%S)
nano /opt/deploy/.env
```

Thêm 4 dòng (mật khẩu sinh ở phần điều kiện tiên quyết):

```bash
MYSQL_ROOT_PASSWORD=<mật khẩu mạnh 1>
MYSQL_DATABASE=su26_sep490_g2
MYSQL_APP_USER=btms_app
MYSQL_APP_PASSWORD=<mật khẩu mạnh 2>
```

Giữ nguyên các biến `RDS_*` — dev vẫn dùng, và cần cho đường lùi.

## Bước 5 — Bật MySQL

```bash
cd /opt/deploy
docker compose \
  -f docker-compose.yml \
  -f docker-compose.mysql.yml \
  --env-file .env \
  up -d mysql

# Chờ tới khi cột STATUS hiện (healthy) — lần đầu mất 40–60 giây
watch -n 3 'docker compose -f docker-compose.yml -f docker-compose.mysql.yml ps mysql'
```

Kiểm tra:
```bash
docker exec -it deploy-mysql-1 mysql -u root -p -e "SELECT VERSION(); SHOW DATABASES;"
```

## Bước 6 — Tắt email tạm thời

> ⚠️ **Đừng bỏ qua bước này.** DB trống nghĩa là seeder chạy lại từ đầu.
> `RegistrationSeedInitializer` đi qua service thật nên **publish event mail thật** —
> ước tính **trên 100 email** gửi tới `player1..16@gmail.com` trong vài giây.
> Gmail SMTP sẽ chặn tạm tài khoản gửi vì hành vi này.

```bash
nano /opt/deploy/.env
# Tạm thời để trống:
#   MAIL_USERNAME=
#   MAIL_PASSWORD=
```

Mail được gửi bất đồng bộ nên lỗi SMTP chỉ ghi log, không làm sập ứng dụng.

## Bước 7 — Chuyển backend sang MySQL mới

```bash
cd /opt/deploy
docker compose \
  -f docker-compose.yml \
  -f docker-compose.mysql.yml \
  --env-file .env \
  up -d --force-recreate backend

# Theo dõi Hibernate dựng schema và seeder chạy
docker logs -f deploy-backend-1
```

Chờ tới khi thấy `Started Su26Sep490G2BeApplication`. Lần đầu lâu hơn bình thường vì phải tạo
39+ bảng và chạy 5 seeder.

Kiểm tra nhanh:
```bash
docker exec -it deploy-mysql-1 mysql -u root -p \
  -e "SELECT COUNT(*) AS so_bang FROM information_schema.tables WHERE table_schema='su26_sep490_g2';"
```

## Bước 8 — Bật lại email

```bash
nano /opt/deploy/.env      # khôi phục MAIL_USERNAME / MAIL_PASSWORD
docker compose -f docker-compose.yml -f docker-compose.mysql.yml \
  --env-file .env up -d --force-recreate backend
```

Lần này DB đã có dữ liệu nên seeder không chạy lại (tất cả đều idempotent) — không có email nào
được gửi.

## Bước 9 — ⚠️ SỬA WORKFLOW DEPLOY — quan trọng nhất

**Đây là bước dễ quên nhất và hậu quả âm thầm nhất.**

`.github/workflows/deploy.yml` hiện chỉ gộp overlay Dozzle. Nếu không sửa, **lần push `prod` kế
tiếp sẽ tạo lại backend không có overlay mysql** → nó quay về đọc biến `RDS_*` và nối lại RDS cũ.
Ứng dụng vẫn chạy bình thường, không báo lỗi gì — bạn chỉ phát hiện khi thấy dữ liệu mới biến mất.

Sửa trong repo, thêm một dòng `-f`:

```yaml
      - name: Build & restart backend + dozzle
        run: |
          docker compose \
            -f /opt/deploy/docker-compose.yml \
            -f /opt/deploy/docker-compose.mysql.yml \
            -f "$GITHUB_WORKSPACE/deploy/docker-compose.dozzle.yml" \
            --env-file /opt/deploy/.env \
            up -d --build backend dozzle
```

> Thứ tự `-f` vẫn phải giữ `/opt/deploy/docker-compose.yml` ở đầu — file đầu tiên quyết định tên
> project. Đảo chỗ sẽ khiến compose coi các container đang chạy là của project khác và bỏ rơi chúng.

Commit và push. Đợi workflow chạy xong rồi kiểm tra backend vẫn nối đúng MySQL container:

```bash
docker exec deploy-backend-1 printenv DB_URL
# Kỳ vọng: jdbc:mysql://mysql:3306/...   KHÔNG được là endpoint RDS
```

## Bước 10 — Cron backup

RDS lo giúp snapshot tự động. Tự chạy Docker là tự nhận trách nhiệm đó.

```bash
sudo mkdir -p /opt/deploy/backups && sudo chown $USER:$USER /opt/deploy/backups
chmod +x /opt/deploy/scripts/backup-mysql.sh

# Chạy thử một lần bằng tay TRƯỚC khi tin vào cron
/opt/deploy/scripts/backup-mysql.sh
ls -lh /opt/deploy/backups/

crontab -e
# 0 3 * * * /opt/deploy/scripts/backup-mysql.sh >> /opt/deploy/backups/backup.log 2>&1
```

## Bước 11 — Mở cổng 3306 cho MySQL Workbench

EC2 → Security Groups → Inbound rules → Add rule:

```
Type: MySQL/Aurora   Port: 3306   Source: <IP của từng thành viên>/32
```

**Đừng để `0.0.0.0/0`.**

Nối từ Workbench:
```
Hostname : 18.138.158.94        Port: 3306
Username : btms_app             Password: <MYSQL_APP_PASSWORD>
SSL      : tab SSL → Use SSL = Require
```

- Không dùng được `root` từ xa — đã ghim `MYSQL_ROOT_HOST=localhost`. Muốn dùng root:
  `docker exec -it deploy-mysql-1 mysql -u root -p`
- Bắt buộc bật SSL vì `--require-secure-transport=ON`

## Bước 12 — Nghiệm thu

- [ ] `curl https://api.biliardtournament.cloud/api/v1/health` → `200`
- [ ] `curl .../api/v1/news?page=0&size=20` → `totalElements: 9`
- [ ] Đăng nhập được bằng tài khoản seed
- [ ] Upload ảnh vẫn hoạt động (MinIO không bị ảnh hưởng)
- [ ] `docker exec deploy-backend-1 printenv DB_URL` → trỏ `mysql:3306`
- [ ] `free -h` → còn dư ít nhất 2 GB
- [ ] Nối được từ MySQL Workbench
- [ ] Chạy tay backup script thành công, file `.sql.gz` > 1 KB
- [ ] Push thử một commit lên `prod` → sau khi deploy, `DB_URL` **vẫn** trỏ `mysql:3306`

## Bước 13 — Dọn dẹp

**Chỉ làm sau khi hệ thống chạy ổn định vài ngày.**

- [ ] Xoá **RDS production** — đây là khoản tiết kiệm chi phí chính
- [ ] **GIỮ** RDS `capstone26-test` — `application-dev.yml` vẫn dùng
- [ ] Sửa Security Group của `capstone26-test`: hiện đang mở `3306` ra toàn Internet trong khi
      hostname nằm trong repo public. Giới hạn theo IP hoặc tắt public accessibility

---

## Đường lùi

Nếu MySQL mới có vấn đề, quay lại RDS trong một lệnh — vì các biến `RDS_*` vẫn còn nguyên trong
`.env` và `docker-compose.yml` gốc không hề bị sửa:

```bash
cd /opt/deploy
docker compose -f docker-compose.yml --env-file .env up -d --force-recreate backend
```

Bỏ overlay mysql là backend tự đọc lại biến `RDS_*`. Container `mysql` vẫn chạy nhưng không ai
dùng, volume `mysql_data` vẫn nguyên.

Nếu đã sửa workflow ở Bước 9 thì nhớ revert commit đó, nếu không lần deploy sau lại chuyển sang
MySQL container.

---

## Sự cố thường gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| Backend crash-loop, log báo lỗi TLS | `--require-secure-transport=ON` xung khắc với `sslMode`. Phải sửa **cả hai**: bỏ flag đó **và** đổi `sslMode=DISABLED`. Sửa một bên là không nối được |
| Backend không nối được, MySQL vẫn `starting` | Thiếu `depends_on: service_healthy` — overlay đã có sẵn, kiểm tra đã dùng đúng file chưa |
| Workbench báo `Access denied` cho `root` | Đúng như thiết kế — `MYSQL_ROOT_HOST=localhost`. Dùng `btms_app` |
| Workbench timeout | Rule 3306 chưa mở, hoặc IP nhà mạng đã đổi. Đặt lại Source = *My IP* |
| Sau deploy dữ liệu "biến mất" | Chưa làm Bước 9 — backend đã âm thầm quay về RDS |
| `df -h` vẫn hiện dung lượng cũ | Chưa chạy `growpart` + `resize2fs` ở Bước 2 |
| MySQL bị OOM killed | `innodb-buffer-pool-size=2G` quá lớn so với RAM còn lại. Giảm xuống 1G |

---

## Nhật ký thực hiện

| Bước | Ngày | Người làm | Kết quả |
|---|---|---|---|
| 1. Nâng instance | | | |
| 2. Nới filesystem | | | |
| 3–4. File + biến môi trường | | | |
| 5. Bật MySQL | | | |
| 6–8. Chuyển backend | | | |
| 9. Sửa workflow | | | |
| 10. Cron backup | | | |
| 11. Mở 3306 | | | |
| 12. Nghiệm thu | | | |
| 13. Dọn dẹp | | | |
