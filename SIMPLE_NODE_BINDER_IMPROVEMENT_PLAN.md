# SimpleNodeBinder Improvement Plan

## Goal and boundaries

Improve in-memory tree binding performance while preserving binding semantics and
aligning it with `StreamingIO` where both APIs represent the same operation.

Do not introduce a `TreeStreamingReader`/`TreeStreamingWriter` adapter or a new
generic `NodeFieldBinder` layer. `FieldReader` and `FieldWriter` are specialized
for token streams; adapting an existing tree to that protocol would add dispatch,
state, and allocation on the hot path.

Reuse shared metadata and state instead:

- `FieldInfo` for precomputed types, codecs, polymorphism, and accessor metadata.
- `CreatorState` for creator argument, pending property, and dynamic-property
  semantics shared with `StreamingIO`.
- The `FieldReader` rule that properties without a setter are skipped.

## Phase 0: baseline

1. Run the required test suites:
   - `./gradlew :sjf4j:test`
   - `./gradlew :sjf4j-testbench:test`
2. Capture `NodeBindingBenchmark` time and allocation (`-prof gc`) for:
   - full graph read/write
   - nested collections
   - OneOf
   - NodeValue map conversion
3. Add focused benchmarks before later performance changes:
   - POJO-to-POJO conversion
   - Set-to-List, Set-to-array, and Set-to-JsonArray conversion

## Phase 1: correctness and semantic alignment

1. Replace the local creator/pending-property state in
   `SimpleNodeBinder._readPojoFromEntries` with `CreatorState`.
   This makes duplicate canonical-name/alias constructor assignments fail
   consistently with streaming binding and centralizes replay behavior.
2. For a recognized POJO property without a setter, skip its source value before
   converting it. This matches `FieldReader` and avoids both unnecessary work and
   divergent failures for invalid values targeting read-only properties.
3. In the raw-compatible deep-copy fast path, resolve a configured value codec and
   invoke `ValueCodecInfo.valueCopy` before falling back to generic deep-copy.
   Preserve the no-copy fast path when `deepCopy` is false.
4. Add tests for duplicate creator aliases, deferred creator properties and dynamic
   properties, read-only property skipping, and mutable value-codec deep copy.
5. Treat `includeNulls` behavior for `NodeBinder.writeNode` as a separate,
   explicitly reviewed compatibility decision; do not change it incidentally.

## Phase 2: low-risk performance work

1. Remove the intermediate `LinkedHashMap` from POJO-to-POJO binding. Iterate
   readable source properties directly, invoke every getter once, and preserve
   property order.
2. Remove Set-to-ArrayList staging. Iterate the source Set directly for each
   supported array-like target while maintaining indexed error paths.
3. Pre-size default List, Set, and Map targets when source size is known. Leave
   custom containers on the existing `TypeRegistry` factory path.
4. For non-generic-dependent `FieldInfo`, use `fi.type` and `fi.boxed` directly;
   call `Types.resolveMemberType` only for generic-dependent properties.

## Phase 3: profile-gated work

1. Investigate eager `PathSegment.Name`/`PathSegment.Index` allocation only if
   allocation profiling shows it to be material. Preserve full binding paths in
   errors.
2. Consider extra field-level `TypeInfo` caching only if cache-hit lookups are a
   measured bottleneck. Avoid permanently increasing metadata footprint otherwise.

## Verification and acceptance

- Preserve or improve branch coverage (minimum 75%).
- Run both Phase 0 test suites after every implemented phase.
- Use targeted JMH with allocation profiling for touched hot paths; stop and
  investigate a regression above 3%.
- Compare the `sjf4j-core` jar size; stop and investigate growth above 3%.
- Keep changes local, avoid public API additions, and retain source traversal order
  and getter-once behavior.
