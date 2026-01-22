#include "WhisperUtils.h"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <vector>
#include <iostream>
#include "fftw3.h"

// 實作 transpose
static void transpose(fftwf_complex *input, int input_rows, int input_cols, fftwf_complex *output) {
    for (int i = 0; i < input_rows; ++i) {
        for (int j = 0; j < input_cols; ++j) {
            int input_index = i * input_cols + j;
            int output_index = j * input_rows + i;
            output[output_index][0] = input[input_index][0];
            output[output_index][1] = input[input_index][1];
        }
    }
}

// 實作 compute_magnitude
static float compute_magnitude(const fftwf_complex &value) {
    return value[0] * value[0] + value[1] * value[1];
}

static void compute_magnitudes(fftwf_complex *stft_result, int num_mel_filters, int num_frames, std::vector<float> &magnitudes) {
    int k = 0;
    for (int i = 0; i < num_mel_filters; i++) {
        for (int j = 0; j < num_frames - 1; j++) {
            magnitudes[k] = compute_magnitude(stft_result[i * num_frames + j]);
            k++;
        }
    }
}

// 實作 hann_window
static void hann_window(std::vector<float> &window, int length) {
    for (int i = 0; i < length; i++) {
        window[i] = 0.5 * (1 - cos(2 * M_PI * i / (length - 1)));
    }
}

// 實作 reflect_pad
static void reflect_pad(const std::vector<float> &audio, std::vector<float> &padded_audio, int pad_width) {
    std::copy(audio.begin(), audio.end(), padded_audio.begin() + pad_width);
    std::reverse_copy(audio.begin(), audio.begin() + pad_width, padded_audio.begin());
    std::reverse_copy(audio.end() - pad_width, audio.end(), padded_audio.end() - pad_width);
}

// STFT 實作
static void stfts(const std::vector<float> &audio, int audio_length, int window_length, int hop_length, const std::vector<float> &window, fftwf_complex *stft_result, int num_frames) {
    float *input = (float *)fftwf_malloc(sizeof(float) * window_length);
    fftwf_complex *output = (fftwf_complex *)fftwf_malloc(sizeof(fftwf_complex) * (window_length / 2 + 1));
    fftwf_plan plan = fftwf_plan_dft_r2c_1d(window_length, input, output, FFTW_ESTIMATE);

    for (int i = 0; i < num_frames; i++) {
        int start = i * hop_length;
        
        for (int j = 0; j < window_length; j++) {
            if (start + j < audio_length) {
                input[j] = audio[start + j] * window[j];
            } else {
                input[j] = 0.0f;
            }
        }

        fftwf_execute(plan);
        memcpy(stft_result + i * (window_length / 2 + 1), output, sizeof(fftwf_complex) * (window_length / 2 + 1));
    }

    fftwf_free(input);
    fftwf_free(output);
    fftwf_destroy_plan(plan);
}

// 矩陣乘法
static void matmul(float *A, float *B, std::vector<float> &C, int ROWS_A, int COLS_A, int COLS_B) {
    for (int i = 0; i < ROWS_A; i++) {
        for (int j = 0; j < COLS_B; j++) {
            float sum = 0.0f;
            for (int k = 0; k < COLS_A; k++) {
                sum += A[i * COLS_A + k] * B[k * COLS_B + j];
            }
            C[i * COLS_B + j] = sum;
        }
    }
}

static void clamp_and_log_max(std::vector<float> &mel_spec, int rows, int cols) {
    float min_val = 1e-10;
    float scaling_factor = 1.0 / 4.0;
    float shift_value = 4.0;

    float max_val = mel_spec[0];
    for (int i = 0; i < rows * cols; ++i) {
        float value = mel_spec[i];
        value = (value < min_val) ? min_val : value;
        mel_spec[i] = log10f(value);

        if (mel_spec[i] > max_val)
            max_val = mel_spec[i];
    }

    float threshold = max_val - 8.0;
    for (int i = 0; i < rows * cols; ++i) {
        mel_spec[i] = (std::max(mel_spec[i], threshold) + shift_value) * scaling_factor;
    }
}

static void pad_x_mel(const std::vector<float> input, int rows_input, int cols_input, std::vector<float> &output, int cols_output) {
    for (int i = 0; i < rows_input; ++i) {
        std::copy(input.begin() + i * cols_input, input.begin() + (i + 1) * cols_input, output.begin() + i * cols_output);
    }
}

