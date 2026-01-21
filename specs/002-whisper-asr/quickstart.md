# Quickstart

## 前置條件

### 模型檔取得與放置

#### 模型來源

**官方來源**：[RKNN Model Zoo - Whisper](https://github.com/airockchip/rknn_model_zoo/tree/main/examples/whisper)

**取得方式**（擇一）：

1. **選項 A**：從 RKNN Model Zoo 下載預轉換的 RKNN 模型
   ```bash
   git clone https://github.com/airockchip/rknn_model_zoo.git
   cd rknn_model_zoo/examples/whisper
   # 依照 README 指示下載或轉換模型
   ```

2. **選項 B**：使用 rknn-toolkit2 自行轉換 Whisper GGML 模型
   ```bash
   # 需要安裝 rknn-toolkit2
   pip install rknn-toolkit2
   # 依照官方文檔轉換 whisper.cpp GGML 模型至 RKNN 格式
   ```

#### 放置路徑

**開發時（SDK 內部）**：
- Assets 打包路徑：`meeting-engine/src/main/assets/models/*.rknn`
- 此路徑由 SDK 自動管理，使用者無需關注

**Runtime 路徑（自動處理）**：
- 自動複製目標：`context.filesDir.absolutePath + "/models/"`
- 由 `ModelAssetManager.ensureModels()` 自動處理，使用者無需手動複製
- C++ 層透過此路徑呼叫 `rknn_init()` 載入模型

**驗證**：檔案大小應 > 10MB，可被 `rknn_init()` 正確載入

**SDK 自包含設計原則**：
- SDK 使用者只需呼叫 `prepare()`，無需維護 assets 結構
- 模型檔自動從 APK 解壓至 app-specific storage（不需額外權限）
- 首次執行會複製模型，後續執行自動跳過（檔案已存在）

### Native 依賴取得與放置

#### RKNN Runtime Library 來源

**官方來源**：[RKNN Toolkit2 Releases](https://github.com/airockchip/rknn-toolkit2/releases)

**版本要求**：`librknnrt.so` >= 1.6.0（對應 RK3588 NPU driver）

#### 放置路徑

1. 將 `librknnrt.so` 放入 `meeting-engine/src/main/jniLibs/arm64-v8a/`
2. 確認版本與模型相容

**驗證**：
```bash
file meeting-engine/src/main/jniLibs/arm64-v8a/librknnrt.so
# 應顯示：ELF 64-bit LSB shared object, ARM aarch64
```

### 版本相容性矩陣

| RKNN SDK 版本 | RK3588 BSP 版本 | Whisper 模型格式 | 支援狀態 |
|--------------|----------------|-----------------|---------|
| >= 1.6.0     | >= 5.10        | RKNN (base)     | ✓ 建議  |
| 1.5.x        | >= 5.10        | RKNN (base)     | ⚠ 部分支援 |
| < 1.5.0      | -              | -               | ✗ 不支援 |

**注意事項**：
- 模型檔與 RKNN Runtime 版本必須匹配
- RK3588 NPU driver 版本需與 BSP 對應
- 建議使用最新的穩定版本以獲得最佳效能

## 基本流程

1. 建立 `MeetingSession` 並設定語言模式（auto 或指定語言）
2. 呼叫 `prepare()` 載入模型
3. 呼叫 `start()` 開始即時轉錄
4. 透過 `transcriptFlow` 觀察逐段文字輸出
5. 呼叫 `stop()` 停止轉錄並釋放資源

## 錯誤處理

- 模型檔缺失或版本不相容時，應回傳錯誤碼 + 可讀訊息
