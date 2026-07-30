# Release Policy

## Canonical Identity

The canonical repository is:

`https://github.com/papagrationbiz-sketch/valhalla-mobile`

All maintained repository links, clone URLs, release asset URLs, documentation, and downstream
dependency declarations must use that URL. GitHub redirects are migration aids, not canonical
configuration.

This repository is a fork of `https://github.com/Rallista/valhalla-mobile`. The owner is always
included when referring to either repository. The project remains under the MIT license in
`LICENSE.md`.

## Fixed Baselines

| Purpose | Commit |
| --- | --- |
| Android restoration source tree | `69dac8ff2013b67a4358b20189f261b15583df3a` |
| Android source deletion | `9fe8bfb0966b59cf76a34711beb277948af45f06` |
| Valhalla submodule for the `0.6.0` baseline | `13eb61624f9b6464dbaad5081e9fd71207ec539c` |
| Existing iOS `0.5.11` release commit | `3e70e7055a29745a977dd89b4196d6ee9910059f` |

Android work must restore only the required Android tree from the restoration source onto current
`main`. It must not revert the deletion commit wholesale or reset the Valhalla submodule to the old
Android baseline.

## Versioning

Releases use one repository-level semantic version.

- `0.5.11` is an immutable existing iOS release. Its tag, commit, asset, checksum, and behavior must
  not change.
- `0.6.0` introduces the restored Android SDK while retaining the existing iOS product and API.
- A release tag is created once, after the release manifest commit exists.
- Moving an existing tag, force-retagging, or force-pushing a release tag is prohibited.
- A failed published release is corrected with a new patch version. Published tags are not reused.

Before `1.0.0`, this project still treats documented iOS compatibility as a release contract:
`0.6.0` must preserve the `Valhalla` Swift product, current public API, minimum platform, and
successful resolution of the binary target.

## Distribution

The initial `0.6.0` distribution is limited to GitHub Releases:

- `valhalla-wrapper.xcframework.zip`
- `valhalla-mobile-0.6.0.aar`

Maven publication is out of scope for `0.6.0`. Maven coordinates and Android namespaces must not
reuse identifiers owned by the upstream fork. `biz.papagration` is only a candidate; ownership of
the corresponding domain and publishing namespace must be verified in a separate issue before
coordinates are finalized or published.

## Release Preconditions

Before creating the final `0.6.0` tag:

1. The canonical repository rename and maintained URL updates are complete.
2. The release commit records version `0.6.0`, the XCFramework checksum, and the exact submodule
   commit.
3. A clean checkout builds the supported iOS variants.
4. A clean checkout builds the approved Android ABIs and the AAR contains only those approved ABIs.
5. Swift Package Manager resolves the release artifact and validates its checksum.
6. Android tests verify native loading and a fixed-fixture route.
7. `motor_scooter.avoid_multi_lane_right_turns` is tested with the option omitted, disabled, and
   enabled; the opt-in penalty must not change ETA seconds.
8. The existing `0.5.11` tag still resolves and builds independently.
9. Release notes list the parent repository commit, Valhalla submodule commit, artifacts, supported
   platforms/ABIs, and known limitations.
10. The downstream GenNavi update freeze described below is merged before the final tag is
    published.

Draft releases or prerelease versions are used for validation. A final release is not published
from an uncommitted manifest or a moving branch reference.

## GenNavi Update Freeze

GenNavi currently permits versions from `0.5.2` up to the next major version. That range can select
`0.6.0` automatically. Before publishing `0.6.0`, a separate GenNavi issue must:

1. temporarily pin the SDK to exact version `0.5.11`;
2. resolve and record commit `3e70e7055a29745a977dd89b4196d6ee9910059f`;
3. merge and verify that pin in the maintained GenNavi branch;
4. retain the pin until `0.6.0` iOS compatibility checks pass.

After publication, GenNavi moves deliberately to the canonical URL and `0.6.0`, records the resolved
tag commit, and runs its iOS build and routing regression checks. Publishing `0.6.0` before the
temporary pin is merged is prohibited.

## Repository Rename Order

1. Prepare and review canonical URL changes on an issue branch.
2. Rename the GitHub repository to `valhalla-mobile`.
3. Update the local `origin` URL to the canonical clone URL.
4. Push the reviewed issue branch and open a draft pull request.
5. Verify the old repository URL redirects to the canonical repository.
6. Merge the URL and policy changes before beginning the Android restoration issue.

No release tag is created as part of the rename.

## Rollback

### Before `0.6.0` publication

- stop the draft or prerelease;
- leave `0.5.11` unchanged;
- keep GenNavi pinned to exact `0.5.11`;
- fix forward on the issue branch.

### After `0.6.0` publication

- do not move or delete the published tag as a repair mechanism;
- publish a corrected patch release such as `0.6.1`;
- document the affected version and workaround;
- pin GenNavi back to exact `0.5.11` if the iOS path is affected.

### Repository rename rollback

If the canonical rename prevents required access, restore the previous repository name through
GitHub administration, update `origin`, and leave downstream dependencies pinned to their last
verified tag. Do not combine a repository-name rollback with tag changes.
