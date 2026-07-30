# Android SDK Restoration

Issue #12 restores the minimum Android library needed to call the existing Valhalla JNI route
bridge. It does not restore the removed typed API or publication configuration.

## Provenance and scope

- Android reference tree: `69dac8ff2013b67a4358b20189f261b15583df3a`
- Android deletion commit: `9fe8bfb0966b59cf76a34711beb277948af45f06`
- implementation base: `12f6002bb46fe7e99dde313049c8d5c717350ca3` or later
- Valhalla submodule: `13eb61624f9b6464dbaad5081e9fd71207ec539c`
- supported ABIs: `arm64-v8a`, `x86_64`
- minimum Android API: 29

The fixture tile archive and route coordinates come from the reference tree. The Gradle module,
public Kotlin API, generated JNI library staging, and CI are rebuilt for the current repository.

The following reference-tree features are intentionally omitted:

- Moshi and Valhalla/OSRM OpenAPI models
- typed request and response classes
- 32-bit ABIs
- Maven publishing and signing
- Dokka and upstream publication metadata
- Prefab native headers
- exact full-response fixture comparisons

Typed Android API parity is deferred to Issue #13.

## Toolchain

| Component | Version |
| --- | --- |
| Android Gradle plugin | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.3.21 |
| JDK | 17 |
| compile SDK | 36 |
| build tools | 35.0.0 |
| NDK | 29.0.14206865 |
| CMake | 3.31.5 |
| vcpkg | `2025.10.17` (`74e6536215718009aae747d86d84b78376bf9e09`) |

Install the Android SDK components side by side. Set `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME`, and
`VCPKG_ROOT` without changing the host's global JDK or Xcode configuration. Put the pinned CMake
`bin` directory on `PATH`.

## Build

Build both supported ABIs into one AAR:

```sh
cd android
./gradlew :valhalla:assembleDebug
```

Build one ABI:

```sh
./gradlew :valhalla:assembleDebug -PvalhallaAbis=x86_64
```

Native libraries are generated under `build/android` and copied into the module build directory.
They are not committed to `src/main/jniLibs`.

CI packaging and Emulator jobs consume native artifacts produced by the native jobs with
`-PvalhallaUsePrebuiltNative=true`. That explicit mode never invokes CMake or vcpkg and fails if an
expected `.so` is missing.

## API

```kotlin
import io.github.papagrationbizsketch.valhalla.Valhalla

val actor = Valhalla("/absolute/path/to/valhalla.json")
val responseJson = actor.route(requestJson)
```

Requests and responses use Valhalla's raw JSON wire format. Native failures are returned as JSON
objects with `code` and `message`.

The internal `com.valhalla.valhalla.ValhallaKotlin` package is retained only to match the existing
JNI symbol. It is not public API.

## Validation

Issue #12 requires:

1. native build for `arm64-v8a` and `x86_64`;
2. AAR and ELF inspection proving that only those ABIs are packaged;
3. x86_64 Emulator native loading;
4. a successful route against the fixed tile fixture;
5. no changes to the Valhalla submodule, shared C++ wrapper, Swift package, Apple code, or release
   workflow.

An arm64 physical-device route remains required before the `0.6.0` release, but is deferred from
the Issue #12 merge gate to the Android SDK integration/performance work.
