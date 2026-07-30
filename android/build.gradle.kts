plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val sharedVersion = rootProject.file("../version.txt").readText().trim()

allprojects {
    version = sharedVersion
}
