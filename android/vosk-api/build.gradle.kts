/**
 * 极薄封装：仅通过 api() 向上暴露 Vosk AAR。
 * 部分 Android Studio 版本对 app 模块直接 implementation 的 Maven AAR（仅含 Java 类）索引不完整，
 * 经本地工程模块传递后，Kotlin/IDE  classpath 通常可稳定解析 org.vosk.*。
 */
plugins {
    id("com.android.library")
}

android {
    namespace = "ai.openclaw.voskapi"
    compileSdk = 36
    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api("net.java.dev.jna:jna:5.18.1@aar")
    api("com.alphacephei:vosk-android:0.3.75")
}
