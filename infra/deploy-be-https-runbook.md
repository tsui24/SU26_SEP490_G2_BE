# Runbook — Đưa Backend lên HTTPS bằng Caddy

> Ngày soạn: 2026-08-07 · Phạm vi: **chỉ Backend + MinIO trên EC2**.
> CI/CD, MinIO và Dozzle đã có sẵn từ trước — việc còn lại là bọc HTTPS và sửa cấu hình MinIO.
> Ghi lại mọi thay đổi hạ tầng vào [§7 Nhật ký](#7-nhật-ký-thay-đổi).
> Runbook FE (Cloudflare Pages): [`SU26_SEP490_G2_FE/docs/deploy-cloudflare-pages.md`](../../SU26_SEP490_G2_FE/docs/deploy-cloudflare-pages.md).

---

## 0. Điền thông số

| Thông số | Giá trị |
|---|---|
| Domain | `biliardtournament.cloud` |
| Elastic IP của EC2 | `18.138.158.94` |
| SSH user | `______________` (thường là `ubuntu`) |
| Nhánh deploy | `prod` |

---

## 1. Hiện trạng — cái gì đã có, cái gì chưa

**Đã có, không cần dựng lại:**

| Hạng mục | Ở đâu |
|---|---|
| CI/CD Backend | [`SU26_SEP490_G2_BE/.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) — `test` → `deploy` trên self-hosted runner label `backend` |
| MinIO | [`docker-compose.yml`](docker-compose.yml), named volume `minio_data` |
| Dozzle | [`deploy/docker-compose.dozzle.yml`](../deploy/docker-compose.dozzle.yml), gộp qua `-f` lúc deploy |
| Database | RDS bên ngoài — cố ý không có container MySQL |

**Chưa có:** HTTPS. Backend đang mở `8080:8080` ra Internet và FE gọi thẳng
`http://18.138.158.94:8080`. Mục §6 của README-EC2 đã lên kế hoạch Caddy nhưng chưa từng chạy.

---

## 2. Thay đổi đã commit sẵn trong repo

Làm trước để bạn chỉ còn việc thao tác trên server.

### 2.1. `Caddyfile` (mới)

Đúng hai hostname: `api.` → `localhost:8080` và `cdn.` → `localhost:9000`.
Apex và `www` không đi qua Caddy — Cloudflare Pages lo.

### 2.2. `docker-compose.yml` — 5 sửa đổi

| Sửa | Vì sao |
|---|---|
| `minio` mở thêm `127.0.0.1:9000:9000` | Trước đây MinIO **không publish port 9000 ra host**, chỉ có console 9001 (đã xác nhận trên server: `docker ps` hiện `9000/tcp` trần). Caddy chạy trên host nên sẽ `connection refused` khi proxy tới |
| `backend` đổi `8080:8080` → `127.0.0.1:8080:8080` | Caddy thành đường vào duy nhất |
| `backend` đổi `MINIO_ENDPOINT` → `https://${MINIO_PUBLIC_HOST}` + `MINIO_SECURE=true` | Sửa bug ở §3 |
| `backend` thêm `extra_hosts: ${MINIO_PUBLIC_HOST}:host-gateway` | Tránh hairpin NAT, xem §3 |
| **Xoá hẳn service `frontend`** | FE chuyển sang Cloudflare Pages. Trên server service này vốn **chưa từng được tạo** (`docker ps -a` chỉ có backend + minio), nên xoá không mất gì |

### 2.3. `.env.example`

Thêm: `MINIO_PUBLIC_HOST`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`,
`PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`.

Bỏ: `FE_REPO_PATH`, `PUBLIC_API_URL` (chỉ service `frontend` dùng).

### 2.4. Repo FE — gỡ job deploy

`.github/workflows/deploy.yml` của repo FE đã bỏ job `deploy` (self-hosted runner
`frontend` → `docker compose up frontend`). Giữ lại sẽ **fail mỗi lần push** vì service
không còn tồn tại. Job `build-check` giữ nguyên làm CI.

> Đã verify bằng `docker compose config`, gồm cả trường hợp gộp overlay Dozzle —
> backend giữ nguyên `logging` 20m và port loopback sau khi merge.

---

## 3. Bug MinIO — nền của thay đổi 2.2

`docker-compose.yml` trước đây đặt `MINIO_ENDPOINT: http://minio:9000`. `minio` là hostname
**nội bộ Docker**. Backend chỉ có một property `app.minio.endpoint`
([`MinioProperties.java:14`](../src/main/java/com/capstone/su26_sep490_g2_be/config/MinioProperties.java#L14)),
dùng chung cho cả kết nối lẫn việc **ký presigned URL**
([`MinioStorageServiceImpl.java:121`](../src/main/java/com/capstone/su26_sep490_g2_be/service/impl/MinioStorageServiceImpl.java#L121)).

Hệ quả: URL ảnh trả cho trình duyệt là `http://minio:9000/...` — không client nào phân giải được.
Avatar, ảnh tin tức, ảnh giải đấu đều hỏng, **độc lập với chuyện Cloudflare**.

Không chữa được bằng cách thay host trong chuỗi URL: chữ ký SigV4 tính trên cả Host, đổi host
là `403 SignatureDoesNotMatch`. Bắt buộc phải ký bằng đúng hostname công khai.

**Hệ quả kéo theo — hairpin NAT.** Khi `MINIO_ENDPOINT` thành `https://cdn.biliardtournament.cloud`, chính
backend cũng phải gọi được địa chỉ đó. Nếu để nó phân giải ra Elastic IP, gói tin đi ra Internet
Gateway rồi vòng lại chính máy mình — AWS không đảm bảo hoạt động, upload ảnh có thể treo.
`extra_hosts: cdn.biliardtournament.cloud:host-gateway` trỏ thẳng về host nơi Caddy nghe 443; TLS vẫn hợp lệ
vì SNI đúng và Caddy trình đúng chứng chỉ. Traffic không rời máy.

> Đây là phương án "chỉ đổi env". Cách sạch hơn là thêm property `public-endpoint` + một
> `MinioClient` thứ hai chỉ để ký (~15 dòng Java), giữ `http://minio:9000` cho upload nội bộ.
> Ghi lại ở đây để cân nhắc sau nếu upload chậm.

---

## 4. Các bước thao tác trên EC2

### Bước 1 — DNS

**Chỉ 2 bản ghi trỏ về EC2** — apex và `www` sẽ do Cloudflare Pages phục vụ, KHÔNG trỏ về đây:

```
api   A   18.138.158.94
cdn   A   18.138.158.94
```

Hiện trạng lúc soạn (2026-08-08): domain còn ở nameserver Mắt Bão
(`ns1.matbao.com` / `ns2.matbao.com`), **chưa có bản ghi nào** — `api`/`cdn`/`www` đều NXDOMAIN.

Tạo 2 bản ghi trên trang quản trị Mắt Bão → *Quản lý tên miền* → *Quản lý DNS*.
Sau này khi chuyển nameserver sang Cloudflare (cần cho Pages), Cloudflare sẽ **tự quét và
nhập lại** 2 bản ghi này, không phải tạo thủ công lần nữa — nhớ để chúng ở **mây xám
(DNS only)**, bật proxy thì HTTP-01 challenge dừng ở Cloudflare và Caddy không lấy được
chứng chỉ.

Chờ có hiệu lực rồi kiểm tra:

```bash
nslookup api.biliardtournament.cloud 8.8.8.8   # phải ra 18.138.158.94
nslookup cdn.biliardtournament.cloud 8.8.8.8
```

### Bước 2 — Security Group

- Mở `80`, `443` cho `0.0.0.0/0`
- **Rule `8080`: GIỮ NGUYÊN ở bước này.** Xem cảnh báo bên dưới
- Giữ đóng `3306`, `9000`, `9001`
- `22`: Source đặt **My IP**, không gõ tay

> ⚠️ **Không xoá rule 8080 cho tới Bước 9.** FE production đang gọi thẳng
> `http://<elastic-ip>:8080` (CRA build cứng `REACT_APP_API_URL` vào bundle). Xoá sớm
> là web sập ngay và sập suốt quãng cài Caddy + chờ chứng chỉ + build lại FE.
> Chỉ xoá sau khi đã xác nhận web chạy được bằng domain mới.

> 💡 Rule `22` ghim vào một IP cố định là nguyên nhân phổ biến nhất khiến SSH
> `Connection timed out` — IP nhà mạng đổi sau mỗi lần reset modem, và đổi hẳn khi
> ngồi mạng khác. Dùng **My IP** để AWS tự điền lại thay vì gõ tay.

### Bước 3 — Cập nhật `/opt/deploy/.env`

Trên server, 5 biến dưới đây hiện **vẫn là IP trần** (`http://18.138.158.94...`) và
`MINIO_PUBLIC_HOST` thì chưa tồn tại. Sửa/thêm thành:

```bash
MINIO_PUBLIC_HOST=cdn.biliardtournament.cloud
FRONTEND_BASE_URL=https://biliardtournament.cloud
CORS_ALLOWED_ORIGIN=https://biliardtournament.cloud
PAYOS_RETURN_URL=https://biliardtournament.cloud/payment/success
PAYOS_CANCEL_URL=https://biliardtournament.cloud/payment/cancel
```

`PUBLIC_API_URL` và `FE_REPO_PATH` để lại cũng được — không còn ai đọc sau khi bỏ service
`frontend`.

### Bước 4 — Đồng bộ compose mới lên server

`/opt/deploy/docker-compose.yml` **không được version control**, phải copy tay:

```bash
scp infra/docker-compose.yml <user>@<ec2-host>:/opt/deploy/docker-compose.yml
```

### Bước 5 — Cài Caddy

⚠️ **`sudo apt install caddy` KHÔNG chạy được trên Ubuntu mặc định** — Caddy không nằm trong
kho chính thức của Ubuntu. Phải thêm repo Cloudsmith của Caddy trước:

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
```

Cài xong Caddy tự chạy và phục vụ trang mặc định ở port 80. Thay cấu hình:

Domain đã điền sẵn trong `Caddyfile`, không cần `sed` gì nữa:

```bash
sudo cp /opt/deploy/Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile   # kiểm tra cú pháp trước khi reload
sudo systemctl restart caddy
sudo journalctl -u caddy -f                          # theo dõi việc xin chứng chỉ
```

> Dùng `restart` chứ không phải `reload`: sau khi cài, Caddy đã được `systemctl stop`
> (2026-08-08) để không phơi trang mặc định ra Internet trong lúc chờ domain.

### Bước 6 — Dựng lại stack

```bash
cd /opt/deploy
docker compose --env-file .env up -d
docker compose ps
```

Chỉ còn 2 container. Cột PORTS phải là `127.0.0.1:8080->8080/tcp` và
`127.0.0.1:9000->9000/tcp, 127.0.0.1:9001->9001/tcp`. Thấy `0.0.0.0:` ở bất kỳ dòng nào
là còn hở ra Internet.

Backend vẫn khởi động bình thường dù `cdn.` chưa có gì phục vụ — nó bỏ qua lỗi kết nối
MinIO lúc boot. Chỉ upload/hiển thị ảnh là chưa dùng được cho tới khi Caddy chạy.

### Bước 7 — Trỏ FE sang domain mới

Sửa `REACT_APP_API_URL` trong [`SU26_SEP490_G2_FE/.github/workflows/deploy.yml`](../../SU26_SEP490_G2_FE/.github/workflows/deploy.yml)
từ `http://18.138.158.94:8080` thành `https://api.biliardtournament.cloud`, rồi push `prod` để build lại.
CRA nhúng biến lúc build nên bắt buộc phải build lại, restart container không đủ.

### Bước 8 — PayOS

Đổi URL webhook trong dashboard PayOS sang
`https://api.biliardtournament.cloud/api/v1/payments/payos/webhook`.

### Bước 9 — Đóng cổng 8080 (bước cuối cùng)

Chỉ làm khi đã xác nhận web chạy được hoàn toàn bằng domain mới. Vào
**EC2 → Security Groups → Inbound rules**, xoá rule `Custom TCP 8080 · 0.0.0.0/0`.

Kiểm tra từ máy bạn (không phải từ EC2):

```bash
curl -m 5 http://<elastic-ip>:8080/api/v1/health   # phải TIMEOUT
curl -i https://api.biliardtournament.cloud/api/v1/health          # phải trả 200
```

Từ đây Caddy là đường vào duy nhất.

---

## 5. Checklist nghiệm thu

- [ ] `curl -i https://api.biliardtournament.cloud/api/v1/health` → `200`, chứng chỉ hợp lệ
- [ ] `docker compose ps` không có dòng nào bind `0.0.0.0`
- [ ] Đăng nhập được từ FE, F5 giữ phiên
- [ ] **Upload ảnh đại diện thành công** — xác nhận `extra_hosts` hoạt động, không bị hairpin
- [ ] Ảnh hiện được, URL trong DevTools là `https://cdn.biliardtournament.cloud/...`, không phải `http://minio:9000/...`
- [ ] WebSocket: tab Network thấy `wss://api.biliardtournament.cloud/ws` trạng thái `101 Switching Protocols`
- [ ] Thanh toán PayOS → quay về `https://biliardtournament.cloud/payment/success`
- [ ] Dozzle qua SSH tunnel vẫn vào được, thấy log backend
- [ ] Push thử 1 commit lên `prod` → workflow chạy xanh, container restart, site vẫn sống
- [ ] **Chỉ sau khi mọi mục trên đã xanh:** làm Bước 9 đóng cổng 8080, rồi
      `curl -m 5 http://<elastic-ip>:8080/api/v1/health` phải **timeout**

---

## 6. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| `ssh: connect to host ... port 22: Connection timed out` | Rule SSH trong SG đang ghim IP cũ. IP nhà mạng đổi sau reset modem / đổi mạng. Lấy IP hiện tại bằng `curl.exe https://checkip.amazonaws.com` rồi đặt Source = **My IP**. Lưu ý: `Permission denied` là chuyện khác hẳn — lúc đó đã kết nối được, chỉ sai user/key |
| `sudo apt install caddy` báo `Unable to locate package` | Chưa thêm repo Cloudsmith — xem Bước 5 |
| Caddy không lấy được chứng chỉ | DNS chưa kịp có hiệu lực, hoặc bản ghi đang bật proxy Cloudflare (mây cam), hoặc SG chưa mở 80 |
| Ảnh trả `403 SignatureDoesNotMatch` | Có proxy nào đó đổi header `Host`. Caddy giữ nguyên Host mặc định — kiểm tra xem có ai thêm `header_up Host` không |
| Upload ảnh treo/timeout | `extra_hosts` chưa có hiệu lực. Container phải được **tạo lại**, không phải restart: `docker compose up -d --force-recreate backend` |
| FE báo lỗi CORS | `CORS_ALLOWED_ORIGIN` chưa khớp domain FE. Lưu ý `application-prod.yml` khai list chỉ 1 phần tử — muốn nhiều domain phải dùng `APP_CORS_ALLOWEDORIGINPATTERNS_0/1/2` |
| Caddy lỗi `address already in use` khi start | Có gì đó đang giữ port 80/443. Kiểm tra `sudo ss -tlnp \| grep -E ':80 \|:443 '` — thường là container cũ chưa gỡ |
| Workflow FE fail ở job `deploy` | Chưa lấy bản `.github/workflows/deploy.yml` mới (đã gỡ job đó) — service `frontend` không còn tồn tại |
| Đổi `.env` mà không thấy tác dụng | `docker compose up -d` chỉ tạo lại container khi cấu hình đổi; ép bằng `--force-recreate` |

---

## 7. Nhật ký thay đổi

| Ngày | Người làm | Nội dung |
|---|---|---|
| 2026-08-07 | | Soạn runbook. Thêm `Caddyfile`; sửa `docker-compose.yml` (MinIO publish 9000, backend + frontend bind loopback, `MINIO_ENDPOINT` sang hostname công khai, thêm `extra_hosts`); bổ sung biến MinIO/PayOS vào `.env.example`; cập nhật `README-EC2.md` (§6 Caddy, §8 sửa nhánh `main` → `prod`, thêm §9 xem log) |
| 2026-08-07 | | Sửa cách cài Caddy: `apt install caddy` không chạy trên Ubuntu mặc định, phải thêm repo Cloudsmith |
| 2026-08-07 | | **Sửa thứ tự đóng cổng 8080.** Bản đầu bảo đóng ngay ở Bước 2 — như vậy web sập trong suốt quá trình chuyển đổi vì FE đang build cứng `http://<ip>:8080`. Chuyển thành Bước 9, làm sau cùng |
| 2026-08-08 | | Khảo sát server thực tế: `.env` có 26 biến nhưng **mọi URL vẫn là IP trần** (cần sửa 5 biến chứ không phải 1); service `frontend` **chưa từng được tạo**; port 80/443 trống; Caddy chưa cài |
| 2026-08-08 | | **Bỏ hẳn frontend khỏi EC2** theo yêu cầu (máy đang quá tải). Xoá service `frontend` khỏi `docker-compose.yml`; Caddyfile còn đúng 2 hostname `api.` + `cdn.`; bỏ `FE_REPO_PATH` + `PUBLIC_API_URL` khỏi `.env.example`; gỡ job `deploy` khỏi workflow FE; README-EC2 §4 bỏ phần cài runner FE và thêm lệnh gỡ runner cũ |
| 2026-08-08 | | Đo tài nguyên EC2: **2 vCPU / 1.9 GiB RAM / KHÔNG có swap**, chỉ còn ~670 Mi available. Disk 28G dùng 40% (thoải mái). `~/actions-runner-fe` còn 903 MB rác; Docker build cache 6 GB (4.4 GB xoá được). Nút thắt là RAM chứ không phải disk → nên tạo swap 2 GB |
| 2026-08-08 | | Đẩy `docker-compose.yml` + `Caddyfile` bản mới lên `/opt/deploy/`. Cài **Caddy v2.11.4** qua repo Cloudsmith (`apt install caddy` thường báo `Unable to locate package`). Đã `systemctl stop caddy` ngay sau khi cài để không phơi trang mặc định ra Internet — chờ có domain mới nạp config và start |
| 2026-08-08 | | Chốt domain **`biliardtournament.cloud`**. Điền thẳng vào `Caddyfile`, `.env.example` và cả hai runbook (bỏ bước `sed` thay placeholder). `caddy validate` trên server: **Valid configuration**. Xác nhận qua DNS: NS còn ở Mắt Bão, chưa có bản ghi nào — `api`/`cdn`/`www` đều NXDOMAIN |
| 2026-08-08 | | **BE lên HTTPS xong.** Tạo A record `api`/`cdn` → `18.138.158.94` trên Mắt Bão. Sao lưu `.env` thành `.env.bak-20260807-171956` rồi sửa 5 biến URL + thêm `MINIO_PUBLIC_HOST`. `docker compose up -d` tạo lại 2 container, cả hai chuyển sang bind loopback. Nạp Caddyfile và `systemctl restart caddy` → Let's Encrypt cấp chứng chỉ cho cả 2 hostname lúc 17:20 UTC (hạn 2026-11-05, Caddy tự gia hạn). Nghiệm thu từ ngoài Internet: `api` HTTP 200 + TLS hợp lệ, `cdn` HTTP 200, HTTP tự 308 sang HTTPS |
| 2026-08-08 | | Tạo **swap 2 GB** (`/swapfile`, `vm.swappiness=10`, đã ghi `/etc/fstab` nên tồn tại sau reboot). Máy chỉ có 1.9 GiB RAM và trước đó swap = 0 — build Maven của BE chạy ngay trên máy này rất dễ OOM. Disk sau khi tạo: 47% dùng, còn 15 G |
| 2026-08-08 | | Ghi nhận: **bot quét lỗ hổng tìm tới trong vòng vài giây** sau khi chứng chỉ được cấp (IP `81.171.74.60` dò `/dump.sql`, `/.env.production`, `/wp-config.php`, `/secrets.json`, `/actuator/heapdump`…). Đây là chuyện bình thường — chứng chỉ mới bị công khai qua Certificate Transparency log và bot quét ngay. Tất cả đều trả 502/404, không rò rỉ gì. `pom.xml` không có dependency actuator nên `/actuator/**` vốn không tồn tại |
| | | |
