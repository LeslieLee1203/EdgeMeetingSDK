/**
 * Google Test 示範測試
 *
 * 目的：驗證 C++ 測試基礎設施正確設置
 *
 * 為什麼需要這個檔案：
 * 1. 驗證 Google Test 框架可正常運作
 * 2. 確保 CMake 配置正確
 * 3. 作為後續 AsrEngine 測試的範本
 *
 * Phase 3 將建立真正的 ASR 測試：
 * - test/AsrEngineTest.cpp
 * - test/WhisperAsrEngineTest.cpp
 */

#include <gtest/gtest.h>

// 簡單的示範測試，驗證 Google Test 運作正常
TEST(SampleTest, BasicAssertion) {
    // 基本斷言測試
    EXPECT_EQ(1 + 1, 2);
    EXPECT_TRUE(true);
    EXPECT_FALSE(false);
}

TEST(SampleTest, StringComparison) {
    // 字串比較測試
    std::string hello = "Hello";
    std::string world = "World";

    EXPECT_EQ(hello, "Hello");
    EXPECT_NE(hello, world);
}

TEST(SampleTest, NumericComparison) {
    // 數值比較測試
    int a = 10;
    int b = 20;

    EXPECT_LT(a, b);  // a < b
    EXPECT_GT(b, a);  // b > a
    EXPECT_LE(a, 10); // a <= 10
    EXPECT_GE(b, 20); // b >= 20
}

// 注意：此檔案在 Phase 3 實作 AsrEngine 後將被移除或重構為真實測試
