# Android AAR Release

Android release artifacts are built and verified only by GitHub Actions. Do not run the native,
CMake, NDK, vcpkg, AAR, or XCFramework builds on a developer machine.

## Automated gates

`.github/workflows/android.yml` is the reusable clean-build gate. It:

1. builds stripped `arm64-v8a` and `x86_64` release libraries with the pinned toolchain;
2. checks ABI, static C++ runtime linkage, and 16 KB ELF segment alignment;
3. packages exactly those two libraries in the release AAR;
4. verifies AAR 16 KB ZIP alignment with `zipalign -P 16`;
5. runs the x86_64 Emulator load and fixed-fixture route test;
6. records compressed and expanded AAR sizes;
7. publishes a SHA-256 checksum, SPDX SBOM from the actual vcpkg install status, and license bundle;
8. rejects changes to Apple code, shared C++, the Valhalla submodule, version, and the existing
   release workflow.

Gradle uses `--warning-mode=fail`; any warning fails the clean build.

## `0.6.0` distribution

The repository has one shared release tag. The existing iOS release workflow remains responsible
for creating the immutable tag and draft GitHub Release. Android does not create, move, or publish a
tag.

After all release preconditions in `docs/release-policy.md` pass:

1. create the `0.6.0` tag and draft release through the existing release workflow;
2. dispatch `Android Release Assets` from the `0.6.0` tag with `release_tag=0.6.0`;
3. first leave `attach_to_draft_release=false` and inspect the generated artifact;
4. confirm the AAR checksum, SPDX SBOM, license archive, sizes, ABI list, ELF checks, Emulator test,
   and zero-warning result;
5. rerun from the same immutable tag with `attach_to_draft_release=true`;
6. confirm the draft release contains `valhalla-mobile-0.6.0.aar`,
   `valhalla-mobile-0.6.0.aar.sha256`, `android-dependencies.spdx.json`,
   `android-third-party-licenses.tar.gz`, and `aar-metrics.json`;
7. complete the physical `arm64-v8a` device load/route gate before publishing the draft release.

The physical-device gate is intentionally not replaced with a paid device-farm dependency. Maven
Central and 32-bit ABIs remain out of scope.
