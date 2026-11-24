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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
}
