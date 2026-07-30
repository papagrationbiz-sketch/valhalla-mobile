import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "contract_verify", ROOT / "contract-tests/tools/verify.py"
)
verify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(verify)


class ContractVerifierTests(unittest.TestCase):
    def test_route_tolerances_and_ignored_verbal_fields(self):
        profile = {
            "ignore_paths": ["/trip/legs/*/maneuvers/*/verbal_*"],
            "numeric_tolerances": [
                {
                    "path": "/trip/summary/time",
                    "absolute": 2.0,
                    "relative": 0.03,
                }
            ],
        }
        reference = {
            "trip": {
                "summary": {"time": 100},
                "shape": "abc",
                "legs": [{"maneuvers": [{"type": 1, "verbal_pre_transition": "A"}]}],
            }
        }
        candidate = {
            "trip": {
                "summary": {"time": 103},
                "shape": "abc",
                "legs": [{"maneuvers": [{"type": 1, "verbal_pre_transition": "B"}]}],
            }
        }
        failures = []
        verify.compare_values(reference, candidate, "", profile, failures)
        self.assertEqual([], failures)

    def test_shape_change_fails(self):
        failures = []
        verify.compare_values(
            {"trip": {"shape": "abc"}},
            {"trip": {"shape": "abd"}},
            "",
            {"ignore_paths": [], "numeric_tolerances": []},
            failures,
        )
        self.assertTrue(any("/trip/shape" in failure for failure in failures))

    def test_equivalent_json_number_types_match(self):
        failures = []
        verify.compare_values(
            {"status": 0},
            {"status": 0.0},
            "",
            {"ignore_paths": [], "numeric_tolerances": []},
            failures,
        )
        self.assertEqual([], failures)

    def test_performance_gate(self):
        thresholds = {
            "schema_version": 1,
            "metrics": {
                "cold_route_ms": {"max_regression": 0.2, "absolute_slack": 50},
                "warm_route_ms_p50": {"max_regression": 0.15, "absolute_slack": 5},
                "peak_rss_mb": {"max_regression": 0.1, "absolute_slack": 16},
            },
            "required_iterations": 100,
            "max_failures": 0,
        }
        identity = {
            "schema_version": 1,
            "fixture_id": "andorra-v1",
            "valhalla_commit": "deadbeef",
            "tile_sha256": "abc123",
        }
        baseline = {
            **identity,
            "metrics": {
                "cold_route_ms": 100,
                "warm_route_ms_p50": 10,
                "peak_rss_mb": 100,
                "iterations": 100,
                "failures": 0,
            },
        }
        candidate = {
            **identity,
            "metrics": {
                "cold_route_ms": 171,
                "warm_route_ms_p50": 10,
                "peak_rss_mb": 100,
                "iterations": 100,
                "failures": 0,
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            directory_path = Path(directory)
            paths = []
            for name, value in (
                ("thresholds.json", thresholds),
                ("baseline.json", baseline),
                ("candidate.json", candidate),
            ):
                path = directory_path / name
                path.write_text(json.dumps(value), encoding="utf-8")
                paths.append(path)
            with self.assertRaises(verify.VerificationError):
                verify.compare_performance(*paths)

    def _run_invariants(self, invariants, responses):
        with tempfile.TemporaryDirectory() as directory:
            fixture_dir = Path(directory) / "fixtures"
            results_dir = Path(directory) / "results"
            fixture_dir.mkdir()
            results_dir.mkdir()
            manifest = {"schema_version": 1, "cases": [], "invariants": invariants}
            (fixture_dir / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            (fixture_dir / "comparison.json").write_text(
                json.dumps({"profiles": {"error": {"ignore_paths": [], "numeric_tolerances": {}}}}),
                encoding="utf-8",
            )
            for case, response in responses.items():
                (results_dir / f"{case}.json").write_text(json.dumps(response), encoding="utf-8")
            verify.check_invariants(fixture_dir, results_dir)

    def test_equal_eta_invariant(self):
        """The avoid_multi_lane_right_turns penalty is added to search cost only,
        so enabling it must not move the reported ETA."""
        invariants = [
            {"id": "eta", "type": "equal_eta", "cases": ["omitted", "enabled"]},
        ]

        def response(time):
            return {"trip": {"summary": {"time": time, "length": 7.5}}}

        self._run_invariants(invariants, {"omitted": response(660.2), "enabled": response(660.2)})
        with self.assertRaises(verify.VerificationError):
            self._run_invariants(
                invariants, {"omitted": response(660.2), "enabled": response(661.2)}
            )

    def test_identical_response_invariant(self):
        """Omitting the option has to behave exactly like disabling it, since
        false is the documented default."""
        invariants = [
            {"id": "default", "type": "identical_response", "cases": ["omitted", "disabled"]},
        ]
        same = {"trip": {"summary": {"time": 660.2}, "legs": [{"shape": "abc"}]}}
        other = {"trip": {"summary": {"time": 660.2}, "legs": [{"shape": "xbc"}]}}
        self._run_invariants(invariants, {"omitted": same, "disabled": dict(same)})
        with self.assertRaises(verify.VerificationError):
            self._run_invariants(invariants, {"omitted": same, "disabled": other})

    def _run_performance(self, thresholds, baseline, candidate):
        with tempfile.TemporaryDirectory() as directory:
            directory_path = Path(directory)
            paths = []
            for name, value in (
                ("thresholds.json", thresholds),
                ("baseline.json", baseline),
                ("candidate.json", candidate),
            ):
                path = directory_path / name
                path.write_text(json.dumps(value), encoding="utf-8")
                paths.append(path)
            verify.compare_performance(*paths)

    def test_platform_mismatch_is_rejected(self):
        """Comparing across platforms is meaningless: the runners differ far more
        than any regression the gate is meant to catch."""
        thresholds = {
            "schema_version": 1,
            "metrics": {"warm_route_ms_p50": {"max_regression": 0.2, "absolute_slack": 2}},
            "required_iterations": 100,
            "max_failures": 0,
        }
        identity = {
            "schema_version": 1,
            "fixture_id": "andorra-v1",
            "valhalla_commit": "deadbeef",
            "tile_sha256": "abc123",
        }
        metrics = {"warm_route_ms_p50": 10, "iterations": 100, "failures": 0}
        with self.assertRaises(verify.VerificationError):
            self._run_performance(
                thresholds,
                {**identity, "platform": "ios", "metrics": metrics},
                {**identity, "platform": "android", "metrics": metrics},
            )

    def test_platform_override_tightens_absolute_slack(self):
        """A slack sized for the slower platform would hide a doubling on the
        faster one, so a platform override has to win over the default."""
        thresholds = {
            "schema_version": 1,
            "metrics": {"warm_route_ms_p50": {"max_regression": 0.2, "absolute_slack": 2.0}},
            "required_iterations": 100,
            "max_failures": 0,
            "platforms": {"ios": {"metrics": {"warm_route_ms_p50": {"absolute_slack": 0.6}}}},
        }
        identity = {
            "schema_version": 1,
            "fixture_id": "andorra-v1",
            "valhalla_commit": "deadbeef",
            "tile_sha256": "abc123",
        }

        def record(platform, warm):
            return {
                **identity,
                "platform": platform,
                "metrics": {"warm_route_ms_p50": warm, "iterations": 100, "failures": 0},
            }

        # 2 -> 4 ms: the default slack allows 4.4 ms, the ios override only 3.0 ms.
        self._run_performance(thresholds, record("android", 2.0), record("android", 4.0))
        with self.assertRaises(verify.VerificationError):
            self._run_performance(thresholds, record("ios", 2.0), record("ios", 4.0))


if __name__ == "__main__":
    unittest.main()
