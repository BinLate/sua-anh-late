# Sửa Ảnh (Android)

Ứng dụng Android chỉnh sửa ảnh bằng **Kotlin + Jetpack Compose** (Material 3).
Không cần quyền lưu trữ — sử dụng **Android Photo Picker** để chọn ảnh và
**MediaStore** để lưu kết quả vào thư viện.

## Tính năng
- ✏️ **Bút vẽ** – vẽ tự do, chọn màu + độ dày nét bút.
- 🖍️ **Bút Highlight** – vẽ bán trong suốt (dạ quang), chọn màu + độ dày ngòi.
- 🔤 **Chữ** – gõ chữ lên ảnh, kéo thả di chuyển, chọn màu + cỡ chữ.
- 🟨 **Che** – 3 kiểu: **Làm mờ (blur)**, **Mosaic (pixelate)**, **Khối màu đặc (solid)**;
  kéo chuột tạo vùng hình chữ nhật để che.
- 🎨 **Chọn màu** – dải màu nhanh + trình chọn màu tùy chỉnh (Hue/Saturation/Value).
- ↩️ **Hoàn tác / Làm lại** (Undo/Redo).
- 💾 **Lưu** ảnh vào thư viện + **Chia sẻ**.

## Kiến trúc
- `editor/model` – các lớp layer (Stroke, Text, Cover) với tọa độ **chuẩn hóa (0..1)**,
  giúp preview và xuất ảnh ở độ phân giải gốc khớp nhau.
- `editor/EditorViewModel` – state + undo/redo + xuất ảnh.
- `editor/CanvasOverlay` – vẽ ảnh/th layer trên Compose Canvas + bắt cử chỉ.
- `editor/EditorProcessor` / `EditorRenderer` – xử lý bitmap để lưu ra file.
- `ui` – màn hình chính và các component (chọn màu, thêm chữ, thanh công cụ).

## Yêu cầu để build
- Android Studio (Iguana trở lên) hoặc JDK 17 + Android SDK 35.
- Mở thư mục gốc bằng Android Studio và nhấn **Run** (Android Studio sẽ tự
  khôi phục Gradle wrapper nếu thiếu).

## Cấu trúc chính
```
app/src/main/java/com/binlate/suaanh/
├── MainActivity.kt
├── editor/
│   ├── model/Models.kt
│   ├── EditorViewModel.kt
│   ├── EditorProcessor.kt
│   ├── EditorRenderer.kt
│   └── CanvasOverlay.kt
└── ui/
    ├── EditorScreen.kt
    └── components/ (Toolbars, ToolControls, ColorPickerDialog, TextContentDialog)
```