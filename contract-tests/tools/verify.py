#!/usr/bin/env python3
"""Validate and compare valhalla-mobile contract and performance results."""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import math
import sys
from pathlib import Path
from typing import Any


class VerificationError(Exception):
    pass


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise VerificationError(f"{path}: {error}") from error


def path_matches(pattern: str, path: str) -> bool:
    return fnmatch.fnmatchcase(path, pattern)


def ignored(path: str, patterns: list[str]) -> bool:
    return any(path_matches(pattern, path) for pattern in patterns)


def compare_values(
    reference: Any,
    candidate: Any,
    path: str,
    profile: dict[str, Any],
    failures: list[str],
) -> None:
    ignore_paths = profile.get("ignore_paths", [])
    if ignored(path, ignore_paths):
        return

    if isinstance(reference, dict) and isinstance(candidate, dict):
        keys = sorted(set(reference) | set(candidate))
        for key in keys:
            child_path = f"{path}/{key}"
            if ignored(child_path, ignore_paths):
                continue
            if key not in reference:
                failures.append(f"{child_path}: unexpected candidate field")
            elif key not in candidate:
                failures.append(f"{child_path}: missing candidate field")
            else:
                compare_values(reference[key], candidate[key], child_path, profile, failures)
        return

    if isinstance(reference, list) and isinstance(candidate, list):
        if len(reference) != len(candidate):
            failures.append(f"{path}: array length {len(candidate)} != {len(reference)}")
            return
        for index, (expected, actual) in enumerate(zip(reference, candidate)):
            compare_values(expected, actual, f"{path}/{index}", profile, failures)
        return

    if (
        isinstance(reference, (int, float))
        and not isinstance(reference, bool)
        and isinstance(candidate, (int, float))
        and not isinstance(candidate, bool)
    ):
        for tolerance in profile.get("numeric_tolerances", []):
            if path_matches(tolerance["path"], path):
                absolute = float(tolerance.get("absolute", 0.0))
                relative = float(tolerance.get("relative", 0.0))
                if math.isclose(
                    float(reference),
                    float(candidate),
                    rel_tol=relative,
                    abs_tol=absolute,
                ):
                    return
                failures.append(
                    f"{path}: {candidate} outside abs={absolute}, rel={relative} "
                    f"from {reference}"
                )
                return
        if float(reference) == float(candidate):
            return
        failures.append(f"{path}: {candidate} != {reference}")
        return

    if type(reference) is not type(candidate) or reference != candidate:
        failures.append(f"{path or '/'}: {candidate!r} != {reference!r}")


def validate_fixtures(fixture_dir: Path) -> None:
    manifest = load_json(fixture_dir / "manifest.json")
    comparison = load_json(fixture_dir / "comparison.json")
    thresholds = load_json(fixture_dir / "performance-thresholds.json")
    if manifest.get("schema_version") != 1:
        raise VerificationError("manifest schema_version must be 1")
    if comparison.get("schema_version") != 1:
        raise VerificationError("comparison schema_version must be 1")
    if thresholds.get("schema_version") != 1:
        raise VerificationError("performance threshold schema_version must be 1")

    expected_tile_sha256 = manifest.get("tile_sha256")
    if not expected_tile_sha256:
        raise VerificationError("manifest is missing tile_sha256")
    for platform, relative_path in manifest.get("tile_archives", {}).items():
        archive_path = fixture_dir / relative_path
        try:
            actual_sha256 = hashlib.sha256(archive_path.read_bytes()).hexdigest()
        except OSError as error:
            raise VerificationError(f"{archive_path}: {error}") from error
        if actual_sha256 != expected_tile_sha256:
            raise VerificationError(
                f"{platform} tile SHA-256 {actual_sha256} != {expected_tile_sha256}"
            )

    profiles = comparison.get("profiles", {})
    case_ids: set[str] = set()
    required_endpoints = {"route", "trace_attributes", "trace_route"}
    found_endpoints: set[str] = set()
    for case in manifest.get("cases", []):
        case_id = case.get("id")
        if not case_id or case_id in case_ids:
            raise VerificationError(f"invalid or duplicate case id: {case_id!r}")
        case_ids.add(case_id)
        endpoint = case.get("endpoint")
        found_endpoints.add(endpoint)
        if case.get("comparison_profile") not in profiles:
            raise VerificationError(f"{case_id}: unknown comparison profile")
        request_path = fixture_dir / case.get("request", "")
        request = load_json(request_path)
        if not isinstance(request, dict):
            raise VerificationError(f"{request_path}: request must be a JSON object")

    missing = required_endpoints - found_endpoints
    if missing:
        raise VerificationError(f"missing endpoint fixtures: {sorted(missing)}")
    if len(case_ids) < 8:
        raise VerificationError("manifest must cover all Issue #15 scenarios")


def compare_contracts(
    fixture_dir: Path,
    reference_dir: Path,
    candidate_dir: Path,
) -> None:
    manifest = load_json(fixture_dir / "manifest.json")
    profiles = load_json(fixture_dir / "comparison.json")["profiles"]
    all_failures: list[str] = []
    for case in manifest["cases"]:
        case_id = case["id"]
        reference = load_json(reference_dir / f"{case_id}.json")
        candidate = load_json(candidate_dir / f"{case_id}.json")
        failures: list[str] = []
        compare_values(
            reference,
            candidate,
            "",
            profiles[case["comparison_profile"]],
            failures,
        )
        all_failures.extend(f"{case_id}{failure}" for failure in failures)
    if all_failures:
        raise VerificationError("\n".join(all_failures))


