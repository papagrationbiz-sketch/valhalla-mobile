#!/bin/bash

# Guards the guard: version_bump.sh is what stops a run meant to release 0.6.0
# from releasing 0.5.12, so a regression here is only noticed once something
# wrong has been tagged.

set -uo pipefail

repository_root="$(cd "$(dirname "$0")/../.." && pwd)"
script="$repository_root/scripts/version_bump.sh"
failures=0

# Runs the script against a throwaway version.txt so the real one is untouched.
expect() {
    local description=$1 starting_version=$2 expected_status=$3
    shift 3

    local workdir
    workdir=$(mktemp -d)
    echo "$starting_version" > "$workdir/version.txt"

    local output status
    output=$(cd "$workdir" && bash "$script" "$@" 2>&1)
    status=$?
    local resulting_version
    resulting_version=$(cat "$workdir/version.txt")
    rm -rf "$workdir"

    if [ "$status" != "$expected_status" ]; then
        echo "FAIL  $description: exited $status, expected $expected_status"
        echo "      output: $output"
        failures=$((failures + 1))
        return
    fi

    # A refused bump must leave version.txt alone, or the next run starts from a
    # version that was never released.
    if [ "$expected_status" != "0" ] && [ "$resulting_version" != "$starting_version" ]; then
        echo "FAIL  $description: version.txt became $resulting_version after a refusal"
        failures=$((failures + 1))
        return
    fi

    echo "ok    $description"
}

expect "minor bump producing the expected version" 0.5.11 0 minor 0.6.0
expect "patch bump when 0.6.0 was expected"        0.5.11 1 patch 0.6.0
expect "major bump producing the expected version" 0.5.11 0 major 1.0.0
expect "minor bump when 1.0.0 was expected"        0.5.11 1 minor 1.0.0
expect "no expected version keeps working"         0.5.11 0 minor
expect "unknown bump type"                         0.5.11 1 sideways
expect "missing bump type"                         0.5.11 1

if [ "$failures" != "0" ]; then
    echo "$failures check(s) failed"
    exit 1
fi

echo "All version_bump.sh checks passed"
