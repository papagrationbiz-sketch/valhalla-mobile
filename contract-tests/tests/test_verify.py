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


if __name__ == "__main__":
    unittest.main()
