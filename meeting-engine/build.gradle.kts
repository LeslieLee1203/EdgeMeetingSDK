plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.edgemeeting.engine"

    // 保留原本的 Preview SDK 設定
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // 【關鍵設定 1】C++ 編譯參數
        externalNativeBuild {
            cmake {
                // Oboe 必須使用 shared STL (c++_shared)
                arguments("-DANDROID_STL=c++_shared")
                // 使用現代 C++17 標準
                cppFlags("-std=c++17")
            }
        }

        // 【關鍵設定 1.1】ABI 過濾器（只編譯 arm64-v8a）
        // 為什麼只編譯 arm64-v8a：
        // 1. RK3588 是 ARM64 架構
        // 2. RKNN Runtime 只提供 arm64-v8a 版本
        // 3. 減少 APK 大小與編譯時間
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // 【關鍵設定 2】開啟 Prefab (自動處理 Native 依賴的神器)
    buildFeatures {
        prefab = true
    }

    // 【關鍵設定 3】指定 CMakeLists 路徑
    // 注意：等一下我們必須馬上建立這個檔案，否則 Sync 會失敗
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // 【關鍵設定 4】解決 libc++_shared.so 衝突 (未來整合 App 時必備)
    packaging {
        jniLibs {
            pickFirsts.add("**/libc++_shared.so")
            // 確保 librknnrt.so 被打包（雖然預設會打包 jniLibs，但明確聲明更清晰）
            // pickFirsts.add("**/librknnrt.so")  // 若有多個來源的 librknnrt.so 才需要
        }
    }

    // 【關鍵設定 5】明確指定 jniLibs 來源目錄（確保 librknnrt.so 被打包）
    // Android Gradle Plugin 預設會包含 src/main/jniLibs，這裡明確聲明以提高可讀性
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    buildTypes {
        debug {
            // Debug build: 啟用所有開發用 log
            externalNativeBuild {
                cmake {
                    // 不定義 NDEBUG，Log.h 中的 LOGD/LOGI/LOGW 會正常輸出
                    cppFlags("-O0", "-g")
                }
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release build: 移除開發用 log，只保留 LOGE 和 LOG_RESULT
            externalNativeBuild {
                cmake {
                    // 定義 NDEBUG 讓 Log.h 中的 LOGD/LOGI/LOGW 變成空操作
                    cppFlags("-DNDEBUG", "-O3")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // 【關鍵設定 6】單元測試配置
    // 為什麼需要 returnDefaultValues：
    // 1. Android SDK 類別（如 android.util.Log）在 JVM 單元測試中沒有實作
    // 2. 設定 true 後，未 mock 的 Android 方法會回傳預設值（0, null, false 等）
    // 3. 這讓我們能在不使用 Robolectric 的情況下執行基本單元測試
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // 依賴核心介面
    implementation(project(":meeting-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // 【關鍵設定 5】Google Oboe (透過 Prefab)
    implementation(libs.oboe)

    // 【關鍵設定 6】Coroutines (用於 StateFlow)
    implementation(libs.kotlinx.coroutines.core)

    // 測試相關
    testImplementation(libs.junit)
    // 用於測試 Coroutines (runTest, TestScope)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}