# Core test layout

Regular tests mirror the production package they cover. Keep test-only reusable code in
`org.sjf4j.testsupport`; it must not become a production API. JSONPath comparison and
conformance data lives in `org.sjf4j.path.comparison`, while executable documentation
lives in `org.sjf4j.examples`.

`org.sjf4j.obnt` is the exception: it groups tests for the root-package basic OBNT
types (`Nodes`, `JsonObject`, `JsonArray`, `NodeStream`, `NodeKind`, and `JsonType`).
All other tests continue to mirror their production packages.

Incubator tests are in `src/incubatorTest` and run through `incubatorTest`; incubator
implementation sources remain in `src/incubator`. Test resources mirror the package path
of their consuming tests.

Name tests for the concrete behavior. Use `Regression` only for a known regression and
`EdgeCase` only for a grouped boundary suite. Use dynamic tests only for an explicit
backend or streaming-mode matrix; ordinary cases remain individual `@Test` methods.
