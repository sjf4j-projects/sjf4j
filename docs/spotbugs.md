# SpotBugs analysis

SpotBugs is an independent, diagnostic bytecode analysis task. It is not part
of the ordinary `build`, `check` or remote publishing tasks. Local Maven
publishing (`publishToMavenLocal`, including individual modules) runs
`spotbugsAll` before publishing; findings remain non-blocking.

With JDK 25 and the project's configured compilation toolchains available:

```bash
./gradlew spotbugsAll --continue --max-workers=2
# Focus on generated code:
./gradlew :sjf4j-testbench:spotbugsTest :sjf4j-testbench:spotbugsJmh --continue
```

The aggregate task scans all Java source sets (main, test, JMH and any
additional source sets) of every non-aggregate module. Annotation-processor
outputs in those source sets are included, except for JMH's own harness
classes under `jmh_generated` in the JMH source set. Handwritten benchmarks
and SJF4J-generated implementations remain included. No existing-issue
baseline is configured. Fixtures compiled dynamically during
test execution are not part of this scan.

SpotBugs 4.10.5 runs with JDK 25 using the 6.5.13 Gradle base plugin.
Reports include all confidence levels and are diagnostic: findings do not
fail local publishing. Review analysis errors and missing classes in the reports
as well as bug instances. Reports are written to each module's
`build/reports/spotbugs/` directory as XML and HTML. Each analyzer has a
4 GB heap limit.

`spotbugsAll` prints an aligned plain-text table and saves it at
`build/reports/spotbugs/summary.txt` in the root project. Each completed task
includes a local HTML report URI (on CI, locate the same report in the
`spotbugs-reports` artifact). It lists the
finding totals by SpotBugs rank, analysis errors, and missing classes.
Ranks are grouped into four severity levels:

| Severity | Rank | SpotBugs category |
| --- | --- | --- |
| Critical | 1–4 | Scariest |
| High | 5–9 | Scary |
| Medium | 10–14 | Troubling |
| Low | 15–20 | Of Concern |

The summary does not group by confidence (P1/P2/P3). All confidence levels
remain enabled; individual XML/HTML reports retain their original details.
These are SpotBugs bug ranks, not security vulnerability scores.

Source sets with no classes are marked `NO-SOURCE`; missing or invalid XML
reports (including missing or invalid ranks) and scans that did not complete
are marked incomplete and excluded from totals. A preparation task clears the previous summary,
and its finalizer writes the current summary even if a compilation or scan
task fails. Use `--continue` to collect as many independent reports as possible.
The summary is diagnostic, not a new publication gate; task failures still
fail the build.

On GitHub Actions, select **SpotBugs → Run workflow** and download
`spotbugs-reports`. The aggregate summary also appears in the workflow job
summary. The workflow runs on pull requests that change the workflow or root build
configuration to verify the integration.
