#!/usr/bin/env python3
"""
fix_vocab_with_transformers.py

使用 Hugging Face Transformers 庫生成正確的 Whisper vocab 檔案
這個腳本會從 OpenAI 官方的 Whisper Base 模型中提取正確的 tokenizer

執行方式：
    uv run python fix_vocab_with_transformers.py
"""

import sys
from pathlib import Path

print("=" * 60)
print("Whisper Vocab 修復工具")
print("=" * 60)
print()

# 1. 檢查依賴
try:
    from transformers import WhisperTokenizer
    print("✅ transformers 庫已安裝")
except ImportError:
    print("❌ transformers 庫未安裝")
    print()
    print("請先安裝依賴：")
    print("  uv pip install transformers torch")
    sys.exit(1)

# 2. 載入 Whisper tokenizer
print("\n[1/4] 從 Hugging Face 載入 Whisper Base tokenizer...")
try:
    tokenizer = WhisperTokenizer.from_pretrained("openai/whisper-base")
    print(f"✅ 成功載入，Vocab 大小: {tokenizer.vocab_size}")
except Exception as e:
    print(f"❌ 載入失敗: {e}")
    sys.exit(1)

# 3. 生成正確的 vocab 檔案
output_path = Path("meeting-engine/src/main/assets/models/vocab_en_fixed.txt")
backup_path = Path("meeting-engine/src/main/assets/models/vocab_en.txt.backup")
original_path = Path("meeting-engine/src/main/assets/models/vocab_en.txt")

print(f"\n[2/4] 生成新的 vocab 檔案...")
print(f"  輸出路徑: {output_path}")

token_count = 0
error_count = 0

with open(output_path, "w", encoding="utf-8") as f:
    for token_id in range(tokenizer.vocab_size):
        try:
            # 使用 tokenizer 的內部方法獲取 token 字符串
            token_str = tokenizer.convert_ids_to_tokens([token_id])[0]

            # 寫入檔案：格式 "token_id token_string"
            f.write(f"{token_id} {token_str}\n")
            token_count += 1

        except Exception as e:
            print(f"  ⚠️  TokenID {token_id} 處理錯誤: {e}")
            error_count += 1

print(f"✅ 成功生成 {token_count} 個 tokens")
if error_count > 0:
    print(f"⚠️  {error_count} 個 tokens 處理失敗")

# 4. 驗證新檔案
print(f"\n[3/4] 驗證新 vocab 檔案...")
broken_count = 0

with open(output_path, "r", encoding="utf-8") as f:
    for line_num, line in enumerate(f, 1):
        if '\uFFFD' in line or '�' in line:
            broken_count += 1
            if broken_count <= 5:  # 只顯示前 5 個
                print(f"  ⚠️  Line {line_num}: {repr(line.strip())}")

if broken_count == 0:
    print(f"✅ 沒有損壞的 token（無 � 字符）")
else:
    print(f"⚠️  發現 {broken_count} 個可能有問題的 token")

# 5. 備份舊檔案並替換
print(f"\n[4/4] 替換舊 vocab 檔案...")

if original_path.exists():
    # 備份
    import shutil
    shutil.copy2(original_path, backup_path)
    print(f"✅ 已備份舊檔案到: {backup_path}")

    # 替換
    shutil.move(str(output_path), str(original_path))
    print(f"✅ 已替換為新 vocab 檔案")
else:
    # 如果原檔案不存在，直接重命名
    output_path.rename(original_path)
    print(f"✅ 已生成新 vocab 檔案")

print()
print("=" * 60)
print("修復完成！")
print("=" * 60)
print()
print("下一步：")
print("  1. 重新編譯並運行 APP 測試中文轉錄")
print("  2. 執行測試腳本: python3 analyze_token_decode.py")
print("  3. 如果有問題，可恢復備份: mv vocab_en.txt.backup vocab_en.txt")
print()
