---
name: dependency-graph-disabled
description: GitHub dependency graph / Dependabot alerts are disabled on herbertpdl Vertice repos, so dependency-review-action can't work; vertice-api uses Grype on the boot jar instead
metadata:
  type: project
---

As of 2026-10-14 the GitHub dependency graph is off for the herbertpdl/vertice-* repos: the SBOM endpoint returns 404, dependency-review compare returns 403, and Dependabot alerts are disabled. vertice-api's CI therefore runs a `dependency-scan` job (Grype on `build/libs` after `./gradlew bootJar`, high cutoff). It is not a required check; only `build-and-test` is.

**Why:** dependency-review-action needs the graph, and turning it on is a repo-owner setting.

**How to apply:** for vuln-gating work, don't propose dependency-review unless the graph has been enabled (re-check the SBOM endpoint first). A red scan is usually fixed by a Boot patch bump or a `set('<lib>.version', ...)` override in build.gradle `ext`.
