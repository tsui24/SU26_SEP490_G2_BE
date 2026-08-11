#!/usr/bin/env bash
#
# Sao lưu database MySQL đang chạy trong Docker.
#
# Khi còn dùng RDS, AWS lo việc này bằng automated snapshot. Tự chạy MySQL trong
# Docker là tự nhận trách nhiệm đó — không có script này thì một lệnh
# `docker compose down -v` gõ nhầm là mất toàn bộ dữ liệu, không đường lùi.
#
# CÀI ĐẶT (chạy một lần trên EC2):
#     sudo mkdir -p /opt/deploy/backups
#     sudo chown $USER:$USER /opt/deploy/backups
#     chmod +x /opt/deploy/scripts/backup-mysql.sh
#     crontab -e
#     # chạy 3h sáng mỗi ngày:
#     0 3 * * * /opt/deploy/scripts/backup-mysql.sh >> /opt/deploy/backups/backup.log 2>&1
#
# KHÔI PHỤC:
#     gunzip -c /opt/deploy/backups/btms-20260811-030000.sql.gz \
#       | docker exec -i deploy-mysql-1 mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"

set -euo pipefail

ENV_FILE="${ENV_FILE:-/opt/deploy/.env}"
BACKUP_DIR="${BACKUP_DIR:-/opt/deploy/backups}"
CONTAINER="${CONTAINER:-deploy-mysql-1}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

# Đọc .env mà không làm rò biến ra ngoài phạm vi script.
# set -a để mọi biến gán vào đều được export cho lệnh con.
if [[ ! -r "$ENV_FILE" ]]; then
	echo "[$(date -Is)] LỖI: không đọc được $ENV_FILE" >&2
	exit 1
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${MYSQL_ROOT_PASSWORD:?thiếu MYSQL_ROOT_PASSWORD trong $ENV_FILE}"
: "${MYSQL_DATABASE:?thiếu MYSQL_DATABASE trong $ENV_FILE}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/btms-$STAMP.sql.gz"

echo "[$(date -Is)] Bắt đầu backup $MYSQL_DATABASE → $OUT"

# --single-transaction: dump nhất quán mà không khoá bảng (InnoDB).
# --routines --triggers --events: lấy cả stored procedure/trigger, mặc định bị bỏ.
# Mật khẩu truyền qua biến môi trường MYSQL_PWD chứ không phải tham số dòng lệnh,
# để nó không hiện trong `ps aux` của các tiến trình khác.
if ! docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTAINER" \
	mysqldump -u root \
	--single-transaction --routines --triggers --events \
	--default-character-set=utf8mb4 \
	"$MYSQL_DATABASE" 2>"$BACKUP_DIR/.err-$STAMP" | gzip -9 > "$OUT"; then
	echo "[$(date -Is)] LỖI: mysqldump thất bại" >&2
	cat "$BACKUP_DIR/.err-$STAMP" >&2
	rm -f "$OUT" "$BACKUP_DIR/.err-$STAMP"
	exit 1
fi
rm -f "$BACKUP_DIR/.err-$STAMP"

# File gzip rỗng vẫn ~20 byte. Dump thật của dự án này cỡ vài trăm KB.
# Kiểm tra để không âm thầm giữ lại một file backup vô dụng.
SIZE=$(stat -c%s "$OUT")
if (( SIZE < 1024 )); then
	echo "[$(date -Is)] LỖI: file backup chỉ $SIZE byte — nhiều khả năng dump rỗng" >&2
	rm -f "$OUT"
	exit 1
fi

# Kiểm tra file gzip toàn vẹn — phát hiện sớm trường hợp đĩa đầy giữa chừng.
if ! gzip -t "$OUT"; then
	echo "[$(date -Is)] LỖI: file gzip hỏng" >&2
	rm -f "$OUT"
	exit 1
fi

echo "[$(date -Is)] Xong: $OUT ($(numfmt --to=iec "$SIZE"))"

# Xoá bản cũ hơn RETENTION_DAYS ngày.
DELETED=$(find "$BACKUP_DIR" -name 'btms-*.sql.gz' -mtime "+$RETENTION_DAYS" -print -delete | wc -l)
if (( DELETED > 0 )); then
	echo "[$(date -Is)] Đã xoá $DELETED bản backup cũ hơn $RETENTION_DAYS ngày"
fi

# ── Khuyến nghị: đẩy ra ngoài máy ────────────────────────────────────────────
# Backup nằm cùng ổ đĩa với database thì không cứu được khi EC2 hỏng hoặc bị xoá.
# Bỏ chú thích sau khi tạo bucket và gắn IAM role cho EC2:
#
# aws s3 cp "$OUT" "s3://<bucket>/btms/" --storage-class STANDARD_IA
