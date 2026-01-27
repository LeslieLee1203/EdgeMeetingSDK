# Hotfix: Local Agreement 策略調整

## 🐛 問題描述

**症狀**: Production 版實現後，Sliding Window、推論、去重都正常工作，但 UI 上幾乎沒有文字輸出（只有 1 個空格）。

**日誌證據** (`log_002.txt`):
```
Line 105: Decoder Result: ' On is the insert at cursor button...'  ✅ 推論正常
Line 125: Decoder Result: ' We have the copy button...'            ✅ 推論正常
Line 138: removePrefixOverlap: Found overlap of 68 chars          ✅ 去重正常

但是：
Line 106: LocalAgreement: No new stable content to commit  ❌
Line 114: LocalAgreement: No new stable content to commit  ❌
Line 127: LocalAgreement: Committing 1 new chars: ' '      ❌ 只提交了 1 個空格！
Line 141: LocalAgreement: No new stable content to commit  ❌
```

**UI 表現**: 只顯示 `[00:00:12-00:00:20]` 和一個空格。

## 🔍 根本原因

### Local Agreement 原理回顧

**原始策略** (whisper-streaming-optimization-plan.md:204-242):
- 計算連續兩輪輸出的最長公共前綴 (LCP)
- 只提交已經連續兩輪都一致的穩定部分
- 目的：避免字幕抖動

**問題場景**:
```
第 1 輪: ' On is the insert at cursor button...'
第 2 輪: ''  (空字串)
第 3 輪: ' We have the copy button...'
第 4 輪: ' We have the copy button...'  (完全相同)
第 5 輪: ' [BLANK_AUDIO]'
第 6 輪: ' If you have dropped down...'
```

- 每輪內容**完全不同**（測試音訊是快速變化的視頻教學）
- 沒有穩定的公共前綴
- LCP 分析：
  - 第 1-2 輪：公共前綴 = 0
  - 第 2-3 輪：公共前綴 = 0
  - 第 3-4 輪：公共前綴 = 68 chars（完全相同）→ 但去重後為空
  - 第 4-5 輪：公共前綴 = 1 char (只有空格 `' '`)
  - 結果：**只提交了 1 個空格**

### 為什麼原始策略太嚴格？

**原始假設**: 用戶在說連續的句子，每輪推論會識別「當前句子 + 下一句的開頭」，因此有穩定的重疊前綴。

**實際情況**:
1. **快速變化的內容**（視頻、廣播、快速對話）
2. **靜音間隔**導致句子斷開
3. **語言切換**（英文 ↔ 中文）
4. **Whisper 的不確定性**（同一段音訊兩次推論可能不同）

→ 導致公共前綴極短，永遠無法提交。

## ✅ 修正方案

### 新策略：平衡版 Local Agreement

**核心理念**: 在穩定性與及時性之間取得平衡。

#### 策略 A：優先使用 LCP（保持穩定性）
```cpp
const size_t MIN_STABLE_PREFIX = 10;  // 最少 10 字元才算穩定

if (common_len >= MIN_STABLE_PREFIX) {
    // 使用標準 Local Agreement
    impl_->stable_prefix = hypothesis.substr(0, common_len);
}
```

**適用場景**: 連續語音、重複內容（原始設計目標）

#### 策略 B：強制提交機制（避免卡住）
```cpp
const size_t MIN_FORCE_COMMIT = 5;

if (hypothesis.length() > MIN_FORCE_COMMIT &&
    !contains_hallucination_marker(hypothesis)) {
    // 強制提交當前內容
    to_commit = hypothesis.substr(impl_->committed_length);
}
```

**適用場景**:
- 內容變化快
- 沒有穩定的 LCP
- 但有實際的語音內容

**幻覺過濾**:
- 排除 `[BLANK_AUDIO]`
- 排除純空白
- 排除長度 < 5 的片段

### 修正後的行為

