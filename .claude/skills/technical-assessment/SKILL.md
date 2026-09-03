---
name: technical-assessment
description: Produce a technical assessment at docs/assessments/<feature-slug>/assessment.md for a feature defined in a PRD (docs/prds/<feature-slug>/prd.md), evaluating what implementing it in this codebase entails across security, privacy, performance, data model and migrations, API and backward compatibility, data integrity, error handling, logging, metrics, testing, architecture fit, rollout, effort and risk, with evidence from the actual code. Use this whenever the user asks to assess, evaluate, review the feasibility of, estimate, or find the risks of implementing a feature or PRD, asks "what would it take to build X", wants a technical review of a PRD before the spec, or invokes /technical-assessment — even if they don't say "assessment". Also use it when the user asks to assess an existing implementation (a branch, PR, or diff) against its PRD. Do not use it to write the spec itself (docs/specs workflow in CLAUDE.md) or for a plain code review of a diff with no PRD (/code-review).
argument-hint: <PRD path or feature slug> [branch, PR, or diff to assess]
---

# Technical assessment

This is the step between the product definition and the technical design:

```
docs/prds/<slug>/prd.md              WHAT and WHY (product rules, no technical content)
docs/assessments/<slug>/assessment.md WHAT IT TAKES: where each rule lands in the code,
                                      what it risks, what the spec must decide   ← this skill
docs/specs/<slug>/spec.md             HOW (the design; its "Scope decisions" answer this file)
implementation
```

The reader is the spec author, who will otherwise discover these things one at a time while
coding. A good assessment is specific to this codebase and this feature. A generic checklist
("consider adding logging") wastes their time; "no code path logs anything today, so the
feature's first log line sets the convention, decide the format in the spec" is useful. Every
finding therefore needs evidence: a file and line, a migration, a proto field, a PRD rule.

Input from the invocation: `$ARGUMENTS`. The first token is the PRD path or the feature slug
(`docs/prds/<slug>/prd.md`). An optional second reference names an existing implementation
(branch, PR number, or diff); when present, the assessment is of that code, not of a plan. If
there is no PRD for the feature, stop and say so: the assessment measures a PRD against the
codebase, and without one it has nothing to measure. Suggest `/prd-creator`.

## Principles

1. **Evidence over opinion.** Each finding cites where it is grounded: `file:line`, a
   migration version, a proto message and field number, or a PRD rule id (`R3`, `E2`). A
   claim about how the codebase behaves today is verified by reading the code in this
   session, not recalled from the reference file (which captures a snapshot and drifts).
2. **Findings, not a design.** The assessment says what the spec must decide and what it
   must not do, ranks the risks, and may recommend an option with reasons. It does not write
   the proto, the migration, or the service. Drawing the line: "the spec has to choose between
   storing the snapshot and recomputing it, and here is why recomputing breaks E4" is
   assessment; the field list of the snapshot table is spec.
3. **Severity is honest.** A Blocker means the PRD cannot be implemented safely as written or
   would lose or expose data; use it rarely and only with evidence. Inflating severity teaches
   the reader to ignore it. "Nothing found" is a valid result for a dimension when you state
   what you checked.
4. **The whole PRD, every rule.** Every `R` and `E` in the PRD appears in the coverage map.
   A rule that lands nowhere in the code is a finding (new surface) and a rule that the code
   already violates today is a finding (existing gap); both are easy to miss when reading
   selectively.
5. **Ask the owner only what the code cannot answer.** Who consumes the API today, whether
   there is production data to migrate, how much traffic to expect. Batch these into one
   round at the end of the investigation, before writing. If no answer is available, write
   the assessment with the assumption stated in section 9 rather than blocking on it.

## Workflow

### 1. Read the PRD, then map it

Read the PRD end to end. In a scratch file, list every rule (`R*`), edge case (`E*`),
decision, and out-of-scope item. For each rule and edge case, decide where it lands:

- **existing** code that already satisfies it (cite it),
- **existing** code that must change (cite it),
- **new** surface (name the aggregate under `src/main/java/com/vertice/api/` it belongs to),
- or **unclear**, which becomes a question in section 9.

This map is section 2 of the document and it drives everything after it: you assess the code
the map points at, not the codebase in general.

### 2. Investigate the codebase

Read the aggregates the map touches: entity, repository, service, controller, mapper, proto,
tests, and the migrations that built their tables. Read the specs for those aggregates
(`docs/specs/*/spec.md`), especially `## 0. Scope decisions`: they record accepted gaps (the
recurring "no ownership check" one, for instance) that the new feature either inherits or has
to close. Read `CLAUDE.md` for the conventions the feature must follow.

Then work through `references/dimensions.md` one dimension at a time. It lists, per
dimension, what to look for and what this codebase's baseline was when the file was written,
with the commands to re-verify each baseline. Verify before you cite.

