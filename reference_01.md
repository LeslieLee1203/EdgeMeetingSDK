用 Whisper 做「即時語音轉錄」時，最麻煩的其實不是推論，而是 **怎麼切 chunk + 怎麼把 chunk 的結果拼回一段不重複、不中斷、越來越穩定的句子**。下面整理幾個實戰上最好用的「分段推論 & 組句演算法」，你可以直接挑一種做 MVP，再逐步加強。

---

## 你會遇到的 3 個核心問題

1. **邊界斷字**：chunk 切在詞中間，頭尾容易漏字或錯字
2. **重複字**：chunk 有 overlap 時，尾端內容會被重複轉一次
3. **輸出不穩定**：即時模式下前面句子會被 Whisper “改口”（hypothesis 反覆修正）

Whisper 也明確是 **30 秒 receptive field**，長音訊要用 sliding window / chunked 策略處理。([Hugging Face][1])

---

## 分段推論策略（Chunking）

### A) 固定長度 Sliding Window + Overlap（最常用）

**做法**

* 維持一個 ring buffer
* 每隔 `step` 秒跑一次推論
* 每次餵 `window` 秒音訊（包含 overlap）

**建議參數（低延遲體感很順）**

* `window = 5 ~ 10s`
* `step = 0.5 ~ 1.0s`
* `overlap = 1 ~ 2s`（或 window 的 20%~40%）

這個模式在 whisper.cpp 的 streaming 範例也很常見（滑動視窗 + VAD）。([GitHub][2])
很多教學也直接建議 5–10 秒滑窗做 live transcription。([muSharp][3])

✅ 優點：實作簡單、延遲可控
⚠️ 缺點：最需要「去重 + 穩定輸出」演算法

---

### B) VAD 切段（用靜音當天然分隔，成本最低）

**做法**

* 先跑 VAD（Silero / WebRTC VAD 都行）
* 只在「有語音」的片段推論
* 靜音段直接當作句子/段落邊界

WhisperX 等 pipeline 常用 VAD 先切段，再送 Whisper。([arXiv][4])

✅ 優點：省算力、句子切得自然
⚠️ 缺點：遇到「幾乎不停頓長講」還是得回到滑窗策略

---

### C) Hybrid：VAD 控制啟停 + Sliding Window 保持連續（推薦做產品）

**做法**

* VAD 只決定「開始講了」和「講完了」
* 一旦進入 speech 狀態，就用 sliding window 連續推
* 偵測到連續靜音超過 `silence_timeout`，才 finalize 一句

這種是 live service 最常見架構（穩、可控、可省成本）。

---

## 組合成句子的演算法（關鍵：去重 + 穩定）

### 1) 文字對齊去重：Longest Common Subsequence（LCS）/ 最長重疊（最穩）

**適用**：chunk overlap 造成重複字

**想法**

* 每次你會拿到一段 `new_text`
* 你也保留上一輪尚未 finalize 的 `tail_text`
* 在兩者之間做字串對齊，找到重疊區，合併後去重

**簡化版（很好用）**

* 只找「tail 的 suffix」與「new 的 prefix」最大重疊長度
* 把重疊 prefix 去掉再 append

```text
committed = 已確認輸出
tail = 尚未確認尾巴（會變動）
new = 本輪推論結果

overlap = 最大 k，使得 tail[-k:] == new[:k]
tail = new
output = committed + (new 去掉 overlap)
```

✅ 優點：效果直觀，重複字問題幾乎解掉
⚠️ 缺點：Whisper 會改字時，單純 exact match 會失效
➡️ 改進：用「word-level」比對、允許 minor edit distance

---

### 2) Token/Word「穩定提交」（Stable Prefix Commit）——即時輸出不抖動的核心

**適用**：你不想螢幕上的字一直被改來改去

**想法**

* 每輪推論都會產生一個 hypothesis（完整句子）
* 你不要一次把全部都當真
* 只把「已經連續幾輪都一樣的前綴」commit 出去

常見做法有兩種：

**(a) 兩輪一致就提交（超簡單但效果已很不錯）**

