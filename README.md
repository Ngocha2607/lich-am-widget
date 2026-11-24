# Lịch âm & Thời tiết — widget Android

Widget 4×2 cho màn hình chính Android, hiện cùng lúc:

- ngày dương, ngày âm (can chi, hoàng đạo/hắc đạo),
- thời tiết hiện tại theo thành phố,
- một dòng gợi ý hôm nay nên làm gì (mang ô, mặc ấm, mai là rằm, ngày tốt...).

Lịch âm tính offline theo thuật toán Hồ Ngọc Đức. Thời tiết lấy từ Open-Meteo (miễn phí, không cần key).

## Build APK bằng GitHub (không cần cài Android Studio)

1. Tạo repo mới trên GitHub (Public hoặc Private đều được).
2. Đẩy code lên:

   ```bash
   git remote add origin https://github.com/<tên-bạn>/lich-am-widget.git
   git push -u origin main
   ```

3. Vào tab **Actions** của repo, chờ job "Build APK" chạy xong (khoảng 5–8 phút lần đầu).
4. Vào tab **Releases**, tải file `LichThoiTiet.apk` về điện thoại rồi cài.
   Lần đầu ColorOS sẽ hỏi cho phép cài từ nguồn không xác định, bấm cho phép.
5. Giữ lâu trên màn hình chính → Widget → "Lịch âm & Thời tiết".

Mỗi lần sửa code và `git push`, GitHub tự build bản mới. Các bản sau cài đè được lên bản trước vì dùng chung khóa ký.

## Cấu trúc

| File | Vai trò |
|---|---|
| `LunarCalendar.kt` | Đổi dương → âm, can chi, hoàng đạo, giờ tốt, tiết khí |
| `Weather.kt` | Gọi Open-Meteo, lưu đệm, đổi mã thời tiết sang tiếng Việt |
| `SuggestionEngine.kt` | Bảng quy tắc sinh gợi ý theo thứ tự ưu tiên |
| `LichWidget.kt` | Widget và phần vẽ giao diện |
| `WeatherWorker.kt` | Cập nhật thời tiết ở nền (3 giờ/lần) |
| `MainActivity.kt` | Màn hình chọn thành phố, bật/tắt hoàng đạo, xem chi tiết |

## Lưu ý

- `app/lichthoitiet.keystore` là khóa ký dùng để thử nghiệm, mật khẩu `lichthoitiet`. Khi phát hành lên CH Play thì tạo khóa riêng và không đưa lên repo.
- ColorOS hay chặn ứng dụng chạy nền. Nếu widget không tự cập nhật, vào Cài đặt → Pin → chọn app → cho phép chạy nền.
