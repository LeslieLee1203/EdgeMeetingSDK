# meeting-engine/src/main/cpp/CMakeLists.txt

# 設定最低版本 (配合 Gradle 設定)
cmake_minimum_required(VERSION 3.22.1)

# 定義專案名稱
project("meeting-engine")

# 1. 尋找 Oboe 套件
# 關鍵：因為我們在 build.gradle 開啟了 Prefab，這裡可以直接 find_package
find_package(oboe REQUIRED CONFIG)

# 2. 定義你的 Native Library
add_library(
        meeting-engine
        SHARED
        # 這裡指定你的 C++ 原始碼檔案
        native-lib.cpp
)

# 3. 連結 Library
target_link_libraries(
        meeting-engine
        # 連結 Oboe (注意 namespace 是 oboe::oboe)
        oboe::oboe
        # Android 系統 Log 工具
        log
        # Android 系統 Audio 工具
        android
)