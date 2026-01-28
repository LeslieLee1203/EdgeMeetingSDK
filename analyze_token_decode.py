#!/usr/bin/env python3
"""
analyze_token_decode.py

分析 Whisper Token 解碼問題的工具腳本
從 log_001.txt 提取 TokenID，從 vocab_en.txt 讀取對應的 token，
模擬 C++ 的拼接邏輯，找出亂碼的根本原因。

使用方法：
    python3 analyze_token_decode.py
"""

import re
import sys
from typing import List, Tuple, Dict

# 路徑配置
VOCAB_PATH = "meeting-engine/src/main/assets/models/vocab_en.txt"
LOG_PATH = "log_001.txt"


def load_vocab(vocab_path: str) -> Dict[int, str]:
    """載入 vocab 檔案，返回 token_id -> token_text 的映射"""
    vocab = {}
    try:
        with open(vocab_path, 'r', encoding='utf-8', errors='replace') as f:
            for line in f:
                parts = line.strip().split(' ', 1)
                if len(parts) == 2:
                    token_id = int(parts[0])
                    token_text = parts[1]
                    vocab[token_id] = token_text
        print(f"✅ 成功載入 vocab: {len(vocab)} tokens")
        return vocab
    except Exception as e:
        print(f"❌ 載入 vocab 失敗: {e}")
        sys.exit(1)


def extract_token_sequences(log_path: str) -> List[Tuple[List[int], str]]:
    """
    從 log 中提取 TokenID 序列和對應的 Decoder Result
    返回: [(token_ids, decoded_text), ...]
    """
    sequences = []
    current_tokens = []

    try:
        with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
            lines = f.readlines()

        i = 0
        while i < len(lines):
            line = lines[i]

            # 檢測新的推論開始（第一個 Decoder Step）
            if "Decoder Step 1: TokenID=" in line:
                current_tokens = []

                # 收集所有 Decoder Step（最多 5 個會被 log）
                j = i
                while j < len(lines) and "Decoder Step" in lines[j]:
                    match = re.search(r'TokenID=(\d+)', lines[j])
                    if match:
                        token_id = int(match.group(1))
                        current_tokens.append(token_id)
                    j += 1

                # 尋找對應的 Decoder Result
                k = j
                while k < len(lines) and k < j + 100:
                    if "Decoder Result:" in lines[k]:
                        match = re.search(r"Decoder Result: '(.*?)'", lines[k])
                        if match:
                            decoded_text = match.group(1)
                            sequences.append((current_tokens.copy(), decoded_text))
                        break
                    k += 1

                i = k
            else:
                i += 1

        print(f"✅ 從 log 提取了 {len(sequences)} 個解碼序列")
        return sequences

    except Exception as e:
        print(f"❌ 讀取 log 失敗: {e}")
        sys.exit(1)


def simulate_cpp_decoding(token_ids: List[int], vocab: Dict[int, str]) -> str:
    """模擬 C++ 的 token 拼接邏輯"""
    result = ""
    for token_id in token_ids:
        if token_id in vocab:
            token_text = vocab[token_id]
            result += token_text
        else:
            result += f"<UNKNOWN:{token_id}>"
    return result


def analyze_utf8(text: str) -> Dict:
    """分析字符串的 UTF-8 編碼"""
    utf8_bytes = text.encode('utf-8', errors='replace')

    # 檢測替換字符 (U+FFFD = EF BF BD)
    replacement_char_count = text.count('\uFFFD')
    replacement_char_positions = [i for i, c in enumerate(text) if c == '\uFFFD']

    return {
        'length': len(text),
        'byte_length': len(utf8_bytes),
        'hex': utf8_bytes.hex(),
        'has_replacement_char': replacement_char_count > 0,
        'replacement_char_count': replacement_char_count,
        'replacement_char_positions': replacement_char_positions,
    }