**同樣的測試場景**:
```
第 1 輪: ' On is the insert at cursor button...'
  → LCP=0，但長度 > 5 → 強制提交 ✅

第 2 輪: ''  (空字串)
  → 跳過 ✅

第 3 輪: ' We have the copy button...'
  → LCP=0，但長度 > 5 → 強制提交 ✅

第 4 輪: ' We have the copy button...'  (去重後為空)
  → 去重移除，無新內容 ✅

第 5 輪: ' [BLANK_AUDIO]'
  → 幻覺標記，過濾 ✅

第 6 輪: ' If you have dropped down...'
  → LCP=0，但長度 > 5 → 強制提交 ✅
```

**結果**: 所有有效內容都被提交，UI 正常顯示完整轉錄文字。

## 📊 策略對比

| 場景 | 原始策略（純 LCP） | 平衡策略 |
|------|-------------------|---------|
| 連續語音（重複前綴） | ✅ 穩定提交 | ✅ 穩定提交 |
| 快速變化內容 | ❌ 卡住不提交 | ✅ 強制提交 |
| 輸出抖動 | ✅ 完美過濾 | ⚠️ 部分過濾（MIN_STABLE_PREFIX） |
| 幻覺標記 | ⚠️ 可能提交 | ✅ 過濾 |
| 空字串/空白 | ⚠️ 可能提交 | ✅ 過濾 |

## 🧪 重新測試

### 1. 重新編譯

```bash
./gradlew clean
./gradlew :meeting-engine:build
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 監控日誌

```bash
adb logcat -c
adb logcat | grep -E "LocalAgreement|Decoder Result"
```

### 3. 預期結果

**修正前** (log_002.txt):
```
Decoder Result: ' On is the insert at cursor button...'
LocalAgreement: No new stable content to commit  ❌

Decoder Result: ' We have the copy button...'
LocalAgreement: No new stable content to commit  ❌
```

**修正後**:
```
Decoder Result: ' On is the insert at cursor button...'
LocalAgreement: Force committing 56 chars (content-based): ' On is...'  ✅

Decoder Result: ' We have the copy button...'
LocalAgreement: Force committing 68 chars (content-based): ' We have...'  ✅
```

**UI 顯示**:
```
[00:00:08-00:00:12] On is the insert at cursor button, which allows you to insert the code block at the current position of the cursor.
[00:00:12-00:00:20] We have the copy button that allows you to copy the generated code.
[00:00:20-00:00:28] If you have dropped down a menu that allows you to run the code directly...
```

## 🎯 調優參數

如果需要進一步調整：

### 參數 1: MIN_STABLE_PREFIX
```cpp
const size_t MIN_STABLE_PREFIX = 10;  // 當前值
```

**調整場景**:
- **提高至 15-20**: 更嚴格的穩定性要求，減少抖動
- **降低至 5-8**: 更寬鬆，適合短句對話

### 參數 2: MIN_FORCE_COMMIT
```cpp
const size_t MIN_FORCE_COMMIT = 5;  // 當前值
```

**調整場景**:
- **提高至 10**: 過濾更多短片段（如 "OK", "Yeah"）
- **降低至 3**: 保留更多短句

### 參數 3: 幻覺過濾
```cpp
// 當前只過濾 [BLANK_AUDIO]
// 可擴展：
if (trimmed.find("Thank you") != std::string::npos ||
    trimmed.find("字幕版權") != std::string::npos) {
    should_force_commit = false;  // 過濾常見幻覺
}
```

## 📝 總結

**修正內容**:
1. ✅ 保留 LCP 策略（MIN_STABLE_PREFIX >= 10）
2. ✅ 新增強制提交機制（MIN_FORCE_COMMIT >= 5）
3. ✅ 新增幻覺過濾（[BLANK_AUDIO]、空白）
4. ✅ 新增內容有效性檢查

**預期效果**:
- 連續語音：穩定提交（與原策略相同）
- 快速變化：強制提交（解決卡住問題）
- 幻覺/空白：過濾（提高準確性）

**修正文件**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp:660-745`

---

**修正日期**: 2026-01-26
**問題來源**: log_002.txt 分析
**相關文檔**: HOTFIX-ENERGY-THRESHOLD.md