* `hypothesis_t` 和 `hypothesis_{t-1}` 做 longest common prefix
* 把共同前綴中「足夠長」的部分 commit

**(b) 多輪 local agreement（更強、學術上也常用）**
Whisper-Streaming 提到用 **Local Agreement** 確認 token 後才輸出，可減少錯誤與抖動。([GitHub][5])

✅ 優點：即時字幕體感超穩
⚠️ 缺點：會多一點延遲（通常 0.5~2 秒）

---

### 3) 用時間戳做切點：Commit “現在時間 - overlap_margin” 以前的詞

**適用**：你拿得到 word timestamps（WhisperX / stable-ts / forced alignment）

**想法**

* 每輪推論你都會得到 words with timestamps
* 你定義 `commit_time = now - overlap_margin`
* **所有 end_time < commit_time 的 words 都視為已穩定，直接 commit**
* 剩下的留在 tail，下輪繼續修

這種方式非常乾淨，也容易控制延遲。
如果你 timestamps 不穩，可以用 stable-ts 類工具改善時間戳穩定度。([GitHub][6])

✅ 優點：又穩又準、很好 debug
⚠️ 缺點：需要 word timestamps（或額外 alignment）

---

### 4) 句子邊界決策（Sentence Finalization / Endpointing）

當你要「變成句子」而不只是字串，通常會加這些規則：

**Finalize 一句的條件（任一符合即可）**

* VAD：連續靜音 > `500~800ms`
* 文字：出現句號/問號/驚嘆號 + 靜音 > `200~400ms`
* 長度：尾巴累積超過 N 個字（避免永遠不結束）

✅ 這會讓 UI 顯示更像人講話，不會一整段黏在一起

---

## 推薦的 MVP 組合（你可以直接照抄）

**我建議你先做這套：**

### ✅ MVP：Sliding Window + 重疊去重 + 兩輪一致提交

* chunk：`window=8s, step=0.8s, overlap=1.6s`
* merge：最大重疊 suffix/prefix 去重
* stabilize：兩輪 longest common prefix，commit 穩定前綴
* finalize：靜音 > 700ms 就結句（配 VAD）

這套不需要額外模型，就能做到「可用的即時字幕」。

---

## 進階版（接近產品等級）

### ✅ Production：Hybrid VAD + Timestamp Commit + Local Agreement

* VAD 控制 speech start/stop，沒人講話就不跑 Whisper（省錢省電）
* speech 段內 sliding window 推論（確保不中斷）
* 用 word timestamps 決定 commit 邊界（穩）
* Local agreement 或多輪一致策略（更穩）([GitHub][5])

Whispy 這類研究也是走「累積短 chunk + shifting buffer」的 streaming 思路。([arXiv][7])

---

## 小提醒：最常見的坑

* **overlap 太小**：邊界漏字變多
* **overlap 太大**：重複字變多 + 成本上升
* **prompt 帶太長**：Whisper 可能 drift / 幻覺更嚴重
* **沒做穩定提交**：UI 文字會一直抖動，體驗很差

---

[1]: https://huggingface.co/openai/whisper-large-v3?utm_source=chatgpt.com "openai/whisper-large-v3"
[2]: https://github.com/ggerganov/whisper.cpp/blob/master/examples/stream/README.md?utm_source=chatgpt.com "whisper.cpp/examples/stream/README.md at master · ..."
[3]: https://musharp.com/building-a-voice-assistant-from-scratch-using-livekit-whisper-gpt/?utm_source=chatgpt.com "Building a Voice Assistant from Scratch using LiveKit ..."
[4]: https://arxiv.org/html/2303.00747v2?utm_source=chatgpt.com "Time-Accurate Speech Transcription of Long-Form Audio"
[5]: https://github.com/ufal/whisper_streaming?utm_source=chatgpt.com "ufal/whisper_streaming: Whisper realtime streaming for ..."
[6]: https://github.com/jianfch/stable-ts?utm_source=chatgpt.com "jianfch/stable-ts: Transcription, forced alignment, and audio ..."
[7]: https://arxiv.org/html/2405.03484v1?utm_source=chatgpt.com "Adapting STT Whisper Models to Real-Time Environments"
