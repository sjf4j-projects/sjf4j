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
outputs in those source sets are included; no generated-code exclusions or
existing-issue baseline are configured. Fixtures compiled dynamically during
test execution are not part of this scan.

SpotBugs 4.10.5 runs with JDK 25 using the 6.5.13 Gradle base plugin.
Reports include all confidence levels and are diagnostic: findings do not
fail local publishing. Review analysis errors and missing classes in the reports
as well as bug instances. Reports are written to each module's
`build/reports/spotbugs/` directory as XML and HTML. Each analyzer has a
4 GB heap limit.

`spotbugsAll` prints a per-task table and saves the aggregate Markdown report
at `build/reports/spotbugs/summary.md` in the root project. It lists the
finding totals by SpotBugs priority (P1/P2/P3/other), analysis errors, and
missing classes. Source sets with no classes are marked `NO-SOURCE`; missing
or invalid XML reports are marked incomplete and excluded from totals.
The summary is diagnostic, not a new publication gate.

On GitHub Actions, select **SpotBugs → Run workflow** and download
`spotbugs-reports`. The aggregate summary also appears in the workflow job
summary. The workflow runs on pull requests that change the workflow itself
to verify the integration.
