#!/bin/bash

set -euo pipefail

readonly android_api=29
readonly expected_cmake_version=3.31.5
readonly expected_ndk_revision=29.0.14206865
readonly expected_vcpkg_commit=74e6536215718009aae747d86d84b78376bf9e09
readonly requested_abi="${1:-}"
readonly repository_root="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  echo "ANDROID_NDK_HOME must point to Android NDK 29.0.14206865" >&2
  exit 1
fi

if [ -z "${VCPKG_ROOT:-}" ]; then
  echo "VCPKG_ROOT must point to vcpkg 2025.10.17" >&2
  exit 1
fi

case "$requested_abi" in
  arm64-v8a)
    readonly vcpkg_target_triplet=arm64-android
    ;;
  x86_64)
    readonly vcpkg_target_triplet=x64-android
    ;;
  *)
    echo "Unsupported Android ABI '$requested_abi'; expected arm64-v8a or x86_64" >&2
    exit 1
    ;;
esac

readonly vcpkg_toolchain_file="$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"
readonly android_toolchain_file="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
readonly build_dir="$repository_root/build/android/$requested_abi/wrapper"
readonly android_cmake_hook="$repository_root/android/native/ValhallaAndroid.cmake"
readonly android_jni_source="$repository_root/android/native/valhalla_jni.cpp"

test -f "$vcpkg_toolchain_file"
test -f "$android_toolchain_file"
test -f "$android_cmake_hook"
test -f "$android_jni_source"
grep -Fq "Pkg.Revision = $expected_ndk_revision" "$ANDROID_NDK_HOME/source.properties"
test "$(git -C "$VCPKG_ROOT" rev-parse HEAD)" = "$expected_vcpkg_commit"
test "$(cmake --version | sed -n '1s/^cmake version \([0-9.]*\).*/\1/p')" = "$expected_cmake_version"
command -v ninja >/dev/null

cmake \
  -S "$repository_root/src" \
  -B "$build_dir" \
  --fresh \
  -Wno-dev \
  -G Ninja \
  -DCMAKE_WARN_DEPRECATED=OFF \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_EXPORT_COMPILE_COMMANDS=ON \
  -DCMAKE_PROJECT_INCLUDE="$android_cmake_hook" \
  -DCMAKE_TOOLCHAIN_FILE="$vcpkg_toolchain_file" \
  -DVCPKG_CHAINLOAD_TOOLCHAIN_FILE="$android_toolchain_file" \
  -DVCPKG_OVERLAY_TRIPLETS="$repository_root/triplets" \
  -DVCPKG_TARGET_TRIPLET="$vcpkg_target_triplet" \
  -DANDROID_ABI="$requested_abi" \
  -DANDROID_PLATFORM="android-$android_api" \
  -DANDROID_STL=c++_static

cmake --build "$build_dir" --config Release --parallel

readonly compile_commands="$build_dir/compile_commands.json"
test -f "$compile_commands"
readonly compile_commands_count="$(
  grep -Ec '"file": ".*android/native/valhalla_jni.cpp"' "$compile_commands"
)"
readonly ninja_commands_count="$(
  ninja -C "$build_dir" -t commands valhalla-wrapper \
    | grep -F 'android/native/valhalla_jni.cpp' \
    | grep -c -- ' -c '
)"
test "$compile_commands_count" -eq 1
test "$ninja_commands_count" -eq 1
