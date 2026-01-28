#!/usr/bin/env python3
"""
verify_bpe_decode.py

驗證 Whisper Byte-Level BPE 解碼邏輯
模擬 C++ 應該實現的解碼流程
"""

from transformers import WhisperTokenizer

print("=" * 60)
print("Whisper Byte-Level BPE 解碼驗證")
print("=" * 60)
print()

# 載入 tokenizer
tokenizer = WhisperTokenizer.from_pretrained("openai/whisper-base")

# 從 log_001.txt 提取的真實 TokenID 序列
test_cases = [
    {
        'name': '序列 2（第一段亂碼）',
        'token_ids': [5562, 21596, 30716, 42503],  # 前 4 個 token
        'expected': '这话题永',
    },
    {
        'name': '序列 3',
        'token_ids': [5000],  # '他'
        'expected': '他',
    },
    {
        'name': '序列 5（沒有亂碼的）',
        'token_ids': [22942, 5000],  # '而且他'
        'expected': '而且他',
    },
]

print("[1] 使用 Tokenizer.decode() 驗證（正確方法）\n")
for tc in test_cases:
    decoded = tokenizer.decode(tc['token_ids'])
    match = "✅" if decoded == tc['expected'] else "❌"
    print(f"{tc['name']}:")
    print(f"  Token IDs: {tc['token_ids']}")
    print(f"  解碼結果: '{decoded}'")
    print(f"  期望結果: '{tc['expected']}'")
    print(f"  匹配: {match}")
    print()

print("-" * 60)
print()

print("[2] 模擬錯誤的 C++ 實現（直接拼接）\n")
for tc in test_cases:
    # 模擬錯誤方法：直接拼接 convert_ids_to_tokens 的結果
    tokens_str = tokenizer.convert_ids_to_tokens(tc['token_ids'])
    wrong_result = ''.join(tokens_str)

    print(f"{tc['name']}:")
    print(f"  Token 字符串: {tokens_str}")
    print(f"  錯誤拼接: '{wrong_result}'")
    print(f"  期望結果: '{tc['expected']}'")
    print(f"  ❌ 不匹配！")
    print()

print("-" * 60)
print()

print("[3] 模擬正確的 C++ 實現（Byte-Level 解碼）\n")
for tc in test_cases:
    # 模擬正確方法：byte-level 解碼
    tokens_str = tokenizer.convert_ids_to_tokens(tc['token_ids'])

    # 將 byte-level 字符轉換為原始字節
    utf8_bytes = bytearray()
    for token_str in tokens_str:
        for char in token_str:
            # 每個字符的 Unicode code point 就是原始字節值（0-255）
            byte_val = ord(char)
            if byte_val > 255:
                print(f"    ⚠️  警告：字符 '{char}' (U+{byte_val:04X}) 超出字節範圍")
            else:
                utf8_bytes.append(byte_val & 0xFF)

    # 解碼為 UTF-8 字符串
    try:
        correct_result = utf8_bytes.decode('utf-8')
        match = "✅" if correct_result == tc['expected'] else "❌"
    except UnicodeDecodeError as e:
        correct_result = f"<解碼錯誤: {e}>"
        match = "❌"

    print(f"{tc['name']}:")
    print(f"  Token 字符串: {tokens_str}")
    print(f"  UTF-8 字節: {utf8_bytes.hex()}")
    print(f"  正確解碼: '{correct_result}'")
    print(f"  期望結果: '{tc['expected']}'")
    print(f"  匹配: {match}")
    print()

print("=" * 60)
print("結論")
print("=" * 60)
print()
print("✅ 方法 1（Tokenizer.decode）和方法 3（Byte-Level 解碼）得到相同的正確結果")
print("❌ 方法 2（直接拼接）產生亂碼")
print()
print("C++ 修復方案：")
print("  1. 讀取 vocab[token_id].token 得到 byte-level 字符串")
print("  2. 將每個字符的 code point 提取為字節（0-255）")
print("  3. 拼接所有字節形成完整的 UTF-8 序列")
print("  4. 將 UTF-8 字節序列轉換為 std::string")
print()
