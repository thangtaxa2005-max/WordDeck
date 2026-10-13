# WordDeck

Ứng dụng Android học từ vựng tiếng Anh, giao diện tiếng Việt, xây dựng theo `mo ta.txt` và ảnh thiết kế đi kèm. Kotlin + Jetpack Compose, Room, WorkManager, Android TextToSpeech, Dictionary API và ML Kit Translate. Không cần tài khoản hoặc máy chủ riêng.

## Cài đặt

Mở `artifacts/WordDeck-1.0-debug.apk` trên điện thoại Android 8.0 trở lên và cho phép cài ứng dụng từ nguồn đã chọn. Đây là bản APK cài thử, ký bằng khóa debug; chưa phải bản phát hành Google Play.

Lần đầu ứng dụng có thư viện trống. Vào **Thư viện → + Thư mục**, mở thư mục, chọn **+ Học phần** rồi thêm thẻ hoặc **Dán / nhập file**. File `samples/ielts.csv` dùng để thử nhập CSV.

## Chức năng

- Tạo, sửa, xóa thư mục, học phần, thẻ; tìm kiếm học phần qua tên và nội dung từ vựng.
- Nhập TXT/CSV hoặc dán danh sách; xem trước, phát hiện và loại từ trùng, sửa từng thẻ hoặc sửa hàng loạt; không lưu khi thiếu nghĩa hoặc còn trùng.
- Tra phiên âm, loại từ và ví dụ qua dictionaryapi.dev; dịch Anh–Việt bằng ML Kit, luôn cho phép kiểm tra/chỉnh bản dịch trước khi lưu.
- Lật thẻ, phát âm, đảo chiều Anh–Việt, xáo trộn, gắn sao, lọc sao và ôn từ sai.
- Lịch ôn SM-2, đánh giá Quên/Khó/Nhớ/Dễ; hiển thị khoảng cách ôn theo tiến độ thật.
- Trắc nghiệm, điền từ, nghe rồi gõ, Write và đúng/sai; phản hồi ngay, lặp lại câu sai trong luyện tập.
- Kiểm tra chọn số câu, một hoặc nhiều dạng câu hỏi, giới hạn thời gian tùy chọn; kết quả gồm độ chính xác, tốc độ, thời gian và câu sai.
- Ghép cặp từ–nghĩa; đồng hồ, phạt 3 giây khi ghép sai và kỷ lục cho từng nhóm từ.
- Mục tiêu từ mới mỗi ngày, streak, số từ đã học/nhớ tốt, độ chính xác và biểu đồ 7 ngày.
- Nhắc học giờ tùy chọn, giọng Anh Mỹ/Anh Anh, chế độ tối.
- Sao lưu JSON gồm nội dung, lịch ôn, nhật ký, cài đặt và kỷ lục; xác nhận và kiểm tra liên kết trước khi thay thế dữ liệu.

## Quy ước và giới hạn

- CSV UTF-8: `word,meaning,phonetic,kind,example`. Có thể có dòng tiêu đề. Hỗ trợ dấu phẩy, xuống dòng và dấu ngoặc kép trong trường được đặt trong ngoặc kép.
- TXT: mỗi dòng `từ - nghĩa`, `từ<TAB>nghĩa` hoặc chỉ `từ`.
- Lần đầu dịch cần mạng để tải mô hình. Sau khi tải, mô hình có thể dịch offline; tra từ điển vẫn cần mạng. Nghĩa dịch và ví dụ không đảm bảo đúng mọi ngữ cảnh.
- Phát âm offline phụ thuộc giọng tiếng Anh đã cài trong Android. App báo khi thiết bị không có giọng phù hợp.
- Thông báo cần được cấp quyền trên Android 13+. WorkManager không bảo đảm đúng từng phút khi máy ngủ/tiết kiệm pin.
- Nếu học phần có ít hơn 4 nghĩa khác nhau, trắc nghiệm dùng số đáp án sẵn có và thông báo rõ.
- “Nhớ tốt” là thẻ có khoảng cách ôn ít nhất 6 ngày. “Từ sai” là thẻ từng có câu trả lời sai. Streak tính theo ngày địa phương, cho phép hôm nay chưa học nhưng hôm qua có học.
- SM-2 ánh xạ bốn mức sang chất lượng 1/3/4/5. Quên đặt lại số lần nhớ, lịch ôn về hôm nay; lần nhớ đầu là 1 ngày, lần hai là 6 ngày, các lần sau nhân hệ số độ dễ (tối thiểu 1.3).
- Dữ liệu đã lưu được giữ khi đóng ứng dụng. Buổi học đang dở không được khôi phục sau khi tiến trình bị Android kết thúc; các câu đã đánh giá vẫn được lưu.
- Không có đồng bộ đám mây, chia sẻ hay thư viện cộng đồng, đúng phạm vi tài liệu.

## Mở và biên dịch

Mở thư mục này bằng Android Studio, chọn JDK 17 trở lên (JBR đi kèm Studio dùng được), cài Android SDK 36 và đồng bộ Gradle. `local.properties` chứa đường dẫn SDK của máy hiện tại; đổi nếu dùng máy khác.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
# Cần máy giả lập hoặc thiết bị Android:
.\gradlew.bat :app:connectedDebugAndroidTest
```

APK gốc: `app/build/outputs/apk/debug/app-debug.apk`.

## Cấu trúc

- `Data.kt`: entity, DAO, Room, SM-2 và parser CSV/TXT.
- `DeckModel.kt`: thao tác dữ liệu, giao dịch, sao lưu/khôi phục.
- `MainActivity.kt`: trang chủ, thư viện, học phần, thống kê, cài đặt và biểu mẫu.
- `ImportScreen.kt`: nhập/xem trước/chỉnh sửa/tra tự động.
- `StudySession.kt`: học, luyện tập, kiểm tra, kết quả và ghép cặp.
- `Services.kt`: từ điển/dịch và nhắc học.
- `CoreTest.kt`, `AppTest.kt`: kiểm thử thuật toán và tích hợp Android.

Các bài kiểm thử Android tạo dữ liệu mẫu riêng trong máy giả lập. Không chạy bộ kiểm thử trên bản WordDeck đang chứa dữ liệu cá nhân cần giữ.
