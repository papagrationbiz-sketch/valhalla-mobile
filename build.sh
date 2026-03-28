#!/bin/bash

# Fail on any error
set -e

ios_archs=("arm64-ios" "arm64-ios-simulator" "x64-ios-simulator")

build_ios() {
    local arch=$1
    if [ -z "$arch" ]; then
        echo "Building for all iOS architectures..."
        for arch in "${ios_archs[@]}"; do
            echo "Building for iOS architecture: $arch"
            ./scripts/build_apple.sh "$arch"
        done
    else
        echo "Building for iOS architecture: $arch"
        ./scripts/build_apple.sh "$arch"
    fi
}

if [ -d "$(pwd)/vcpkg" ]; then
  export VCPKG_ROOT="$(pwd)/vcpkg"
else
  # If VCPKG_ROOT is not set and vcpkg directory doesn't exist locally
  if [ -z "${VCPKG_ROOT+x}" ]; then
    echo "VCPKG_ROOT is not set and vcpkg directory not found in the current working directory."
    echo "Review setup in README.md or export a custom $VCPKG_ROOT."
    exit 1
  fi
fi

platform=""
arch=""
clean=false

while [[ $# -gt 0 ]]; do
    case $1 in
        ios|all)
            platform=$1
            ;;
        --ios)
            platform="ios"
            arch=$2
            shift
            ;;
        clean)
            clean=true
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
    shift
done

# Handle cleaning
if [ "$clean" = true ]; then
    if [ "$platform" == "ios" ]; then
        echo "Cleaning the iOS build directory..."
        rm -rf build/apple
    else
        echo "Cleaning the build directory..."
        rm -rf build
    fi
fi

# Build based on platform
if [ "$platform" == "ios" ] || [ "$platform" == "all" ]; then
    build_ios "$arch"
    if [ -z "$arch" ]; then
        echo "Creating xcframework..."
        ./scripts/create_xcframework.sh
    fi
fi

echo "Done!"
