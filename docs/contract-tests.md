# iOS / Android contract and performance tests

`contract-tests/fixtures/manifest.json` is the platform-neutral test inventory. The JSON
requests in the adjacent `requests` directory are the sole request fixtures; platform
tests must load them rather than copying request strings into Swift or Kotlin.
The manifest also pins the SHA-256 shared by the existing iOS and Android Andorra tile
archives, and fixture validation rejects either archive if it drifts.

The existing iOS raw Valhalla JSON API is the reference producer. Each producer writes
one response named `<case-id>.json`. Compare an Android result directory with the iOS
reference directory using:

```sh
python3 contract-tests/tools/verify.py compare-contracts \
  contract-tests/fixtures path/to/ios-results path/to/android-results
```

`contract-tests/reference/ios` holds the recorded iOS responses. The Android job compares
against them on every pull request, so cross-platform divergence is caught without
building the Apple wrapper each time. The `iOS contract results` workflow rebuilds the
wrapper from the pinned submodule, reruns the fixtures in the simulator, and fails if the
recorded reference no longer matches — refresh it from that run's artifact when the change
was intended. It runs only on changes that can move routing output (`apple`, `src`,
`Package.swift`, `contract-tests`) and on manual dispatch.

Comparison is structural, not JSON-text equality. Object key order and formatting do
not matter. Arrays remain ordered. Shape strings, edge order and IDs, maneuver order and
types, status, and all unspecified attributes must match exactly. Route distance and
time, maneuver distance and time, and selected trace values use the tolerances in
`comparison.json`. Only generated verbal maneuver text is ignored.

## Performance record

Both platforms must write the schema shown in `contract-tests/examples`. Those two files
are a schema example and a smoke test for the gate, not real measurements — the recorded
numbers live in `contract-tests/baselines`. They carry the same `platform` because a
baseline and candidate from different platforms are refused. Measurements use the
`route-auto` case and the release SDK:

- `cold_route_ms`: first route after actor construction;
- `warm_route_ms_p50`: median of the following 100 routes on the same actor;
- `peak_rss_mb`: peak resident memory covering actor creation and all routes;
- `iterations`: completed warm calls, at least 100;
- `failures`: thrown errors, invalid JSON responses, or nonzero trip status.

The fixture ID, Valhalla commit, and tile archive SHA-256 must be identical before a
comparison is accepted. Thresholds live in `performance-thresholds.json`; the verifier
fails CI when a metric exceeds its relative regression plus the absolute noise allowance,
when fewer than 100 calls complete, or when any call fails.

```sh
python3 contract-tests/tools/verify.py compare-performance \
  contract-tests/fixtures/performance-thresholds.json \
  path/to/baseline.json path/to/candidate.json
```

## Single-run invariants

`manifest.json` also carries `invariants`: relations that must hold between cases of one
run. `compare-contracts` checks one case against the other platform, so it cannot express
"these two cases must agree with each other". Both platform jobs run:

```sh
python3 contract-tests/tools/verify.py check-invariants \
  contract-tests/fixtures path/to/results
```

Two are defined, both for `motor_scooter.avoid_multi_lane_right_turns`. Omitting the
option must produce exactly the response disabling it produces, because `false` is the
default (`src/sif/motorscootercost.cc` parses it with `JSON_PBF_DEFAULT_V2(co, false, ...)`).
And all three states must report the same ETA seconds: the penalty is added to search cost
only, never to elapsed time — the engine adds it as `c.cost += kMultiLaneRightTurnPenalty`
with `c.secs` untouched.

Note what this does not establish. On the Andorra fixture the three states return identical
routes, so the ETA invariant holds trivially and the cases do not demonstrate that enabling
the option ever changes a route. Showing that needs a fixture whose path contains a
multi-lane right turn, which the current tile extract has not been shown to contain.

## Recorded baselines

`contract-tests/baselines/<platform>.json` holds the measurement a CI run actually
produced, and every later run is compared against it. A comparison across platforms is
refused: the runner dominates the numbers. The same commit and the same tiles give
`warm_route_ms_p50` of 14.6 ms on the CI x86_64 Android emulator and 2.0 ms in the iOS
simulator on Apple silicon.

That spread is also why `absolute_slack` is per platform. It is an absolute allowance,
so a value generous enough for the Android emulator hides a doubling on iOS — with the
Android slack of 2.0 ms an iOS regression from 2.0 ms to 4.0 ms still passes. Defaults in
`metrics` are the Android values; `platforms.<platform>.metrics` overrides individual
fields, and only the timing slacks need it. `max_regression` is relative and stays shared.

The thresholds are calibrated against the recorded baselines rather than estimates. They
reject a doubling of any metric on either platform while absorbing about 25% of
run-to-run movement. They were previously set for placeholder numbers roughly forty times
slower, which left Android `cold_route_ms` with 253% headroom — a doubling passed.

Update a baseline only when a change is meant to move the numbers, and say why in the
commit. The calibration rests on a single CI sample per platform, so the noise allowance
is an estimate until several runs have been recorded.

## Integration dependency

This change intentionally does not edit Apple code, shared C++, the Valhalla submodule,
or Android runtime code. PR #18 / Issue #13 owns Android persistent actor,
`trace_attributes`, `trace_route`, reuse, and reinitialization. After it merges, the
Android instrumentation job should:

1. package `contract-tests/fixtures` as generated test assets;
2. execute every manifest case through the endpoint named by `endpoint`;
3. execute `actor-lifecycle` 100 times on one actor, close it, then repeat after creating
   a new actor;
4. upload response and performance JSON for this verifier.

The equivalent iOS CI producer remains a separate integration change because this issue
forbids modifying iOS code. Until both producers exist, this workflow gates fixture
schema, normalization/comparison behavior, and performance-regression policy only.