def main():
    print("=" * 60)
    print("Whisper Token Decode 問題分析工具")
    print("=" * 60)
    print()

    # 1. 載入 vocab
    vocab = load_vocab(VOCAB_PATH)

    # 2. 從 log 提取 token 序列
    sequences = extract_token_sequences(LOG_PATH)

    if not sequences:
        print("❌ 未找到任何解碼序列")
        sys.exit(1)

    print()
    print("=" * 60)
    print("解碼序列分析")
    print("=" * 60)

    # 3. 分析每個序列
    for idx, (token_ids, actual_result) in enumerate(sequences):
        print(f"\n【序列 {idx + 1}】")
        print(f"  Token IDs (前 5 個): {token_ids}")

        # 顯示對應的 token 文字
        print(f"  Token 文字:")
        for i, tid in enumerate(token_ids):
            if tid in vocab:
                token_text = vocab[tid]
                print(f"    TokenID {tid}: '{token_text}'")
            else:
                print(f"    TokenID {tid}: <NOT IN VOCAB>")

        # 模擬 C++ 拼接
        simulated_result = simulate_cpp_decoding(token_ids, vocab)
        print(f"  模擬拼接結果 (前{len(token_ids)}個token): '{simulated_result}'")

        # 實際 C++ Decoder Result
        print(f"  實際 Decoder Result: '{actual_result}'")

        # UTF-8 分析
        actual_analysis = analyze_utf8(actual_result)
        simulated_analysis = analyze_utf8(simulated_result)

        print(f"\n  UTF-8 分析 (實際):")
        print(f"    字符數: {actual_analysis['length']}")
        print(f"    字節數: {actual_analysis['byte_length']}")
        print(f"    包含亂碼: {'是 ❌' if actual_analysis['has_replacement_char'] else '否 ✅'}")
        if actual_analysis['has_replacement_char']:
            print(f"    亂碼數量: {actual_analysis['replacement_char_count']}")
            print(f"    亂碼位置: {actual_analysis['replacement_char_positions']}")
        print(f"    Hex (前 40 bytes): {actual_analysis['hex'][:80]}...")

        print(f"\n  UTF-8 分析 (模擬):")
        print(f"    字符數: {simulated_analysis['length']}")
        print(f"    字節數: {simulated_analysis['byte_length']}")
        print(f"    包含亂碼: {'是 ❌' if simulated_analysis['has_replacement_char'] else '否 ✅'}")
        print(f"    Hex: {simulated_analysis['hex']}")

        # 比較
        if actual_analysis['has_replacement_char'] and not simulated_analysis['has_replacement_char']:
            print(f"\n  🔍 發現問題：")
            print(f"    - 模擬拼接（基於 vocab 前 {len(token_ids)} 個 token）沒有亂碼")
            print(f"    - 實際 Decoder Result 有 {actual_analysis['replacement_char_count']} 個亂碼")
            print(f"    - 可能原因：")
            print(f"      1. Decoder 輸出了更多 token（log 只顯示前 5 個）")
            print(f"      2. 某些 token 在 vocab 中的 UTF-8 編碼不完整")
            print(f"      3. C++ 字符串拼接過程中發生了編碼錯誤")

        print()
        print("-" * 60)

    print()
    print("=" * 60)
    print("關鍵發現")
    print("=" * 60)
    print()
    print("1. Vocab 檔案中的中文 token 是以 UTF-8 直接存儲的（非 Base64）")
    print("2. Log 只顯示前 5 個 Decoder Step，實際解碼了更多 token")
    print("3. 亂碼 � (U+FFFD) 表示無效的 UTF-8 序列")
    print()
    print("建議：")
    print("  - 在 C++ 中增加完整的 token decode log")
    print("  - 檢查 vocab 檔案是否有損壞的 UTF-8 序列")
    print("  - 驗證 strdup 和字符串拼接是否正確處理多字節字符")
    print()


if __name__ == "__main__":
    main()
