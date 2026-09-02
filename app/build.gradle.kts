import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.notifyguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.notifyguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.5.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "notifyguard"
            keyAlias = "notifyguard"
            keyPassword = "notifyguard"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        // getByName 而不是 release {}，和上面的 signingConfigs 保持一致的写法。
        getByName("release") {
            // 用仓库里那把 debug 密钥签 release，好处是 Release 页的包和 Actions 的
            // debug 包签名一致、可以互相覆盖安装，而且不需要任何 secret。
            // 代价：仓库是 public，这把密钥和口令都是公开的 —— 任何人都能签出一个
            // 能覆盖你已装应用的 APK。只适合个人自用分发，别拿去上应用市场。
            // 可用 gradlew :app:signingReport 核对 release 变体确实用了这把密钥。
            signingConfig = signingConfigs.getByName("debug")

            // 暂不开 R8：这台机器拉不到 dl.google.com 的依赖、跑不完整构建，
            // 混淆一旦弄坏 Compose 运行时只有装到手机上才会发现。等能本地验证了再开。
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
}
