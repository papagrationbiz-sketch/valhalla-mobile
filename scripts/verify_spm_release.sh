#!/bin/bash

# Release policy precondition 5: Swift Package Manager resolves the release
# artifact and validates its checksum.
#
# Resolves a throwaway package that depends on an exact published version. SPM
# downloads the binary target and rejects it when the checksum in the released
# Package.swift does not match the bytes it fetched, so a successful resolve is
# the check. The downloaded artifact is then confirmed to exist, because a
# resolve that never fetched anything would prove nothing.
#
# Usage: verify_spm_release.sh <version> [repository-url]

set -euo pipefail

version="${1:?usage: verify_spm_release.sh <version> [repository-url]}"
repository="${2:-https://github.com/papagrationbiz-sketch/valhalla-mobile.git}"

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

mkdir -p "$workdir/Sources/ResolveCheck"
cat > "$workdir/Sources/ResolveCheck/Empty.swift" <<'SWIFT'
// Intentionally empty: only dependency resolution is under test.
SWIFT

cat > "$workdir/Package.swift" <<SWIFT
// swift-tools-version:5.8
import PackageDescription

let package = Package(
    name: "ResolveCheck",
    platforms: [.iOS("16.4")],
    dependencies: [
        .package(url: "$repository", exact: "$version")
    ],
    targets: [
        .target(
            name: "ResolveCheck",
            dependencies: [.product(name: "Valhalla", package: "valhalla-mobile")]
        )
    ]
)
SWIFT

echo "Resolving $repository at exactly $version"
(cd "$workdir" && swift package resolve)

resolved_version=$(
    python3 - "$workdir/Package.resolved" <<'PY'
import json, sys
document = json.load(open(sys.argv[1]))
pins = document.get("pins") or document.get("object", {}).get("pins", [])
for pin in pins:
    identity = str(pin.get("identity") or pin.get("package", ""))
    if "valhalla-mobile" == identity:
        print(pin["state"].get("version", ""))
        break
PY
)

if [ "$resolved_version" != "$version" ]; then
    echo "Resolved $resolved_version, expected $version" >&2
    exit 1
fi

# SPM validates the checksum while downloading the binary target, so the
# artifact being present is what proves the recorded checksum matched.
artifact_count=$(find "$workdir/.build/artifacts" -name "*.xcframework" -maxdepth 3 2>/dev/null | wc -l | tr -d ' ')
if [ "$artifact_count" = "0" ]; then
    echo "No XCFramework was downloaded, so the checksum was never validated." >&2
    find "$workdir/.build/artifacts" -maxdepth 3 >&2 2>/dev/null || true
    exit 1
fi

echo "Resolved $version and downloaded $artifact_count XCFramework(s); checksum accepted."
