# Hotfix: committed_length 追蹤邏輯錯誤

## 🐛 問題描述

**症狀**: 第三次測試 (log_003.txt) 顯示，第一次推論正常輸出，但之後的推論都無法提交，導致大部分語音沒有轉出來。

**日誌證據**:
```
Line 42: Decoder Result: ' We can now accept this card...' (88 chars)
Line 43: LocalAgreement: Force committing 88 chars ✅ 第一次成功

Line 56: Decoder Result: ' Let's accept these changes.' (28 chars)
Line 57: LocalAgreement: No new content (commonLen=1, hypothesisLen=28) ❌ 失敗

Line 67: Decoder Result: ' Let's accept these changes and see...' (49 chars)
Line 68: LocalAgreement: No new content (commonLen=27, hypothesisLen=49) ❌ 失敗

Line 79: Decoder Result: '... It looks like Copilot' (22 chars after dedup)
Line 83: LocalAgreement: No new content (commonLen=1, hypothesisLen=22) ❌ 失敗
```

**UI 結果**: 只顯示 3 個片段，大量內容丟失。

## 🔍 根本原因

### committed_length 的語義混淆

**當前邏輯流程**:
```cpp
// 第一輪推論
raw_text = "We can now accept this card or show..."  // 88 chars
deduped = removePrefixOverlap(last_output="", raw_text)
// deduped = "We can now accept..." (88 chars, 無重疊)

applyLocalAgreement(deduped):
  hypothesis.length() = 88
  committed_length = 0
  → 88 > 5 → should_force_commit = true
  → 88 > 0 → 強制提交 ✅
  → committed_length = 88

// 第二輪推論
raw_text = "Let's accept these changes."  // 28 chars
deduped = removePrefixOverlap(last_output="We can...", raw_text)
// deduped = "Let's accept..." (28 chars, 與上一輪無重疊)

applyLocalAgreement(deduped):
  hypothesis.length() = 28
  committed_length = 88 (上一輪的累積值)
  → 28 > 5 → should_force_commit = true
  → 但檢查: 28 > 88? FALSE ❌
  → 無法提交！
```

### 錯誤的假設

**代碼假設**:
```cpp
if (should_force_commit && hypothesis.length() > impl_->committed_length) {
    // 假設 hypothesis 是累積增長的
    to_commit = hypothesis.substr(impl_->committed_length);
}
```

**實際情況**:
- `hypothesis` 是**去重後的新內容**，每輪長度獨立
- `committed_length` 追蹤的是**全局累積長度**
- 當去重後內容長度 < committed_length 時，條件永遠失敗

### 為什麼第一次成功，之後失敗？

**第一次**:
- committed_length = 0 (初始值)
- hypothesis.length() = 88
- 88 > 0 → 成功 ✅

**第二次及之後**:
- committed_length = 88 (第一次設定的)
- hypothesis.length() = 28, 49, 22... (去重後的新內容)
- 所有都 < 88 → 全部失敗 ❌

## ✅ 修正方案

### 核心修正

#### 1. 檢測內容大幅變化
```cpp
const size_t MIN_STABLE_PREFIX = 10;
bool content_changed = (common_len < MIN_STABLE_PREFIX);

if (content_changed) {
    // 內容大幅變化，重置 committed_length
    impl_->committed_length = 0;
    impl_->stable_prefix.clear();
}
```

**理由**:
- LCP < 10 表示前後兩輪內容幾乎不同
- 此時 `committed_length` 應該重置，因為追蹤的 `prev_hypothesis` 已經完全改變

#### 2. 改進強制提交邏輯
```cpp
else if (should_force_commit) {
    // 修正前：單純檢查 hypothesis.length() > committed_length
    // 修正後：處理內容變短的情況
    if (hypothesis.length() > impl_->committed_length) {
        to_commit = hypothesis.substr(impl_->committed_length);
    } else {
        // 內容變短，直接提交完整內容
        to_commit = hypothesis;
    }

    impl_->committed_length = hypothesis.length();
}
```

**理由**:
- 去重後內容可能變短
- 不應該因為長度檢查失敗而不提交
- 關鍵是內容有效性（> 5 字元，非幻覺）

#### 3. 增強幻覺過濾
```cpp
// 排除幻覺標記
if (!trimmed.empty() &&
    trimmed.find("[BLANK_AUDIO]") == std::string::npos &&
    trimmed.find("[Music]") == std::string::npos &&  // ← 新增
    trimmed.length() > MIN_FORCE_COMMIT) {
    should_force_commit = true;
}
```

**理由**: log_003.txt 中出現 `[Music]` 標記（Line 138, 151），應過濾。

