#!/bin/bash
# run_token_decode_test.sh
#
# 在 Android 設備上執行 Whisper Token 解碼測試
# 用於驗證中文 Token 處理邏輯

set -e

echo "=== Whisper Token Decode Test Runner ==="
echo ""

# 1. 檢查 JAVA_HOME
echo "[1/6] Checking JAVA_HOME..."
if [ -z "$JAVA_HOME" ]; then
    echo "  Setting JAVA_HOME..."
    export JAVA_HOME="$(/usr/libexec/java_home)"
fi
echo "  JAVA_HOME=$JAVA_HOME"

# 2. 編譯 C++ 測試
echo ""
echo "[2/6] Building C++ tests..."
JAVA_HOME="$JAVA_HOME" ./gradlew :meeting-engine:build

# 3. 找到測試 binary
echo ""
echo "[3/6] Locating test binary..."
TEST_BINARY=$(find meeting-engine/.cxx/Debug -name "asr_tests" -type f | grep arm64-v8a | head -1)

if [ -z "$TEST_BINARY" ]; then
    echo "  ERROR: Test binary not found!"
    echo "  Expected path: meeting-engine/.cxx/Debug/*/arm64-v8a/asr_tests"
    exit 1
fi
echo "  Found: $TEST_BINARY"

# 4. 檢查 Android 設備
echo ""
echo "[4/6] Checking Android device..."
DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "  ERROR: No Android device connected!"
    echo "  Please connect a device or start an emulator."
    exit 1
fi
echo "  Device connected: $(adb devices | grep 'device$' | awk '{print $1}')"

# 5. 推送測試 binary 和模型檔案到設備
echo ""
echo "[5/6] Pushing test files to device..."
adb shell "mkdir -p /data/local/tmp/models"

# 推送 vocab 檔案
echo "  Pushing vocab_en.txt..."
adb push meeting-engine/src/main/assets/models/vocab_en.txt /data/local/tmp/models/

# 推送測試 binary
echo "  Pushing asr_tests..."
adb push "$TEST_BINARY" /data/local/tmp/
adb shell "chmod +x /data/local/tmp/asr_tests"

# 6. 執行測試
echo ""
echo "[6/6] Running tests on device..."
echo "----------------------------------------"
adb shell "cd /data/local/tmp && ./asr_tests"
TEST_EXIT_CODE=$?

echo "----------------------------------------"
echo ""

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo "✅ All tests passed!"
else
    echo "❌ Tests failed with exit code: $TEST_EXIT_CODE"
    exit $TEST_EXIT_CODE
fi
