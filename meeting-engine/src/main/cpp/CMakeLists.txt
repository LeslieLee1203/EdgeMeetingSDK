# meeting-engine/src/main/cpp/CMakeLists.txt

# 設定最低版本 (配合 Gradle 設定)
cmake_minimum_required(VERSION 3.22.1)

# 定義專案名稱與語言
project("meeting-engine" LANGUAGES CXX)

# 啟用測試（必須在專案定義後）
enable_testing()

# Google Test 設置（用於 C++ 單元測試）
# 為什麼使用 Google Test：
# 1. 業界標準的 C++ 測試框架
# 2. 與 JUnit 類似的斷言風格
# 3. 支援 TDD 開發流程
include(FetchContent)
FetchContent_Declare(
        googletest
        GIT_REPOSITORY https://github.com/google/googletest.git
        GIT_TAG v1.14.0  # 使用穩定版本
)
# 防止 Google Test 覆蓋父專案的編譯器/連結器設定
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# 1. 尋找 Oboe 套件
# 關鍵：因為我們在 build.gradle 開啟了 Prefab，這裡可以直接 find_package
find_package(oboe REQUIRED CONFIG)

# 2. RKNN Runtime Library 設定（用於 Whisper ASR on RK3588）
# 為什麼需要 RKNN：
# - RK3588 NPU 加速 Whisper 模型推論
# - librknnrt.so 提供 rknn_init, rknn_run 等 API
# - 預編譯的 .so 檔位於 jniLibs/arm64-v8a/

# RKNN Runtime Library 路徑
set(RKNN_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(RKNN_RUNTIME_LIB ${RKNN_LIB_DIR}/librknnrt.so)

# 驗證 RKNN 庫存在
if(NOT EXISTS ${RKNN_RUNTIME_LIB})
    message(WARNING "RKNN Runtime Library not found at ${RKNN_RUNTIME_LIB}")
    message(WARNING "ASR functionality will not be available")
    message(WARNING "Please download librknnrt.so from https://github.com/airockchip/rknn-toolkit2/releases")
endif()

# 建立 RKNN imported library target（只有當檔案存在時）
if(EXISTS ${RKNN_RUNTIME_LIB})
    add_library(rknnrt SHARED IMPORTED)
    set_target_properties(rknnrt PROPERTIES
            IMPORTED_LOCATION ${RKNN_RUNTIME_LIB}
    )
    message(STATUS "RKNN Runtime Library found: ${RKNN_RUNTIME_LIB}")
endif()

# 3. 定義你的 Native Library
add_library(
        meeting-engine
        SHARED
        # 這裡指定你的 C++ 原始碼檔案
        native-lib.cpp
        AudioRecorder.cpp
        AudioRecorder.h
        AudioProcessor.cpp
        AudioProcessor.h
        # Phase 3 將加入 ASR 原始碼：
        # asr/AsrEngine.cpp
        # asr/WhisperAsrEngine.cpp
)

# 4. 連結 Library
target_link_libraries(
        meeting-engine
        # 連結 Oboe (注意 namespace 是 oboe::oboe)
        oboe::oboe
        # Android 系統 Log 工具
        log
        # Android 系統 Audio 工具
        android
)

# 連結 RKNN Runtime（如果存在）
# Phase 3 實作 WhisperAsrEngine 時會實際使用
if(TARGET rknnrt)
    target_link_libraries(meeting-engine rknnrt)
    message(STATUS "Linked RKNN Runtime to meeting-engine")
endif()

# 4. C++ 單元測試（Google Test）
# 為什麼需要 C++ 測試：
# - Phase 3 的 AsrEngine、WhisperAsrEngine 需要在 C++ 層測試
# - 驗證 RKNN 模型載入、音訊處理邏輯
# - 獨立於 Android 環境，CI 友善

# 測試原始碼位於 test/ 子目錄
file(GLOB_RECURSE TEST_SOURCES "test/*.cpp")

# 只有當測試檔案存在時才建立測試目標
if(TEST_SOURCES)
    add_executable(
            asr_tests
            ${TEST_SOURCES}
            # 需要測試的原始碼（排除 JNI 綁定層 native-lib.cpp）
            AudioRecorder.cpp
            AudioProcessor.cpp
            # 未來會加入 asr/AsrEngine.cpp, asr/WhisperAsrEngine.cpp
    )

    target_link_libraries(
            asr_tests
            GTest::gtest_main  # Google Test main
            oboe::oboe
            log
            android
    )

    # 注意：gtest_discover_tests 在交叉編譯時無法使用（無法在 macOS 執行 ARM64 binary）
    # 測試將在 Android 設備上透過 adb 執行，或在 CI 的 Android 模擬器中執行
    # include(GoogleTest)
    # gtest_discover_tests(asr_tests)
endif()