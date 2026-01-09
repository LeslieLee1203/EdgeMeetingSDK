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
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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