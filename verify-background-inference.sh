#!/bin/bash
# 後台推論線程實施驗證腳本
# 使用方式: ./verify-background-inference.sh

set -e

echo "======================================"
echo "後台推論線程實施驗證"
echo "======================================"
echo ""

# 1. 編譯驗證
echo "步驟 1/3: 編譯驗證..."
./gradlew :meeting-engine:build > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ 編譯成功"
else
    echo "❌ 編譯失敗"
    exit 1
fi
echo ""

# 2. 單元測試驗證
echo "步驟 2/3: 單元測試驗證..."
./gradlew :meeting-engine:test > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ 單元測試通過"
else
    echo "❌ 單元測試失敗"
    exit 1
fi
echo ""

# 3. 檢查關鍵代碼變更
echo "步驟 3/3: 檢查關鍵代碼變更..."

# 檢查推論線程函數是否存在
if grep -q "void inferenceThreadFunc" meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp; then
    echo "✅ 推論線程函數已添加"
else
    echo "❌ 推論線程函數未找到"
    exit 1
fi

# 檢查 Impl 結構體是否包含新成員
if grep -q "std::thread inferenceThread" meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp; then
    echo "✅ 推論線程成員已添加"
else
    echo "❌ 推論線程成員未找到"
    exit 1
fi

# 檢查 init() 是否啟動線程
if grep -q "impl_->inferenceThread = std::thread" meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp; then
    echo "✅ init() 中已啟動推論線程"
else
    echo "❌ init() 未啟動推論線程"
    exit 1
fi

# 檢查 release() 是否停止線程
if grep -q "impl_->inferenceThread.join()" meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp; then
    echo "✅ release() 中已停止推論線程"
else
    echo "❌ release() 未停止推論線程"
    exit 1
fi

# 檢查 pushAudio() 是否使用隊列
if grep -q "impl_->inferenceQueue.push" meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp; then
    echo "✅ pushAudio() 已改為隊列模式"
else
    echo "❌ pushAudio() 未使用隊列"
    exit 1
fi

# 檢查 header 是否有 friend 聲明
if grep -q "friend void inferenceThreadFunc" meeting-engine/src/main/cpp/asr/WhisperAsrEngine.h; then
    echo "✅ header 已添加 friend 聲明"
else
    echo "❌ header 未添加 friend 聲明"
    exit 1
fi

echo ""
echo "======================================"
echo "✅ 所有驗證通過！"
echo "======================================"
echo ""
echo "後續步驟:"
echo "1. 運行應用: ./gradlew :app:installDebug"
echo "2. 監控 log: adb logcat | grep 'WhisperAsrEngine\\|pushAudio\\|Inference thread'"
echo "3. 驗證性能改進:"
echo "   - pushAudio() 耗時應 < 5ms"
echo "   - 應看到 'Inference thread started' log"
echo "   - 應看到 'Transcript emitted (XXX ms)' log"
echo ""
echo "詳細實施摘要: IMPLEMENTATION-SUMMARY-BACKGROUND-INFERENCE.md"
echo ""
