---
name: p105-data-subject-rights-split
description: Plan item P1-05 (data-subject rights) ships as three sequential vertice-api PRs; (a) #67 and (b) #68 merged, (c) ExportUserData #69 open; next is the vertice-bff step 2
metadata:
  type: project
---

P1-05 was split (2026-10-14) into sequential PRs, each reviewed and merged by the orchestrator before the next starts:
(a) EndTrainerClientRelationship + delete refusals + DataIntegrityViolationException fallback — PR #67, squash-merged 2026-10-15;
(b) DeleteAccount (anonymise per D5, self only, users.deleted_at revokes tokens via findRoleById, audit line without PII) — PR #68, squash-merged 2026-10-15;
(c) ExportUserData (self only, JSON in a proto string, bounded by vertice.export.*, refused not truncated) — PR #69.

**Why:** the change was too large for one PR (~1,200-line threshold) and (b) is irreversible, so it lands on top of refusals that already hold.
**How to apply:** when asked to continue P1-05, once #69 merges the vertice-api slice is done; the BFF routes (P1-05 step 2) are next, extend `docs/specs/data-subject-rights/spec.md` (it already records (a)'s decisions and the follow-ups), and keep `UserService#deleteUser`'s refusal as the fallback for non-anonymising deletes.
