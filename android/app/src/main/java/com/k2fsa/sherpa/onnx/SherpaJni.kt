// Copyright (c)  2024  Xiaomi Corporation
package com.k2fsa.sherpa.onnx

/**
 * 统一加载 Sherpa ONNX 依赖的 .so；须先加载 onnxruntime，再加载 sherpa-onnx-jni（与官方 Android 预编译包一致）。
 */
object SherpaJni {
  @Volatile
  private var loaded = false

  @Synchronized
  fun ensureLoaded() {
    if (loaded) return
    for (name in listOf("onnxruntime", "onnxruntime4j_jni")) {
      try {
        System.loadLibrary(name)
        break
      } catch (_: UnsatisfiedLinkError) {
        continue
      }
    }
    System.loadLibrary("sherpa-onnx-jni")
    loaded = true
  }
}
