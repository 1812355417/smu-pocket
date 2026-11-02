// 顶层构建脚本。版本号刻意保守（AGP 8.7.3 / Kotlin 2.0.21），
// 你本机 Android Studio 若提示可升级，忽略即可；升级前先确认 Compose compiler 插件版本跟着 Kotlin 一起改。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
