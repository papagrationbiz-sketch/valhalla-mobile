#!/bin/bash

# Writes the release notes body required by docs/release-policy.md precondition 9:
# the parent repository commit, the Valhalla submodule commit, the artifacts, the
# supported platforms and ABIs, and the known limitations.
#
# GitHub's generated notes list the merged pull requests, which is useful but says
# nothing about which engine a release was built from. Both are used: this body is
# passed as body_path and the generated notes are appended.
#
# Usage: generate_release_notes.sh <version> <xcframework-zip> [output]

set -euo pipefail

version="${1:?usage: generate_release_notes.sh <version> <xcframework-zip> [output]}"
xcframework_zip="${2:?usage: generate_release_notes.sh <version> <xcframework-zip> [output]}"
output="${3:-release-notes.md}"

repository_commit=$(git rev-parse HEAD)
submodule_commit=$(git rev-parse "HEAD:src/valhalla")
xcframework_checksum=$(shasum -a 256 "$xcframework_zip" | awk '{print $1}')

# The ABIs the Android SDK is built and shipped for. android.yml fails the build
# when the AAR carries anything else, so this list and that check must agree.
android_abis="arm64-v8a, x86_64"

{
    echo "## Build provenance"
    echo
    echo "| | |"
    echo "| --- | --- |"
    echo "| Version | \`$version\` |"
    echo "| Repository commit | \`$repository_commit\` |"
    echo "| Valhalla submodule commit | \`$submodule_commit\` |"
    echo "| XCFramework SHA-256 | \`$xcframework_checksum\` |"
    echo
    echo "## Artifacts"
    echo
    echo "- \`valhalla-wrapper.xcframework.zip\` — iOS device and simulator slices, consumed through Swift Package Manager."
    echo
    echo "## Supported platforms"
    echo
    echo "- iOS 16.4 and later, on device and simulator."
    echo "- Android API 29 and later, ABIs: $android_abis."
    echo
    echo "## Known limitations"
    echo
    echo "- Routing results are only verified against the Andorra fixture tiles. Other regions are untested."
    echo "- \`motor_scooter.avoid_multi_lane_right_turns\` is verified to leave reported ETA seconds unchanged, but no fixture in this repository contains a multi-lane right turn, so the avoidance itself is not exercised."
    echo "- Performance thresholds are calibrated from a single CI sample per platform, so the run-to-run noise allowance is an estimate."
    echo
    echo "---"
    echo
} > "$output"

echo "Wrote $output"
