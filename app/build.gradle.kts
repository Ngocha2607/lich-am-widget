plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Mỗi lần GitHub build, số phiên bản tự tăng để cài đè được bản cũ
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "vn.lichthoitiet"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.lichthoitiet"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Khóa ký cố định: các bản build sau cài đè lên bản trước được
    signingConfigs {
        create("shared") {
            storeFile = file("lichthoitiet.keystore")
            storePassword = "lichthoitiet"
            keyAlias = "lichthoitiet"
            keyPassword = "lichthoitiet"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Jetpack Compose cho màn hình native la bàn AR (Compose Compiler 1.5.14 đi với Kotlin 1.9.24)
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Compose: giao diện la bàn AR
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")

    // CameraX: khung hình camera cho la bàn AR
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    testImplementation("junit:junit:4.13.2")
    // org.json thật cho unit test (android.jar chỉ có bản rỗng)
    testImplementation("org.json:json:20240303")
}
