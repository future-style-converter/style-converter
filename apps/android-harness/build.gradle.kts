// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9 has built-in Kotlin support: org.jetbrains.kotlin.android is no longer
// applied (AGP rejects it) — only the compiler-plugin companions remain, and
// their 2.4.0 version pulls the KGP runtime AGP's built-in Kotlin uses.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
