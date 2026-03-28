# Valhalla Mobile

[![Valhalla](https://img.shields.io/badge/Valhalla-3.6.2-blue)](https://github.com/valhalla/valhalla/releases/tag/3.6.2)
[![](https://img.shields.io/endpoint?url=https%3A%2F%2Fswiftpackageindex.com%2Fapi%2Fpackages%2FRallista%2Fvalhalla-mobile%2Fbadge%3Ftype%3Dswift-versions)](https://swiftpackageindex.com/Rallista/valhalla-mobile)
[![](https://img.shields.io/endpoint?url=https%3A%2F%2Fswiftpackageindex.com%2Fapi%2Fpackages%2FRallista%2Fvalhalla-mobile%2Fbadge%3Ftype%3Dplatforms)](https://swiftpackageindex.com/Rallista/valhalla-mobile)

This project builds [valhalla](https://github.com/valhalla/valhalla) as a static iOS library.

It currently only exposes the route function for the primary purpose of generating turn by turn navigation routes
using a downloaded pre-parsed valhalla tileset.

We welcome contributions to expand the functionality of this library. See our [CONTRIBUTING.md](CONTRIBUTING.md)
for more information.
If you've got questions, would like to have informal discussions, or just want to ping us about a question, PR. Feel free 
to reach out on the OpenStreetMap Slack (osmus.slack.com) under the [#valhalla-mobile](`https://osmus.slack.com/archives/C08N6SUNZTJ`) channel.

## Setup

### iOS

In a swift package:

```swift
let package = Package(
    dependencies: [
        .package(url: "https://github.com/rallista/valhalla-mobile.git", from: "0.1.0"),
    ],
    targets: [
        .target(
            dependencies: [
                .product(name: "Valhalla", package: "valhalla-mobile")
            ]
        ),
    ]
)
```

## Manually Building Valhalla C++

Fetching submodules

```sh
git submodule update --init --recursive
```

Set up VCPKG

```sh
git clone https://github.com/microsoft/vcpkg && git -C vcpkg checkout 2025.12.12
./vcpkg/bootstrap-vcpkg.sh
export VCPKG_ROOT=`pwd`/vcpkg
```

### iOS Swift Package

On iOS, you must pre-build the xcframework using the command:

```sh
./build.sh ios clean
```

## Valhalla Fork

This project uses our fork of valhalla at <https://github.com/Rallista/valhalla> as a submodule. If a feature is missing, please
open an issue or PR on that repository to upgrade it to valhalla's latest version.

## References

- Valhalla <https://github.com/valhalla/valhalla>
- Swift Package Manager C++ (for fun - this repo takes the old approach) <https://www.swift.org/documentation/articles/wrapping-c-cpp-library-in-swift.html>
