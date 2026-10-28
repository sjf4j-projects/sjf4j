#!/usr/bin/env python3
"""Isolate leaf-wrapper reuse independently for schema byKey and byIndex.

Runs against one pinned source revision. Only active inferSubByKey/Index
calls inside Evaluator.java change; no traversal or evaluator logic changes.

Modes: none (fresh key/index), key (reused key only),
       index (reused index only; unchanged main), both (reused key/index).
"""
import argparse
import hashlib
import re
from pathlib import Path

CALL = re.compile(
    r"(?P<prefix>\binferSubBy(?P<kind>Key|Index)\([^\n;]*?,\s*)"
    r"(?P<leaf>null|ctx\.reusedLeaf\(\))(?P<tail>\))"
)


def transform(source, mode):
    key_reuse = mode in ("key", "both")
    index_reuse = mode in ("index", "both")
    count = {"Key": 0, "Index": 0}
    changed = []

    def replace(match):
        kind = match.group("kind")
        count[kind] += 1
        leaf = "ctx.reusedLeaf()" if (key_reuse if kind == "Key" else index_reuse) else "null"
        return match.group("prefix") + leaf + match.group("tail")

    for line in source.splitlines(keepends=True):
        if line.lstrip().startswith("//") or "inferSubBy" not in line:
            changed.append(line)
        else:
            changed.append(CALL.sub(replace, line))

    if count != {"Key": 4, "Index": 4}:
        raise AssertionError("Unexpected active call sites: " + str(count))

    result = "".join(changed)
    if mode == "index" and result != source:
        raise AssertionError("index mode must exactly match latest main")

    print(mode, "call sites", count,
          "sha256", hashlib.sha256(result.encode("utf-8")).hexdigest()[:16])
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path)
    ap.add_argument("output", type=Path)
    ap.add_argument("--mode", choices=["none", "key", "index", "both"], required=True)
    args = ap.parse_args()
    source = args.input.read_text(encoding="utf-8")
    args.output.write_text(transform(source, args.mode), encoding="utf-8")


if __name__ == "__main__":
    main()
