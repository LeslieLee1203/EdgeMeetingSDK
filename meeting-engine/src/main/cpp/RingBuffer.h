//
// Created by Leslie Lee 李俊德 (奧圖碼) on 2026/1/15.
//

#ifndef EDGEMEETINGSDK_RINGBUFFER_H
#define EDGEMEETINGSDK_RINGBUFFER_H

#include <vector>
#include <mutex>
#include <algorithm>

/**
 * Thread-Safe 環形緩衝區。
 * 用途：緩衝 Oboe (生產者) 的資料，供 Worker Thread (消費者) 讀取。
 */
template <typename T>
class RingBuffer {
public:
    explicit RingBuffer(size_t capacity) : buffer(capacity), max_size(capacity) {
        read_index = 0;
        write_index = 0;
    }

    // 寫入資料 (Oboe Thread 呼叫)
    size_t write(const T* data, size_t count) {
        std::lock_guard<std::mutex> lock(mutex);

        size_t available = max_size - (write_index - read_index);
        if (available == 0) return 0; // 滿了 (Overrun)

        size_t to_write = std::min(count, available);
        size_t current_write_pos = write_index % max_size;
        size_t first_chunk = std::min(to_write, max_size - current_write_pos);

        // 複製第一段
        std::copy(data, data + first_chunk, buffer.begin() + current_write_pos);

        // 折返複製第二段
        if (first_chunk < to_write) {
            std::copy(data + first_chunk, data + to_write, buffer.begin());
        }

        write_index += to_write;
        return to_write;
    }

    // 讀取資料 (Worker Thread 呼叫)
    size_t read(T* dest, size_t count) {
        std::lock_guard<std::mutex> lock(mutex);

        size_t available = write_index - read_index;
        if (available == 0) return 0; // 空的 (Underrun)

        size_t to_read = std::min(count, available);
        size_t current_read_pos = read_index % max_size;
        size_t first_chunk = std::min(to_read, max_size - current_read_pos);

        // 讀取第一段
        std::copy(buffer.begin() + current_read_pos, buffer.begin() + current_read_pos + first_chunk, dest);

        // 折返讀取第二段
        if (first_chunk < to_read) {
            std::copy(buffer.begin(), buffer.begin() + (to_read - first_chunk), dest + first_chunk);
        }

        read_index += to_read;

        // 簡單的重置機制，防止 index 無限增長溢位
        if (read_index == write_index) {
            read_index = 0;
            write_index = 0;
        }

        return to_read;
    }

    // 取得目前可讀取的數量
    size_t availableRead() const {
        // 這裡不加鎖，僅供快速檢查
        return write_index - read_index;
    }

private:
    std::vector<T> buffer;
    size_t max_size;
    size_t read_index;
    size_t write_index;
    std::mutex mutex;
};

#endif //EDGEMEETINGSDK_RINGBUFFER_H
