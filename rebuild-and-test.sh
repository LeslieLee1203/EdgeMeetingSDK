#!/bin/bash

# Production 版 Whisper 重新編譯與測試腳本
# 修正：能量檢測閾值問題

set -e  # 遇到錯誤立即退出

echo "=========================================="
echo "Production 版 Whisper 重新編譯與測試"
echo "修正: 能量檢測閾值 (1000.0 → 30.0)"
echo "=========================================="
echo ""

# 1. 清理舊的 build
echo "步驟 1/5: 清理舊的 build..."
./gradlew clean
echo "✅ 清理完成"
echo ""

# 2. 編譯 meeting-engine (C++ 代碼)
echo "步驟 2/5: 編譯 meeting-engine (C++ 代碼)..."
./gradlew :meeting-engine:build
echo "✅ meeting-engine 編譯完成"
echo ""

# 3. 編譯 APK
echo "步驟 3/5: 編譯 Debug APK..."
./gradlew :app:assembleDebug
echo "✅ APK 編譯完成"
echo ""

# 4. 安裝到設備
echo "步驟 4/5: 安裝到設備..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo "✅ 安裝完成"
echo ""

# 5. 啟動日誌監控
echo "步驟 5/5: 啟動日誌監控..."
echo ""
echo "=========================================="
echo "開始監控關鍵日誌..."
echo "=========================================="
echo ""
echo "請執行以下操作測試："
echo "  1. 打開應用"
echo "  2. 點擊 Prepare"
echo "  3. 點擊 Start"
echo "  4. 對著麥克風說話 10-20 秒"
echo "  5. 點擊 Stop"
echo ""
echo "預期看到："
echo "  ✅ Inference triggered: Reached window size (8s)"
echo "  ✅ shouldSkipInference: maxRMS=XXX, shouldSkip=0"
echo "  ✅ runInference: Processing XXX samples"
echo "  ✅ Decoder Result: '你的語音內容'"
echo "  ✅ LocalAgreement: Stable prefix extended"
echo ""
echo "按 Ctrl+C 停止監控"
echo ""
echo "=========================================="
echo ""

# 清空舊日誌
adb logcat -c

# 啟動日誌監控（彩色輸出）
adb logcat | grep --color=always -E "WhisperAsrEngine|shouldSkipInference|runInference|Decoder Result|LocalAgreement|removePrefixOverlap"
