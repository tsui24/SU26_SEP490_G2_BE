# Setup EC2 cho CI/CD (làm 1 lần)

## 1. Launch EC2
- AMI: Ubuntu 22.04/24.04 LTS
- Instance type: `t3.small` trở lên (BE + FE + MySQL + MinIO cùng chạy)
- Gắn **Elastic IP** để có IP tĩnh
- Security Group:
  - `22` (SSH) — chỉ IP của bạn
  - `80`, `443` (HTTP/HTTPS) — `0.0.0.0/0`
  - Không mở `3306`, `8080`, `9000`, `9001` ra ngoài

## 2. Cài Docker
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker
docker compose version
```

## 3. Chuẩn bị thư mục deploy
```bash
sudo mkdir -p /opt/deploy
sudo chown $USER:$USER /opt/deploy
```
Copy 2 file `docker-compose.yml` và `.env.example` (đổi tên thành `.env`, điền giá trị thật) từ thư mục `infra/` vào `/opt/deploy/` trên EC2 (dùng `scp`).

## 4. Cài self-hosted runner cho repo BE
Vào repo BE trên GitHub → **Settings → Actions → Runners → New self-hosted runner**:

```bash
mkdir ~/actions-runner-be && cd ~/actions-runner-be
# ... tải & giải nén theo hướng dẫn GitHub cung cấp ...
./config.sh --url https://github.com/<org>/SU26_SEP490_G2_BE --token <TOKEN> --labels backend --name ec2-backend
sudo ./svc.sh install
sudo ./svc.sh start
```

Đường dẫn workspace runner tạo ra chính là giá trị `BE_REPO_PATH` trong `/opt/deploy/.env`:
```
~/actions-runner-be/_work/SU26_SEP490_G2_BE/SU26_SEP490_G2_BE
```

> **Không cài runner cho FE.** Từ 2026-08-08 frontend build trên Cloudflare Pages.
> Nếu máy đang còn `~/actions-runner-fe` thì gỡ đi để nhẹ máy:
> ```bash
> cd ~/actions-runner-fe && sudo ./svc.sh stop && sudo ./svc.sh uninstall
> ./config.sh remove --token <TOKEN lay tu GitHub>
> cd ~ && rm -rf ~/actions-runner-fe
> ```
> Riêng thư mục `_work` của runner FE chứa cả `node_modules` — thường là vài trăm MB.

## 5. Chạy lần đầu
```bash
cd /opt/deploy
docker compose --env-file .env up -d --build
docker compose ps
```

## 6. Domain + HTTPS (Caddy)

> Chi tiết từng bước, checklist nghiệm thu và nhật ký thay đổi: **[deploy-be-https-runbook.md](deploy-be-https-runbook.md)**.
> Phần dưới chỉ là tóm tắt.

Caddy chạy **trên host** (không trong Docker), tự xin và tự gia hạn Let's Encrypt.
Nó là đường vào duy nhất — mọi container đều chỉ bind loopback.

Caddy **không có trong kho Ubuntu mặc định** — phải thêm repo Cloudsmith trước:

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy

sudo cp /opt/deploy/Caddyfile /etc/caddy/Caddyfile
sudo sed -i 's/<domain>/yourdomain.com/g' /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Hai hostname được phục vụ (xem [`Caddyfile`](Caddyfile)):

| Hostname | Đích | Dùng cho |
|---|---|---|
| `api.<domain>` | `localhost:8080` | REST API + WebSocket `/ws` |
| `cdn.<domain>` | `localhost:9000` | Ảnh MinIO (presigned URL) |

Apex `<domain>` và `www` **không đi qua Caddy** — do Cloudflare Pages phục vụ.

**Thứ tự bắt buộc:** trỏ A record của cả 2 hostname về Elastic IP **trước**, chờ DNS
có hiệu lực, rồi mới `reload caddy`. Reload sớm thì Let's Encrypt fail và Caddy sẽ
retry với backoff tăng dần, phải chờ khá lâu mới thử lại.

⚠️ **Nếu domain đã chuyển nameserver sang Cloudflare** (do FE dùng Pages): riêng 2 bản ghi
`api` và `cdn` phải để **mây xám / DNS only**. Bật proxy (mây cam) thì thử thách HTTP-01
dừng ở Cloudflare, không tới được Caddy, chứng chỉ không cấp được.
Bản ghi apex/www do Pages tạo thì cứ để proxy bình thường — Pages tự lo TLS.

## 7. RDS (MySQL đã có sẵn — không chạy container mysql nữa)
Chỉnh Security Group của RDS: inbound port `3306` chỉ cho phép từ **Security Group của EC2** (hoặc IP riêng của EC2), không để `0.0.0.0/0` vì RDS đang có public IP.
Điền `RDS_ENDPOINT`, `RDS_DATABASE`, `RDS_USERNAME`, `RDS_PASSWORD` vào `/opt/deploy/.env`.

## 8. Từ giờ về sau
Mỗi lần push lên nhánh **`prod`** ở repo BE hoặc FE, workflow tương ứng sẽ tự build lại đúng container đó và restart với `SPRING_PROFILES_ACTIVE=prod` — không đụng tới container còn lại. MinIO data giữ nguyên nhờ named volume; dữ liệu MySQL nằm trên RDS nên không phụ thuộc vòng đời của EC2/container.

Workflow BE còn gộp thêm overlay [`deploy/docker-compose.dozzle.yml`](../deploy/docker-compose.dozzle.yml) để dựng **Dozzle** (xem log real-time) và bật xoay vòng log cho backend. Cách xem log: [`deploy/README.md`](../deploy/README.md).

## 9. Xem log
```bash
ssh -L 9999:127.0.0.1:9999 <user>@<ec2-host>
```
Giữ terminal mở, vào <http://localhost:9999>. Dozzle cố ý chỉ nghe loopback vì nó mount `docker.sock` — ai vào được UI là đọc được toàn bộ biến môi trường của mọi container (`DB_PASSWORD`, `JWT_SECRET`, `PAYOS_CHECKSUM_KEY`).