def check_invariants(fixture_dir: Path, results_dir: Path) -> None:
    """Check relations that hold between cases of a single run.

    compare-contracts checks one case across platforms; these checks compare
    cases against each other on one platform, which is how an option that must
    not change the answer gets verified.
    """
    manifest = load_json(fixture_dir / "manifest.json")
    profiles = load_json(fixture_dir / "comparison.json")["profiles"]
    failures: list[str] = []
    for invariant in manifest.get("invariants", []):
        invariant_id = invariant["id"]
        results = [load_json(results_dir / f"{case}.json") for case in invariant["cases"]]
        first, rest = results[0], results[1:]
        first_case, rest_cases = invariant["cases"][0], invariant["cases"][1:]
        if invariant["type"] == "identical_response":
            for case, result in zip(rest_cases, rest):
                differences: list[str] = []
                compare_values(first, result, "", profiles["error"], differences)
                failures.extend(
                    f"{invariant_id}: {first_case} and {case} differ{difference}"
                    for difference in differences
                )
        elif invariant["type"] == "equal_eta":
            expected = first["trip"]["summary"]["time"]
            for case, result in zip(rest_cases, rest):
                actual = result["trip"]["summary"]["time"]
                if actual != expected:
                    failures.append(
                        f"{invariant_id}: {case} reports {actual} seconds where "
                        f"{first_case} reports {expected}"
                    )
        else:
            raise SystemExit(f"Unknown invariant type: {invariant['type']}")
    if failures:
        raise VerificationError("\n".join(failures))


def compare_performance(
    thresholds_path: Path,
    baseline_path: Path,
    candidate_path: Path,
) -> None:
    thresholds = load_json(thresholds_path)
    baseline = load_json(baseline_path)
    candidate = load_json(candidate_path)
    for result_name, result in (("baseline", baseline), ("candidate", candidate)):
        if result.get("schema_version") != 1:
            raise VerificationError(f"{result_name}: schema_version must be 1")
        for identity in ("fixture_id", "valhalla_commit", "tile_sha256"):
            if not result.get(identity):
                raise VerificationError(f"{result_name}: missing {identity}")
    for identity in ("fixture_id", "valhalla_commit", "tile_sha256"):
        if baseline[identity] != candidate[identity]:
            raise VerificationError(f"{identity}: candidate and baseline differ")

    if baseline.get("platform") != candidate.get("platform"):
        raise VerificationError("platform: candidate and baseline differ")

    baseline_metrics = baseline["metrics"]
    candidate_metrics = candidate["metrics"]
    # absolute_slack is an absolute allowance, so it cannot be shared across
    # platforms whose numbers differ by an order of magnitude: the CI Android
    # emulator routes in ~15 ms where the iOS simulator takes ~2 ms, and one
    # slack generous enough for the former hides a doubling of the latter.
    overrides = thresholds.get("platforms", {}).get(candidate.get("platform"), {})
    override_metrics = overrides.get("metrics", {})
    failures: list[str] = []
    for metric, default_policy in thresholds["metrics"].items():
        policy = {**default_policy, **override_metrics.get(metric, {})}
        expected = float(baseline_metrics[metric])
        actual = float(candidate_metrics[metric])
        limit = expected * (1.0 + float(policy["max_regression"])) + float(
            policy["absolute_slack"]
        )
        if actual > limit:
            failures.append(f"{metric}: {actual} exceeds {limit:.3f}")
    if int(candidate_metrics["iterations"]) < int(thresholds["required_iterations"]):
        failures.append(
            f"iterations: {candidate_metrics['iterations']} below "
            f"{thresholds['required_iterations']}"
        )
    if int(candidate_metrics["failures"]) > int(thresholds["max_failures"]):
        failures.append(
            f"failures: {candidate_metrics['failures']} exceeds "
            f"{thresholds['max_failures']}"
        )
    if failures:
        raise VerificationError("\n".join(failures))


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser("validate-fixtures")
    validate_parser.add_argument("fixture_dir", type=Path)

    contract_parser = subparsers.add_parser("compare-contracts")
    contract_parser.add_argument("fixture_dir", type=Path)
    contract_parser.add_argument("reference_dir", type=Path)
    contract_parser.add_argument("candidate_dir", type=Path)

    invariant_parser = subparsers.add_parser("check-invariants")
    invariant_parser.add_argument("fixture_dir", type=Path)
    invariant_parser.add_argument("results_dir", type=Path)

    performance_parser = subparsers.add_parser("compare-performance")
    performance_parser.add_argument("thresholds", type=Path)
    performance_parser.add_argument("baseline", type=Path)
    performance_parser.add_argument("candidate", type=Path)

    args = parser.parse_args()
    try:
        if args.command == "validate-fixtures":
            validate_fixtures(args.fixture_dir)
        elif args.command == "compare-contracts":
            compare_contracts(args.fixture_dir, args.reference_dir, args.candidate_dir)
        elif args.command == "check-invariants":
            check_invariants(args.fixture_dir, args.results_dir)
        else:
            compare_performance(args.thresholds, args.baseline, args.candidate)
    except VerificationError as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