#### 4. 增強 Debug Log
```cpp
LOGD("LocalAgreement: No new content to commit (commonLen=%zu, hypothesisLen=%zu, shouldForce=%d, committed=%zu)",
     common_len, hypothesis.length(), should_force_commit, impl_->committed_length);
```

**理由**: 提供更多診斷資訊，方便後續問題排查。

### 修正後的邏輯流程

**第一輪**:
```
hypothesis = "We can now accept..." (88 chars)
committed_length = 0 (初始值)
LCP = 0 → content_changed = true → reset committed_length = 0
should_force_commit = true
88 > 0 → 提交 88 chars ✅
committed_length = 88
```

**第二輪**:
```
hypothesis = "Let's accept..." (28 chars)
committed_length = 88
LCP = 1 → content_changed = true → reset committed_length = 0  ← 關鍵修正
should_force_commit = true
28 > 0 → 提交 28 chars ✅
committed_length = 28
```

**第三輪**:
```
hypothesis = "Let's accept these changes and see..." (49 chars)
committed_length = 28
LCP = 27 → content_changed = false (LCP >= 10)
should_force_commit = true
49 > 28 → 提交 49-28=21 chars ✅
committed_length = 49
```

## 📊 修正前後對比

| 推論輪次 | 去重後長度 | 修正前 committed_length | 修正後 committed_length | 修正前結果 | 修正後結果 |
|---------|-----------|----------------------|----------------------|-----------|-----------|
| 第 1 輪 | 88 | 0 | 0 | ✅ 提交 88 | ✅ 提交 88 |
| 第 2 輪 | 28 | 88 | 0 (重置) | ❌ 28 < 88 失敗 | ✅ 提交 28 |
| 第 3 輪 | 49 | 88 | 28 | ❌ 49 < 88 失敗 | ✅ 提交 21 |
| 第 4 輪 | 22 | 88 | 0 (重置) | ❌ 22 < 88 失敗 | ✅ 提交 22 |

## 🧪 重新測試

### 1. 重新編譯
```bash
./gradlew clean
./gradlew :meeting-engine:build
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 監控關鍵日誌
```bash
adb logcat -c
adb logcat | grep -E "LocalAgreement|Decoder Result|Content changed"
```

### 3. 預期日誌

**修正前** (log_003.txt):
```
Decoder Result: ' We can now accept...' (88 chars)
LocalAgreement: Force committing 88 chars ✅

Decoder Result: ' Let's accept...' (28 chars)
LocalAgreement: No new content (commonLen=1, hypothesisLen=28) ❌

Decoder Result: ' Let's accept these changes and see...' (49 chars)
LocalAgreement: No new content (commonLen=27, hypothesisLen=49) ❌
```

**修正後**:
```
Decoder Result: ' We can now accept...' (88 chars)
LocalAgreement: Content changed (LCP=0), resetting
LocalAgreement: Force committing 88 chars ✅

Decoder Result: ' Let's accept...' (28 chars)
LocalAgreement: Content changed (LCP=1), resetting  ← 關鍵
LocalAgreement: Force committing 28 chars ✅

Decoder Result: ' Let's accept these changes and see...' (49 chars)
LocalAgreement: Stable prefix extended to 27 chars
LocalAgreement: Force committing 22 chars ✅  (49-27)
```

### 4. UI 驗證

**修正前** (log_003):
```
[00:00:00-00:00:08] We can now accept this card...
[00:00:12-00:00:20] cissors game that has too many
[00:00:35-00:00:43] f code.
```
**只有 3 個片段** ❌

**修正後**（預期）:
```
[00:00:00-00:00:08] We can now accept this card...
[00:00:08-00:00:12] Let's accept these changes.
[00:00:12-00:00:16] Let's accept these changes and see what we have.
[00:00:16-00:00:20] It looks like Copilot...
[00:00:20-00:00:24] ...
```
**所有推論都有輸出** ✅

## 📝 總結

**問題**: `committed_length` 追蹤的是全局累積長度，但 `hypothesis` 是去重後的新內容，兩者語義不一致。

**修正**:
1. ✅ 檢測內容大幅變化（LCP < 10）時重置 `committed_length`
2. ✅ 改進強制提交邏輯，處理內容變短的情況
3. ✅ 增加 `[Music]` 幻覺過濾
4. ✅ 增強 debug log

**預期效果**: 所有有效的推論結果都能正確提交到 UI。

---

**修正日期**: 2026-01-26
**修正文件**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp:660-762`
**問題來源**: log_003.txt 分析
**相關修正**: HOTFIX-ENERGY-THRESHOLD.md, HOTFIX-LOCAL-AGREEMENT.md
