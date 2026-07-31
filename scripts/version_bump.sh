#!/bin/bash

# Get the last version from the version.txt file (X.Y.Z)
last_version=$(cat version.txt)

# Determine the next version based on the last tag and the desired version
# bump type in argument 1
if [ "$1" == "major" ]; then
    next_version=$(echo $last_version | awk -F. '{print $1+1".0.0"}')
elif [ "$1" == "minor" ]; then
    next_version=$(echo $last_version | awk -F. '{print $1"."$2+1".0"}')
elif [ "$1" == "patch" ]; then
    next_version=$(echo $last_version | awk -F. '{print $1"."$2"."$3+1}')
else
    echo "Invalid version bump type. Please use 'major', 'minor', or 'patch'."
    exit 1
fi

# Optional second argument: the version the caller believes it is releasing.
# A bump type alone is easy to get wrong — leaving it on "patch" when 0.6.0 was
# intended silently ships 0.5.12 — so the caller can state the answer and have
# the mismatch refused instead of published.
expected_version="${2:-}"
if [ -n "$expected_version" ] && [ "$expected_version" != "$next_version" ]; then
    echo "Refusing to bump: a '$1' bump of $last_version produces $next_version," >&2
    echo "but $expected_version was expected. Choose the bump type that produces it." >&2
    exit 1
fi

echo "Bumping version from $last_version to $next_version"

# Write the version to the version.txt file
echo "$next_version" > version.txt

# Output the tag for GitHub Actions and for local callers.
if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "version=$next_version" >> "$GITHUB_OUTPUT"
fi
echo "$next_version"
