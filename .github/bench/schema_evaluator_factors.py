#!/usr/bin/env python3
"""Generate controlled Evaluator variants from one pinned source revision.

Factors:
    traversal: walker (NodeWalker.anyMatch) vs loop (Nodes iterator/entrySet)
    leaf: reuse (ctx.reusedLeaf()) vs fresh (null)

Only the four evaluator classes under test are touched. The loop conversion
changes callback returns to equivalent control flow and uses ordinary local
scalars instead of lambda-captured mutable holders. The contains shortcut
must break its loop, not return from evaluate().
"""
import argparse
import hashlib
import re
from pathlib import Path

SECTIONS = [
    ("PropertiesEvaluator", "RequiredEvaluator"),
    ("ItemsEvaluator", "ContainsEvaluator"),
    ("ContainsEvaluator", "IfThenElseEvaluator"),
    ("UnevaluatedEvaluator", None),
]

CALL = re.compile(
    r"(?m)^(?P<indent> *)"
    r"(?P<assign>boolean shouldAbort = )?"
    r"NodeWalker\.anyMatchIn(?P<kind>Object|Array)"
    r"\((?P<node>[^,]+), \((?P<args>[^)]+)\) -> \{\n"
)


def loopify(section: str, class_name: str) -> tuple[str, int]:
    replacements = 0
    while True:
        match = CALL.search(section)
        if not match:
            break
        indent = match.group("indent")
        close = "\n" + indent + "});"
        close_pos = section.find(close, match.end())
        if close_pos < 0:
            raise AssertionError("Closing anyMatch callback not found: " + class_name)
        body = section[match.end():close_pos]
        if not re.search(r"\n\s*return false;\s*$", body):
            raise AssertionError("Callback must end with return false: " + class_name)
        body = re.sub(r"\n\s*return false;\s*$", "\n", body, count=1)
        node = match.group("node")
        kind = match.group("kind")
        assigned = bool(match.group("assign"))
        if kind == "Object":
            if match.group("args") != "key, sub":
                raise AssertionError("Unexpected object lambda parameters")
            prefix = (
                indent + "for (Map.Entry<String, Object> entry : Nodes.entrySetInObject(" + node + ")) {\n"
                + indent + "    String key = entry.getKey();\n"
                + indent + "    Object sub = entry.getValue();\n"
            )
            suffix = indent + "}"
        else:
            if match.group("args") != "i, sub":
                raise AssertionError("Unexpected array lambda parameters")
            prefix = (
                indent + "Iterator<Object> iter = Nodes.iteratorInArray(" + node + ");\n"
                + indent + "int i = 0;\n"
                + indent + "while (iter.hasNext()) {\n"
                + indent + "    Object sub = iter.next();\n"
            )
            suffix = indent + "    i++;\n" + indent + "}"

        if assigned:
            expected = "\n" + indent + "if (shouldAbort) return false;"
            end = close_pos + len(close)
            if not section.startswith(expected, end):
                raise AssertionError("Missing short-circuit check in " + class_name)
            end += len(expected)
            if body.count("return true;") == 0:
                raise AssertionError("No callback early return in " + class_name)
            body = body.replace("return true;", "return false;")
        else:
            if class_name != "ContainsEvaluator":
                raise AssertionError("Unexpected unassigned anyMatch in " + class_name)
            end = close_pos + len(close)
            if body.count("return true;") != 1:
                raise AssertionError("Expected exactly one contains early exit")
            body = body.replace("return true;", "break;")

        section = section[:match.start()] + prefix + body + suffix + section[end:]
        replacements += 1

    if replacements:
        section = re.sub(r"final boolean\[\] result = new boolean\[\]\s*\{\s*true\s*\};",
                         "boolean result = true;", section)
        section = re.sub(r"final int\[\] (propIdx|matches) = new int\[\]\s*\{\s*0\s*\};",
                         r"int \1 = 0;", section)
        for variable in ("result", "propIdx", "matches"):
            section = section.replace(variable + "[0]", variable)
    return section, replacements


def convert(source: str, traversal: str, leaf: str) -> str:
    counts = {"calls": 0, "leaf": 0}
    for start_name, stop_name in SECTIONS:
        start_token = "final class " + start_name + " implements Evaluator"
        start = source.index(start_token)
        stop = source.index("final class " + stop_name + " implements Evaluator", start) if stop_name else len(source)
        section = source[start:stop]
        if traversal == "loop":
            section, n = loopify(section, start_name)
            counts["calls"] += n
        if leaf == "fresh":
            lines = []
            for line in section.splitlines(keepends=True):
                if line.lstrip().startswith("//"):
                    lines.append(line)
                else:
                    counts["leaf"] += line.count("ctx.reusedLeaf()")
                    lines.append(line.replace("ctx.reusedLeaf()", "null"))
            section = "".join(lines)
        source = source[:start] + section + source[stop:]
    if traversal == "loop" and counts["calls"] != 5:
        raise AssertionError("Expected five anyMatch call sites, found " + str(counts))
    if leaf == "fresh" and counts["leaf"] != 6:
        raise AssertionError("Expected six active reusedLeaf call sites, found " + str(counts))
    return source


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--traversal", choices=["walker", "loop"], required=True)
    parser.add_argument("--leaf", choices=["reuse", "fresh"], required=True)
    args = parser.parse_args()
    src = args.source.read_text(encoding="utf-8")
    updated = convert(src, args.traversal, args.leaf)
    if args.traversal == "walker" and args.leaf == "reuse":
        assert updated == src, "The baseline must be identical to pinned main"
    args.output.write_text(updated, encoding="utf-8")
    sha = hashlib.sha256(updated.encode()).hexdigest()[:16]
    print(f"{args.traversal}/{args.leaf}: source sha256={sha}, changed={updated != src}")


if __name__ == "__main__":
    main()
