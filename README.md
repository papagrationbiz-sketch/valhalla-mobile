# Valhalla Mobile

[![Valhalla](https://img.shields.io/badge/Valhalla-3.6.2-blue)](https://github.com/valhalla/valhalla/releases/tag/3.6.2)

This project builds [Valhalla](https://github.com/valhalla/valhalla) as a static iOS library. The
Apple wrapper exposes routing and trace APIs for downloaded, pre-parsed Valhalla tilesets.

## Repository Identity and Upstream

The canonical repository is
[`papagrationbiz-sketch/valhalla-mobile`](https://github.com/papagrationbiz-sketch/valhalla-mobile).
It is a fork of
[`Rallista/valhalla-mobile`](https://github.com/Rallista/valhalla-mobile).
Repository owners are part of the identity: links, issues, releases, and source references for this
fork must use the `papagrationbiz-sketch` owner.

The main differences in this fork are:

- the Valhalla engine is pinned to the
  [`papagrationbiz-sketch/valhalla`](https://github.com/papagrationbiz-sketch/valhalla) fork;
- the Apple wrapper exposes `route`, `trace_route`, and `trace_attributes`;
- the pinned engine includes an opt-in `motor_scooter.avoid_multi_lane_right_turns` costing option;
- Android SDK restoration is planned for the repository-level `0.6.0` release, but Android artifacts
  are not available from the current `0.5.11` release.

This fork retains the upstream MIT license and copyright notices. See [LICENSE.md](LICENSE.md).
The Valhalla engine submodule carries its own upstream notices.

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
        .package(url: "https://github.com/papagrationbiz-sketch/valhalla-mobile.git", from: "0.1.0"),
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

### Android

The restored Android SDK exposes a minimal raw JSON route API for API 29 and later on
`arm64-v8a` and `x86_64`. Build and validation instructions are in
[docs/android-restoration.md](docs/android-restoration.md). GitHub Actions verification and
GitHub Release distribution are documented in [docs/android-release.md](docs/android-release.md).

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

This project uses <https://github.com/papagrationbiz-sketch/valhalla> as a submodule. Releases pin an
exact submodule commit. The `0.6.0` release baseline is
`13eb61624f9b6464dbaad5081e9fd71207ec539c`; do not replace it with an upstream tag without
validating the fork-specific behavior.

Release, migration, and rollback requirements are documented in
[docs/release-policy.md](docs/release-policy.md).

## References

- Valhalla <https://github.com/valhalla/valhalla>
- Swift Package Manager C++ (for fun - this repo takes the old approach) <https://www.swift.org/documentation/articles/wrapping-c-cpp-library-in-swift.html>
