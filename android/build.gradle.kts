plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9는 Kotlin을 내장해요. 아래 줄은 Kotlin 버전을 고정하는 용도
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
