// Versions pinned to a set that builds with compileSdk/targetSdk 36 on AGP 8.13.
// Newer Compose/Lifecycle releases (mid-2026 on) need compileSdk 37 and AGP 9.2+.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
