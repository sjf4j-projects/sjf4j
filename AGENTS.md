# AGENTS

## Mission

SJF4J is a lightweight, high-performance, low-dependency JSON facade and structural processing framework for Java.
The goal is consistent JSON semantics across backends, formats, paths, patches, schemas, and native object graphs without sacrificing speed, simplicity, or package size.

## Priorities

1. Performance first.
2. Correctness and cross-backend consistency.
3. Small source size and small jar size.
4. Simple, direct, readable code.

## Rules

- Do not over-abstract. Prefer simple and obvious code.
- Keep hot paths explicit. This is an open-source base framework; runtime cost matters more than elegance.
- Prefer metadata precomputation and cache reuse over repeated reflection, scanning, or branching.
- Be careful with code size and jar size. Avoid new helper layers or classes unless they clearly pay for themselves.
- Reuse existing serializers/readers/writers when possible instead of adding near-duplicate wrappers.
- Small, direct changes are preferred over large refactors.

## Before Commit

- Run at minimum:
  - `./gradlew :sjf4j:test`
  - `./gradlew :sjf4j-jdk17-test:test`
- Keep branch coverage at or above 70%.
- Run targeted JMH benchmarks for touched hot paths when needed.
- Compare jar size, usually `sjf4j/build/libs/sjf4j-*.jar`.
- If performance regresses by more than 3%, stop and investigate.
- If jar size grows by more than 3%, stop and investigate.

## Review Checklist

- Is this the simplest readable implementation?
- Does it add unnecessary abstraction?
- Does it add per-call overhead on a hot path?
- Does it increase jar size more than necessary?
- Can backend-native behavior handle the global case faster?