When an implementation reference was given, do the same reading on that branch or diff, and
add to the coverage map, per rule, where it is implemented and which test covers it. A rule
with no test is a finding.

### 3. Ask the owner what remains

Collect the questions the investigation could not answer and ask them in one batch, using
`AskUserQuestion` for closed choices and plain text for open ones. Typical ones: which clients
consume the affected RPCs and whether they can be redeployed together with the API; whether
there is production data in the affected tables; expected volumes. Do not ask what the code
or the PRD already answers.

### 4. Write

Path: `docs/assessments/<feature-slug>/assessment.md`, same slug as the PRD and the future
spec. Create the directory. Use the template below exactly. Then tell the owner the path, the
overall risk rating, the Blocker and High findings in one line each, and that the next step is
`docs/specs/<feature-slug>/spec.md`, whose `Related` line must point at both the PRD and this
file and whose scope decisions must resolve every Blocker and High finding. Do not write the
spec in the same session unless asked. Do not commit unless asked.

## Findings

Each finding has an id (`F1`, `F2`, ... numbered across the whole document), a severity, a
dimension, and four parts:

- **What**: the issue or constraint, one or two sentences.
- **Where**: `file:line`, migration, proto field, or "new surface" plus the PRD rule(s) it
  relates to.
- **Why it matters**: the consequence if ignored, concrete to this feature.
- **Recommendation**: what the spec should decide or do. Options with a preferred one when
  there is a real choice.

Severity scale:

| Severity | Meaning | Spec must |
|---|---|---|
| Blocker | PRD cannot be implemented safely as written, or would lose, corrupt, or expose data | Resolve, or the PRD goes back to the owner |
| High | Real risk to correctness, security, or compatibility if not designed for | Resolve in `## 0. Scope decisions` |
| Medium | Would cause rework, operational pain, or a known gap if ignored | Resolve or explicitly defer with a reason |
| Low | Improvement worth taking while in the code | Optional |
| Info | Observation the spec author benefits from knowing; no action | Nothing |

## Template

```markdown
# Technical assessment: <Feature name>

Status: Draft
Owner: <owner email>
Related: `docs/prds/<slug>/prd.md`, `docs/specs/<touched>/spec.md` (touched), <branch/PR if
assessing an implementation>
Spec: not yet written (will be `docs/specs/<slug>/spec.md`)

## 1. Summary

Overall risk: Low | Medium | High. Three to five sentences: what the feature touches, the
top findings, and the recommended approach if there is a choice to make. Then a table of
Blocker and High findings: Id | Severity | One line.

## 2. PRD coverage map

Table: Rule | Lands on | Notes. One row per `R*` and `E*`. "Lands on" is a file reference,
"new: <aggregate>", or "unclear (Q1)". When assessing an implementation, add columns
Implemented at | Tested by.

## 3. Current state

What exists today that the feature builds on or changes, in a few paragraphs with file
references. Accepted gaps inherited from earlier specs, quoted with their spec path.

## 4. Findings by dimension

One `###` sub-heading per dimension from `references/dimensions.md`, in that order. Under
each: the findings (format above), or "Nothing found. Checked: <what you looked at>."

## 5. Options

Conditional: only when there are materially different ways to implement the feature. Per
option: description, findings it resolves or triggers, cost. End with the recommendation
and why. Omit the section otherwise.

## 6. Testing strategy

What must be tested to prove each Blocker/High finding is handled and each PRD rule holds,
mapped onto this repo's pattern (Service test with Mockito, Controller test over a real
gRPC channel). Name the scenarios, not the test code.

## 7. Rollout

Migration and deploy ordering, whether old and new API clients can coexist, what to check
after deploy, how to roll back. "No special handling: additive change only" is a valid
answer when it is true.

## 8. Effort and risk

Size (S / M / L / XL) with the reasoning in two or three sentences, the risks that could
change it, and dependencies on other work.

## 9. Questions and assumptions

Questions the owner still has to answer (`Q1`, `Q2`, ...) and the assumption the spec should
use meanwhile. "None." when there are none.

## 10. Inputs to the spec

Checklist of decisions the spec's `## 0. Scope decisions` must contain, one per Blocker,
High, and non-deferred Medium finding, phrased as the decision to make, not the answer.
```

## Self-check before handing over

- Every `R*` and `E*` from the PRD is in the coverage map. Diff the two lists.
- Every finding has all four parts and a real location. "Somewhere in the service layer" is
  not a location.
- Every baseline claim ("nothing logs today", "no list call paginates") was re-verified in
  this session, not copied from the reference file.
- Blockers have evidence a skeptical reader would accept. If they do not, downgrade.
- Section 10 has one line per Blocker and High finding.
- The document recommends and constrains; it does not contain proto definitions, DDL, or
  Java. Code snippets are allowed only when quoting existing code as evidence.
