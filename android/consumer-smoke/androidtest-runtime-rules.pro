# Rules required only to run instrumentation tests against the minified release
# variant. They are NOT part of what the Valhalla AAR must supply to consumers:
# proguard-rules.pro stays empty so the AAR keeps being validated on its own.
#
# testBuildType is "release", so AndroidJUnitRunner and its androidx.test /
# kotlinx.coroutines dependencies run inside this minified app process. Those
# libraries are written in Kotlin and resolve the Kotlin runtime from the app,
# which R8 otherwise strips because the app's own code never needs it.
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
