pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // 与 Gradle 文档一致；部分插件/元数据不在 Central，缺少此项可能误报无法解析某些坐标
    gradlePluginPortal()
  }
}

rootProject.name = "OpenClawNodeAndroid"
include(":app")
include(":benchmark")
include(":vosk-api")
