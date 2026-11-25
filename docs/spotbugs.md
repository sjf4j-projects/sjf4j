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
`build/reports/spotbugs/` directory as XML and HTML.

On GitHub Actions, select **SpotBugs → Run workflow** and download
`spotbugs-reports`. The workflow also runs on pull requests that change
the workflow itself to verify the integration.
