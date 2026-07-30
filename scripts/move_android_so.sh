#!/bin/bash

set -euo pipefail

readonly repository_root="$(cd "$(dirname "$0")/.." && pwd)"
readonly requested_abi="${1:-}"

case "$requested_abi" in
  arm64-v8a|x86_64) ;;
  *)
    echo "Unsupported Android ABI '$requested_abi'; expected arm64-v8a or x86_64" >&2
    exit 1
    ;;
esac

readonly source_library="$repository_root/build/android/$requested_abi/wrapper/wrapper/libvalhalla-wrapper.so"
readonly destination="$repository_root/android/valhalla/build/generated/jniLibs/$requested_abi"

test -f "$source_library"
mkdir -p "$destination"
cp "$source_library" "$destination/"
