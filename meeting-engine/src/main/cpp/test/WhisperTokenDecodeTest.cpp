/**
 * WhisperTokenDecodeTest.cpp
 *
 * 測試 Whisper Token 解碼邏輯，特別是中文 Token 的處理
 *
 * 從 log_001.txt 中提取的真實 TokenID：
 * - TokenID 5562: '这'
 * - TokenID 21596: '话'
 * - TokenID 30716: '题'
 * - TokenID 42503: '永'
 *
 * 預期輸出："这话题永"
 * 實際輸出（錯誤）："这话题永����不完..."（包含 � 亂碼）
 */

#include <gtest/gtest.h>
#include <string>
#include <cstring>
#include <cstdio>
#include <vector>

// 從 WhisperUtils.h 複製必要的結構和函數聲明
struct VocabEntry {
    int index;
    char* token;
};

// 模擬 read_vocab 函數（簡化版）
int read_vocab_test(const char *fileName, VocabEntry *vocab, int max_count) {
    FILE *fp = fopen(fileName, "r");
    if (fp == NULL) {
        return -1;
    }

    char line[512];
    int count = 0;
    while (fgets(line, sizeof(line), fp) && count < max_count) {
        char* space = strchr(line, ' ');
        if (space) {
            *space = '\0';
            vocab[count].index = atoi(line);

            char* token = space + 1;
            token[strcspn(token, "\r\n")] = 0;
            vocab[count].token = strdup(token);
            count++;
        }
    }
    fclose(fp);
    return count;
}

// 測試 1：驗證 vocab 檔案讀取是否正確
TEST(WhisperTokenDecodeTest, VocabReadingTest) {
    const char* vocab_path = "/data/local/tmp/models/vocab_en.txt";

    // 分配 vocab 數組
    const int VOCAB_SIZE = 51866;
    VocabEntry* vocab = new VocabEntry[VOCAB_SIZE];
    memset(vocab, 0, sizeof(VocabEntry) * VOCAB_SIZE);

    int count = read_vocab_test(vocab_path, vocab, VOCAB_SIZE);

    ASSERT_GT(count, 0) << "Failed to read vocab file";
    ASSERT_GE(count, 42504) << "Vocab size too small";

    // 驗證從 log 中提取的 TokenID
    struct TestCase {
        int token_id;
        const char* expected_utf8;
        const char* expected_hex;
    };

    std::vector<TestCase> test_cases = {
        {5562, "这", "e8bf99"},
        {21596, "话", "e8af9d"},
        {30716, "题", "e9a298"},
        {42503, "永", "e6b0b8"},
    };

    for (const auto& tc : test_cases) {
        ASSERT_LT(tc.token_id, count) << "TokenID " << tc.token_id << " out of range";
        ASSERT_NE(vocab[tc.token_id].token, nullptr) << "TokenID " << tc.token_id << " token is null";

        std::string token_str(vocab[tc.token_id].token);

        // 檢查 UTF-8 編碼
        printf("TokenID %d: '%s' (len=%zu)\n", tc.token_id, token_str.c_str(), token_str.length());
        printf("  Expected: %s\n", tc.expected_utf8);
        printf("  Hex: ");
        for (unsigned char c : token_str) {
            printf("%02x", c);
        }
        printf(" (expected: %s)\n", tc.expected_hex);

        EXPECT_EQ(token_str, std::string(tc.expected_utf8))
            << "TokenID " << tc.token_id << " mismatch";
    }

    // 清理
    for (int i = 0; i < count; i++) {
        if (vocab[i].token) free(vocab[i].token);
    }
    delete[] vocab;
}

// 測試 2：驗證 Token 拼接邏輯
TEST(WhisperTokenDecodeTest, TokenConcatenationTest) {
    // 模擬從 Decoder 獲得的 Token 序列
    std::vector<const char*> tokens = {
        "这",   // TokenID 5562
        "话",   // TokenID 21596
        "题",   // TokenID 30716
        "永",   // TokenID 42503
    };

    // 方法 1：直接拼接（當前實現）
    std::string result_direct;
    for (const char* token : tokens) {
        result_direct += token;
    }

    printf("Direct concatenation result: '%s'\n", result_direct.c_str());
    printf("  Bytes: ");
    for (unsigned char c : result_direct) {
        printf("%02x", c);
    }
    printf("\n");

    // 預期結果
    std::string expected = "这话题永";
    EXPECT_EQ(result_direct, expected) << "Direct concatenation should work for UTF-8";

    // 驗證沒有無效的 UTF-8 序列
    // 如果有 � (U+FFFD)，表示編碼錯誤
    EXPECT_EQ(result_direct.find("\uFFFD"), std::string::npos)
        << "Found replacement character (�), indicating invalid UTF-8";
}

// 測試 3：測試實際的 Whisper 後處理邏輯
static void replace_substr(std::string &str, const std::string &from, const std::string &to) {
    if (from.empty()) return;
    size_t start_pos = 0;
    while ((start_pos = str.find(from, start_pos)) != std::string::npos) {
        str.replace(start_pos, from.length(), to);
        start_pos += to.length();
    }
}

TEST(WhisperTokenDecodeTest, PostProcessingTest) {
    // 模擬完整的解碼流程
    std::string all_token_str = "这话题永";

    // 應用 Whisper 的後處理規則
    replace_substr(all_token_str, "\u0120", " ");  // Whisper 使用 U+0120 表示空格
    replace_substr(all_token_str, "<|endoftext|>", "");
    replace_substr(all_token_str, "\n", "");

    printf("After post-processing: '%s'\n", all_token_str.c_str());

    EXPECT_EQ(all_token_str, "这话题永") << "Post-processing should preserve Chinese characters";
}

// 測試 4：測試包含 BPE 空格標記的情況
TEST(WhisperTokenDecodeTest, BPESpaceTokenTest) {
    // Whisper BPE 使用 \u0120 (Ġ) 表示詞首空格
    std::string token_with_space = "\u0120你好";  // " 你好"

    replace_substr(token_with_space, "\u0120", " ");

    printf("Token with BPE space: '%s'\n", token_with_space.c_str());
    EXPECT_EQ(token_with_space, " 你好");
}

// 主函數
int main(int argc, char **argv) {
    printf("=== Whisper Token Decode Test ===\n");
    printf("This test verifies Chinese token handling in Whisper ASR\n\n");

    testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
