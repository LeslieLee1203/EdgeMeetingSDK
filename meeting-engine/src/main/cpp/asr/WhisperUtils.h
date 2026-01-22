#ifndef MEETING_ENGINE_WHISPER_UTILS_H
#define MEETING_ENGINE_WHISPER_UTILS_H

#include <vector>
#include <string>
#include <cmath>

#define VOCAB_NUM 51865
#define MAX_TOKENS 12
#define SAMPLE_RATE 16000
#define N_FFT 400
#define HOP_LENGTH 160
#define CHUNK_LENGTH 20
#define MAX_AUDIO_LENGTH (CHUNK_LENGTH * SAMPLE_RATE)
#define N_MELS 80
#define MELS_FILTERS_SIZE 201 // (N_FFT / 2 + 1)
#define ENCODER_INPUT_SIZE (CHUNK_LENGTH * 100)
#define ENCODER_OUTPUT_SIZE (CHUNK_LENGTH * 50 * 512) // base model
#define DECODER_INPUT_SIZE ENCODER_OUTPUT_SIZE

// 簡單的 PI 定義
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

typedef struct {
    int index;
    char *token;
} VocabEntry;

// 讀取詞彙表
int read_vocab(const char *fileName, VocabEntry *vocab);

// 讀取 Mel Filters
int read_mel_filters(const char *fileName, float *data, int max_lines);

// 音訊前處理：PCM -> Mel Spectrogram
// input: audio_data (float array), audio_len (samples)
// output: x_mel (flattened 2D array)
void audio_preprocess(const float* audio_data, int audio_len, float *mel_filters, std::vector<float> &x_mel);

// 找出最大值索引
int argmax(float *array);

// Base64 解碼 (用於中文 tokens)
std::string base64_decode(const std::string &s);

// 字串替換
void replace_substr(std::string &str, const std::string &from, const std::string &to);

#endif // MEETING_ENGINE_WHISPER_UTILS_H
