// meeting-engine/src/main/cpp/Log.h
// 統一的 Log 定義 - Release build 時自動移除開發用 log

#pragma once

#include <android/log.h>

// ============================================================================
// Log Level 說明：
// - LOGE: 錯誤訊息 (永遠啟用)
// - LOG_RESULT: 重要結果輸出，如 Decoder Result (永遠啟用)
// - LOGW: 警告訊息 (Debug only)
// - LOGI: 資訊訊息 (Debug only)
// - LOGD: 除錯訊息 (Debug only)
// ============================================================================

#ifndef LOG_TAG
#define LOG_TAG "MeetingEngine"
#endif

// 永遠啟用的 log（錯誤和重要結果）
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOG_RESULT(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Debug only logs - Release build 時會被移除（NDEBUG 由 CMake Release build 自動定義）
#ifdef NDEBUG
    // Release build: 移除所有開發用 log
    #define LOGD(...) ((void)0)
    #define LOGI(...) ((void)0)
    #define LOGW(...) ((void)0)
#else
    // Debug build: 啟用所有 log
    #define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#endif
