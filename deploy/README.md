# Xem log ứng dụng với Dozzle

Dozzle là web UI xem log Docker real-time. Đã được nối vào pipeline deploy sẵn —
không cần cài gì thêm trên server.

## Cách xem log

Dozzle **chỉ lắng nghe trên loopback** của EC2, không mở ra Internet. Truy cập
bằng SSH tunnel:

```bash
ssh -L 9999:127.0.0.1:9999 <user>@<ec2-host>
```

Giữ terminal đó mở, rồi mở trình duyệt: <http://localhost:9999>

Không cần mật khẩu — chỉ người có SSH key mới vào được. Đây là đánh đổi có chủ
đích: an toàn hơn hẳn việc mở cổng công khai, đổi lại phải mở tunnel mỗi lần xem.

## Vì sao không mở thẳng ra Internet

Dozzle được mount `/var/run/docker.sock`. Kể cả gắn `:ro`, quyền đọc socket cho
phép liệt kê **mọi container cùng toàn bộ biến môi trường** — tức `DB_PASSWORD`,
`JWT_SECRET`, `PAYOS_CHECKSUM_KEY` trong `/opt/deploy/.env` đều đọc được. Log ứng
dụng còn thường chứa email người dùng và câu truy vấn.

Nếu vẫn muốn truy cập qua trình duyệt mà không cần tunnel, xem khối hướng dẫn bật
`DOZZLE_AUTH_PROVIDER: simple` trong [`docker-compose.dozzle.yml`](docker-compose.dozzle.yml).
**Bật auth trước, rồi mới bỏ tiền tố `127.0.0.1`** — không làm ngược lại.

## Cách hoạt động

`/opt/deploy/docker-compose.yml` nằm trên EC2 và không được version control. Thay
vì sửa tay file đó, [`docker-compose.dozzle.yml`](docker-compose.dozzle.yml) là
một **overlay** được gộp lên trên khi deploy:

```bash
docker compose \
  -f /opt/deploy/docker-compose.yml \
  -f deploy/docker-compose.dozzle.yml \
  --env-file /opt/deploy/.env \
  up -d --build backend dozzle
```

Compose gộp theo khoá, nên mục `backend:` trong overlay (chỉ khai `logging:`)
được **cộng thêm** vào định nghĩa backend sẵn có chứ không thay thế nó.

> Thứ tự `-f` không được đổi. Tên project được suy ra từ thư mục của file đầu
> tiên (`/opt/deploy` → project `deploy`). Đảo thứ tự sẽ đổi tên project, khiến
> compose coi các container đang chạy là của project khác và bỏ rơi chúng.

Chạy riêng file overlay sẽ báo lỗi `service backend has neither an image nor a
build context` — đúng như thiết kế, vì nó là mảnh ghép chứ không phải file hoàn chỉnh.

## Xoay vòng log

Overlay đặt giới hạn 20 MB × 5 file = **tối đa 100 MB log gần nhất** mỗi container.

Docker mặc định ghi log JSON không giới hạn, đủ sức ăn hết ổ đĩa và làm sập EC2.
Rủi ro này *tăng lên* khi có Dozzle: log trở nên "vô hình" vì bạn xem qua web UI
và không còn để ý file đang phình to dưới `/var/lib/docker/containers`.

Giới hạn chỉ có hiệu lực khi container được **tạo lại**, không phải restart. Lần
deploy đầu tiên sau khi merge thay đổi này sẽ tự làm việc đó.

## Giới hạn cần biết

Dozzle chỉ hiển thị những gì Docker còn giữ. Sau khi bật xoay vòng, log cũ hơn
100 MB **bị xoá vĩnh viễn** và Dozzle không lưu trữ thêm gì.

Nó là công cụ *xem log đang chạy*, không phải hệ thống lưu trữ và truy vấn lịch
sử. Cần tra cứu log tuần trước, đặt cảnh báo, hay gộp log nhiều máy thì đó là
việc của Loki hoặc CloudWatch.

## Kiểm tra sau khi deploy

```bash
docker compose -f /opt/deploy/docker-compose.yml ps dozzle
docker logs dozzle --tail 20
```

Cổng phải hiện đúng `127.0.0.1:9999->8080/tcp`. Nếu thấy `0.0.0.0:9999->8080/tcp`
thì Dozzle đang mở ra Internet — dừng ngay và kiểm tra lại phần `ports` của overlay.