static void log_mel_spectrogram(const float *audio_data, int audio_length, int cur_num_frames_of_stfts, float *filters, std::vector<float> &mel_spec) {
    std::vector<float> window(N_FFT);
    hann_window(window, N_FFT);

    std::vector<float> audio(audio_data, audio_data + audio_length);
    int padded_size = audio_length + N_FFT;
    std::vector<float> padded_audio(padded_size);
    reflect_pad(audio, padded_audio, N_FFT / 2);

    fftwf_complex *stfts_result = (fftwf_complex *)fftwf_malloc(sizeof(fftwf_complex) * MELS_FILTERS_SIZE * cur_num_frames_of_stfts);
    
    stfts(padded_audio, audio_length + N_FFT, N_FFT, HOP_LENGTH, window, stfts_result, cur_num_frames_of_stfts);

    fftwf_complex *stfts_result_t = (fftwf_complex *)fftwf_malloc(sizeof(fftwf_complex) * MELS_FILTERS_SIZE * cur_num_frames_of_stfts);
    transpose(stfts_result, cur_num_frames_of_stfts, MELS_FILTERS_SIZE, stfts_result_t);

    std::vector<float> magnitudes(MELS_FILTERS_SIZE * (cur_num_frames_of_stfts - 1));
    compute_magnitudes(stfts_result_t, MELS_FILTERS_SIZE, cur_num_frames_of_stfts, magnitudes);

    int ROWS_A = N_MELS;
    int COLS_A = MELS_FILTERS_SIZE;
    int COLS_B = cur_num_frames_of_stfts - 1;

    matmul(filters, magnitudes.data(), mel_spec, ROWS_A, COLS_A, COLS_B);

    clamp_and_log_max(mel_spec, ROWS_A, COLS_B);

    fftwf_free(stfts_result);
    fftwf_free(stfts_result_t);
}

void audio_preprocess(const float* audio_data, int audio_len, float *mel_filters, std::vector<float> &x_mel) {
    int audio_length = audio_len;
    
    // Ensure output vector is sized correctly for the Encoder
    // Encoder expects [1, 80, 2000] -> 160,000 floats
    // This resize is CRITICAL to prevent SIGSEGV
    x_mel.resize(N_MELS * ENCODER_INPUT_SIZE);
    
    if (audio_length >= MAX_AUDIO_LENGTH) {
        std::vector<float> trim_audio_data(audio_data, audio_data + MAX_AUDIO_LENGTH);
        int cur_num_frames_of_stfts = MAX_AUDIO_LENGTH / HOP_LENGTH + 1;
        log_mel_spectrogram(trim_audio_data.data(), MAX_AUDIO_LENGTH, cur_num_frames_of_stfts, mel_filters, x_mel);
    } else {
        int cur_num_frames_of_stfts = audio_length / HOP_LENGTH + 1;
        int x_mel_rows = N_MELS;
        int x_mel_cols = cur_num_frames_of_stfts - 1;
        int x_mel_cols_pad = MAX_AUDIO_LENGTH / HOP_LENGTH;
        
        std::vector<float> cur_x_mel(x_mel_rows * x_mel_cols, 0.0f);
        log_mel_spectrogram(audio_data, audio_length, cur_num_frames_of_stfts, mel_filters, cur_x_mel);
        pad_x_mel(cur_x_mel, x_mel_rows, x_mel_cols, x_mel, x_mel_cols_pad);
    }
}

int read_vocab(const char *fileName, VocabEntry *vocab) {
    FILE *fp = fopen(fileName, "r");
    if (fp == NULL) {
        return -1;
    }

    char line[512];
    int count = 0;
    while (fgets(line, sizeof(line), fp)) {
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
    return 0;
}

int read_mel_filters(const char *fileName, float *data, int max_lines) {
    FILE *file = fopen(fileName, "r");
    if (file == NULL) return -1;

    int line_count = 0;
    while (line_count < max_lines && fscanf(file, "%f", &data[line_count]) == 1) {
        line_count++;
    }
    fclose(file);
    return 0;
}

int argmax(float *array) {
    int offset = (MAX_TOKENS - 1) * VOCAB_NUM;
    float max_value = array[offset];
    int max_index = offset;
    
    for (int i = 1; i < VOCAB_NUM; i++) {
        if (array[offset + i] > max_value) {
            max_value = array[offset + i];
            max_index = offset + i;
        }
    }
    
    return max_index - offset;
}

static int32_t get_char_index(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return 0;
}

std::string base64_decode(const std::string &encoded_string) {
    if (encoded_string.empty()) return "";
    return encoded_string; // 暫時保留原樣，需實作 base64 logic 才能支援中文
}

void replace_substr(std::string &str, const std::string &from, const std::string &to) {
    if (from.empty()) return;
    size_t start_pos = 0;
    while ((start_pos = str.find(from, start_pos)) != std::string::npos) {
        str.replace(start_pos, from.length(), to);
        start_pos += to.length();
    }
}
