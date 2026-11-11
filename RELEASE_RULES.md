# QUY TẮC PHÁT HÀNH & PHÁT TRIỂN DỰ ÁN (RELEASE & DEVELOPMENT RULES)

> [!IMPORTANT]
> **QUY TẮC BẮT BUỘC CHO MỌI AGENT VÀ DEVELOPER THAM GIA DỰ ÁN NÀY**

---

### 1. QUY TẮC PHÁT HÀNH BẢN MỚI (STRICT NO-AUTO-RELEASE POLICY)
- **TUYỆT ĐỐI KHÔNG TỰ ĐỘNG PHÁT HÀNH:** Không bao giờ tự ý tạo git tag mới (ví dụ `v1.0.2`, `v1.0.3`...) và không push tag lên GitHub khi người dùng (Olivia) chưa ra lệnh rõ ràng.
- **CHỈ PHÁT HÀNH KHI CÓ LỆNH EXPLICIT:** Chỉ khi người dùng yêu cầu: *"phát hành bản mới"*, *"tạo release"*, *"up bản v1.0.x"*... thì mới thực hiện quy trình release.
- **TEST CỤC BỘ QUA ADB TRƯỚC:** Các bản sửa lỗi, tính năng mới hoặc tối ưu chỉ được build release cục bộ (`assembleGithubRelease`) và nạp thẳng vào thiết bị test của người dùng qua ADB (Streamed Install qua Wi-Fi ADB).
- **CHỜ NGƯỜI DÙNG DUYỆT:** Sau khi nạp vào máy, để người dùng test thực tế. Người dùng hài lòng và ra lệnh phát hành thì mới bump `BuildInfo.java` (`VERSION`, `VERSION_CODE`), commit, tạo tag và push.

---

### 2. TÊN DỰ ÁN & ASSETS SẠCH
- **LOẠI BỎ TOÀN BỘ TÊN CŨ:** Dự án chính thức là **NN Agent** (`app.nn.agent`). Tuyệt đối không để sót tên cũ `buddy` trong file APK phát hành.
- **FILE PHÁT HÀNH CHUẨN:** Chỉ có 2 asset chính thức trên GitHub Release:
  1. `NN_AGENT.apk` (Bản có tự động cập nhật)
  2. `nn-agent-fdroid.apk` (Bản kiểm tra tương thích F-Droid)

---

### 3. KEYSTORE & CHỮ KÝ SỐ (SIGNING KEY)
- **LUÔN DÙNG KEYSTORE CHÍNH THỨC:** `keystore/nn.jks` (alias `nn`, pass `nnagent2026`).
- Tuyệt đối không dùng debug key hoặc keystore ngẫu nhiên để tránh lỗi lệch chữ ký khiến người dùng cũ không cài đè cập nhật được.

---

### 4. TỐI ƯU HÓA HIỆU NĂNG & MƯỢT MÀ (120HZ & SCROLL LOCK)
- **Mở khóa 120Hz:** Mọi Activity (`MainActivity`, `ChatActivity`, `SetupActivity`, `MonitorActivity`) và Bong bóng nổi (`Bubble`) phải gọi `Ui.unlockHighRefreshRate()` để kích hoạt tốc độ làm tươi cao nhất của màn hình thiết bị (90Hz / 120Hz / 144Hz).
- **Cache kiểm tra tài khoản AI:** Tránh gọi lệnh `Linux.exec()` liên tục khi đổi tab; giữ cache 60s (`REFRESH_TTL_MS = 60_000`) trừ khi người dùng chủ động thao tác.
- **Debounce Render:** Luôn gom các sự kiện giao diện trong 100ms thành 1 lần render duy nhất (`scheduleRender()`).
- **Khóa View khi cuộn:** Trong `SetupActivity`, khi ngón tay đang chạm hoặc màn hình đang cuộn quán tính (`isUserScrolling == true`), tuyệt đối hoãn việc xóa/vẽ lại View (`list.removeAllViews()`) cho đến khi màn hình dừng cuộn hẳn để tránh đơ/giật lag.
