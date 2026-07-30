import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val supportedAbis = setOf("arm64-v8a", "x86_64")
val requestedAbis =
    providers.gradleProperty("valhallaAbis")
        .map { value -> value.split(",").map(String::trim).filter(String::isNotEmpty).toSet() }
        .orElse(supportedAbis)
        .get()
val usePrebuiltNative =
    providers.gradleProperty("valhallaUsePrebuiltNative")
        .map(String::toBooleanStrict)
        .orElse(false)
        .get()

if (requestedAbis.isEmpty() || !supportedAbis.containsAll(requestedAbis)) {
    throw GradleException(
        "valhallaAbis must contain only: ${supportedAbis.sorted().joinToString(", ")}"
    )
}

android {
    namespace = "io.github.papagrationbizsketch.valhalla"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += requestedAbis
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val prepareNativeTasks =
    requestedAbis.map { abi ->
        val taskSuffix =
            when (abi) {
                "arm64-v8a" -> "Arm64V8a"
                "x86_64" -> "X8664"
                else -> error("Unsupported ABI: $abi")
            }
        val nativeLibrary =
            rootProject.layout.projectDirectory.file(
                "../build/android/$abi/wrapper/wrapper/libvalhalla-wrapper.so"
            )
        val buildNative =
            tasks.register<Exec>("buildValhalla$taskSuffix") {
                group = "build"
                description = "Build the Valhalla native library for $abi"
                workingDir = rootProject.layout.projectDirectory.dir("..").asFile
                commandLine("bash", "scripts/build_android.sh", abi)
                outputs.file(nativeLibrary)
                onlyIf("valhallaUsePrebuiltNative is not enabled") { !usePrebuiltNative }
            }

        tasks.register<Copy>("prepareValhalla$taskSuffix") {
            group = "build"
            description = "Stage the Valhalla native library for $abi"
            dependsOn(buildNative)
            from(nativeLibrary)
            into(layout.buildDirectory.dir("generated/jniLibs/$abi"))
            doFirst {
                if (!nativeLibrary.asFile.isFile) {
                    throw GradleException(
                        "Missing native library for $abi: ${nativeLibrary.asFile}. " +
                            "Build it first or supply it with valhallaUsePrebuiltNative=true."
                    )
                }
            }
        }
    }

tasks.named("preBuild") {
    dependsOn(prepareNativeTasks)
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
