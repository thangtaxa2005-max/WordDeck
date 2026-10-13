# Kiểm tra WordDeck — 13/10/2026

## Kết quả

- APK debug được biên dịch thành công cho Android 8.0+.
- 7/7 kiểm thử JVM đạt: lịch ôn khi quên, tăng khoảng cách SM-2, giới hạn độ dễ, CSV có dấu phẩy/xuống dòng/ngoặc kép, TXT, CSV lỗi và chuẩn hóa câu trả lời.
- 7/7 kiểm thử Android đạt trên Medium Phone, Android 12 (API 31): tạo thư mục/học phần/thẻ, nhập và loại trùng, lưu lịch ôn và nhật ký, các dạng luyện tập, Write và kết quả, ghép cặp/kỷ lục, sao lưu/khôi phục và từ chối liên kết lỗi.
- Android lint đạt, không có lỗi chặn. Còn các khuyến nghị về phiên bản thư viện/target SDK và cách dùng Kotlin extensions.
- Ảnh màn hình thật trong `screenshots/files/`; dữ liệu trong ảnh là dữ liệu kiểm thử.
- Nhật ký 7 kiểm thử Android: `android-tests.txt`.

## Phần cần xác nhận trên thiết bị thực

- Chưa chạy trên điện thoại vật lý của người dùng hoặc nhiều phiên bản Android.
- Chưa xác nhận tải mô hình ML Kit, chất lượng dịch, âm thanh TTS và thời điểm gửi thông báo trên điện thoại thật. Các luồng đã được triển khai, nhưng phụ thuộc kết nối, giọng hệ thống, quyền và tối ưu pin.
- Chưa phát hành Google Play; APK bàn giao dùng chữ ký debug để cài thử.

Nguồn, cách build, hành vi chức năng và giới hạn chi tiết nằm trong `../README.md`.
