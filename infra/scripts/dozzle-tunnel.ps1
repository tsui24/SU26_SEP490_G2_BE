# Mở SSH tunnel tới Dozzle rồi bật trình duyệt.
#
# Dozzle bind 127.0.0.1:9999 trên EC2 nên Internet không thấy — bắt buộc đi qua
# tunnel. Đây là chủ ý: Dozzle mount /var/run/docker.sock, ai vào được giao diện
# là đọc được toàn bộ biến môi trường của mọi container (MYSQL_APP_PASSWORD,
# JWT_SECRET, PAYOS_CHECKSUM_KEY...).
#
# Cách dùng:
#     .\dozzle-tunnel.ps1
#     .\dozzle-tunnel.ps1 -LocalPort 9998        # nếu 9999 đã bị chiếm
#
# Đóng cửa sổ hoặc Ctrl+C để ngắt tunnel.

param(
	[string]$KeyPath   = "D:\sep490-g2.pem",
	[string]$SshUser   = "ubuntu",
	[string]$SshHost   = "18.138.158.94",
	[int]   $LocalPort = 9999,
	[int]   $RemotePort = 9999
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $KeyPath)) {
	Write-Host "Không tìm thấy SSH key: $KeyPath" -ForegroundColor Red
	Write-Host "Chạy lại với: .\dozzle-tunnel.ps1 -KeyPath 'D:\duong\dan\key.pem'"
	exit 1
}

# Cổng đang bị chiếm thì ssh sẽ báo lỗi khó hiểu — kiểm tra trước cho rõ ràng.
$busy = Get-NetTCPConnection -LocalPort $LocalPort -State Listen -ErrorAction SilentlyContinue
if ($busy) {
	Write-Host "Cổng $LocalPort đang bị chiếm." -ForegroundColor Yellow
	Write-Host "Dùng cổng khác: .\dozzle-tunnel.ps1 -LocalPort 9998"
	exit 1
}

$url = "http://localhost:$LocalPort"
Write-Host ""
Write-Host "  Đang mở tunnel tới Dozzle..." -ForegroundColor Cyan
Write-Host "  $SshUser@$SshHost  ->  127.0.0.1:$RemotePort"
Write-Host ""
Write-Host "  Trình duyệt sẽ tự mở: $url" -ForegroundColor Green
Write-Host "  GIỮ CỬA SỔ NÀY MỞ. Đóng là tunnel đứt và trang chết." -ForegroundColor Yellow
Write-Host "  Cửa sổ sẽ im lặng không in gì — đó là bình thường."
Write-Host ""

# Mở trình duyệt sau vài giây để tunnel kịp thiết lập.
Start-Job -ScriptBlock { Start-Sleep -Seconds 3; Start-Process $using:url } | Out-Null

# -N          : chỉ forward cổng, không mở shell
# ServerAlive : giữ nhịp 30s, tránh bị ngắt do idle — nguyên nhân phổ biến nhất
#               khiến trang Dozzle đang xem tự nhiên chết
ssh -i $KeyPath -N `
	-o ServerAliveInterval=30 `
	-o ServerAliveCountMax=3 `
	-L "${LocalPort}:127.0.0.1:${RemotePort}" `
	"$SshUser@$SshHost"
