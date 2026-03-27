Sherpa-ONNX 必须随 APK 打入 native 库，否则日志会出现：
  dlopen failed: library "libsherpa-onnx-jni.so" not found
以及 KeywordSpotter 的 NoClassDefFoundError（由 JNI 未加载引起）。

目录结构（与 defaultConfig.ndk.abiFilters 一致）：

  jniLibs\armeabi-v7a\*.so
  jniLibs\arm64-v8a\*.so
  jniLibs\x86\*.so
  jniLibs\x86_64\*.so

真机多数是 arm64-v8a，模拟器可能是 x86_64。

官方包一般至少包含：
  libsherpa-onnx-jni.so
  libonnxruntime.so
（不同版本可能还有其它 .so，请整目录复制。）

自动拉取（需能访问 GitHub；模型另从 Hugging Face 拉）：

  cd E:\...\ClawClient\android
  powershell -ExecutionPolicy Bypass -File .\scripts\fetch-sherpa-kws-assets.ps1

勿使用 -SkipJni，除非你已手工拷齐各 ABI 的 .so。完成后务必重新编译安装 APK。

手动：从 sherpa-onnx Releases 下载 sherpa-onnx-v*-android.tar.bz2，解压后在深层目录找到 arm64-v8a
等文件夹，整夹拷入上述 jniLibs 路径。
