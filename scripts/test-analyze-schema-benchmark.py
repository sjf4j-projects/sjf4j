#!/usr/bin/env python3
"""Minimal regression checks for the Full JMH report generator."""

import importlib.util
import json
from pathlib import Path
import tempfile

SCRIPT = Path(__file__).resolve().with_name("analyze-schema-benchmark.py")
spec = importlib.util.spec_from_file_location("creek_report", SCRIPT)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def sample(name, score):
    return {
        "benchmark": "org.sjf4j.testbench.schema.CreekSchemaBenchmark." + name,
        "primaryMetric": {
            "score": score,
            "scoreError": 0.12,
            "scoreUnit": "us/op",
            "rawData": [[score] * 5 for _ in range(3)],
        },
        "secondaryMetrics": {"gc.alloc.rate.norm": {"score": 48.0}},
    }


def main():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        files = {}
        for group, names in report.METHODS.items():
            path = root / (group + ".json")
            path.write_text(
                json.dumps([sample(name, 2.0 if "compiled" in name else 4.0)
                            for name in names]), encoding="utf-8")
            files[group] = path
        output = root / "report.md"
        report.run(files["validation"], files["roundtrip"], output)
        content = output.read_text(encoding="utf-8")
        for names in report.METHODS.values():
            for name in names:
                assert f"| {name} |" in content
        assert "jsonNode_isValid_runtime" in content
        assert "roundtrip_isValid_compiled" in content
        assert "2.000x" in content
        assert "48.0" in content

        rows = json.loads(files["validation"].read_text(encoding="utf-8"))
        rows.pop()
        files["validation"].write_text(json.dumps(rows), encoding="utf-8")
        try:
            report.load_group(files["validation"], "validation")
        except ValueError as error:
            assert "missing" in str(error)
        else:
            raise AssertionError("Incomplete Full JMH group must fail")
        # Reject duplicated rows and malformed fork measurements as well.
        complete = [sample(name, 2.0) for name in report.METHODS["validation"]]
        for invalid in (complete + [complete[0]],
                        [{**complete[0], "primaryMetric": {
                            **complete[0]["primaryMetric"], "rawData": [[2.0] * 4] * 3}}] + complete[1:],
                        [{**complete[0], "primaryMetric": {
                            **complete[0]["primaryMetric"], "score": float("nan")}}] + complete[1:]):
            files["validation"].write_text(json.dumps(invalid), encoding="utf-8")
            try:
                report.load_group(files["validation"], "validation")
            except ValueError:
                pass
            else:
                raise AssertionError("Malformed Full JMH input must fail")
    print("Schema JMH report tests passed")


if __name__ == "__main__":
    main()
