---
name: prd
description: Turn a feature description into a Product Requirements Document (PRD) at docs/prds/<feature-slug>/prd.md by interviewing the owner in rounds until every rule and edge case is pinned down, then writing a product-only document (no technical design) that a later docs/specs/<feature-slug>/spec.md is derived from. Use this whenever the user describes a new feature or behavior change and wants it defined, scoped, or "written up" before implementation, asks for a PRD, product requirements, requirements doc, feature definition, or "let's define the rules for X", or invokes /prd — even if they don't say the word PRD. Do not use it to write the implementation spec itself (that is the docs/specs workflow in CLAUDE.md); a PRD comes before the spec.
argument-hint: <feature description>
---

# PRD creation

You are producing the product half of this repo's feature workflow. The chain is:

```
feature idea  →  docs/prds/<slug>/prd.md  (this skill: WHAT and WHY, product rules)
              →  docs/specs/<slug>/spec.md (later, separate task: HOW, technical design)
              →  implementation
```

The PRD is the contract the spec author works from. Anything vague, unstated, or assumed here
becomes a guess in the spec and a bug in the code, so the job is to make the owner's intent
explicit, one concrete rule at a time. The owner (the user) holds the product knowledge; you hold
the questions. The PRD contains nothing you made up.

Feature description from the invocation: `$ARGUMENTS`. If it is empty, ask for the feature
description first and do nothing else until you have it.

## Ground rules

1. **Product language only, no technical design.** The PRD describes behavior as a trainer or
   client experiences it, using the product vocabulary: trainer, client, training plan, workout,
   exercise, set, workout log, feedback. It never contains RPC or service names, proto messages,
   request/response fields, tables, columns, migrations, entity or class names, status codes,
   validation annotations, or library choices. Test for a sentence: if it would have to change
   when the database, transport, or framework changed, it does not belong in the PRD.
   The one exception is the owner asking for it: either they *explicitly state* a technical
   requirement ("this must be one call", "old records must be kept as they were"), which you
   record verbatim in the `Technical constraints (stated by the owner)` section and nowhere
   else, or they explicitly ask for the PRD to carry technical content, in which case that
   section holds exactly what they asked for. Never add that section on your own initiative,
   and never turn an offhand technical remark into a constraint without asking whether it is
   one.
2. **Every rule traces to the owner.** A rule comes from the owner's answers, the feature
   description, or `docs/requirements.md`. When you need a default the owner has not given,
   ask; do not fill the gap silently. If the owner says "you decide", propose one option with a
   one-line reason, get a yes, and log it in the Decisions section as a proposed-and-accepted
   decision so the spec author knows it was a choice, not a requirement.
3. **Exhaust before writing.** Do not write the PRD after the first round of answers. Keep
   asking until a full pass over `references/question-bank.md` yields no question whose answer
   could change a rule, and the owner confirms there is nothing left. A short PRD with no open
   questions beats a long one with holes.
4. **Contradictions surface immediately.** If a new answer conflicts with an earlier one or
   with `docs/requirements.md`, point it out in the same round and ask which wins.

## Workflow

### 1. Ground yourself before asking anything

Read, in this order, and take notes in a scratch file rather than in the conversation:

- `docs/requirements.md`: find the sentence(s) this feature traces to, if any. Quote them in
  the PRD's `Related` line the way existing specs do.
- `docs/prds/`: any existing PRD touching the same area, so you do not re-ask what is already
  decided and so new rules do not contradict old ones.
- `docs/specs/`: specs for features this one touches. Their `## 0. Scope decisions` sections
  hold product decisions worded in technical terms; translate them to product language when
  you use them in questions ("today a client can log the same workout more than once in a
  week" rather than naming the repository query that allows it).
- `docs/domain-model.md`: only for vocabulary and how concepts nest.

Reading source code is allowed when you need to know how something behaves today, but the code
is context for better questions, never content for the PRD.

### 2. Open with a restatement

Before the first question, restate the feature in three to five sentences in product terms, and
list the assumptions you are already making. This lets the owner correct the framing before you
spend rounds on questions built on a misreading. Then start round one in the same message.

### 3. Interview in rounds

Each round is four to seven questions, grouped by theme, each one a concrete scenario with the
outcomes you can foresee. Concrete questions get real rules; "any edge cases?" gets "no".

Bad: "How should duplicates be handled?"
Good: "A client finishes Monday's workout, then starts it again the same day. Should the second
attempt be (a) allowed as a separate session, (b) replace the first, or (c) rejected? If you don't
mind, I'd assume (a) since the app already treats each completion as its own session."

Per question, state the option you would default to and why, so the owner can answer with a
single letter when they agree and spend their words where they disagree.

Mechanics:

- Use `AskUserQuestion` for closed questions with a clear set of options (it renders choices the
  owner can click), up to four per call. Put open-ended questions in plain numbered text. Mixing
  both in one round is fine.
- Keep a ledger in a scratch file (the session scratchpad directory when there is one): decided rules, edge cases with their outcomes, open
  questions, and questions you plan to ask next. Update it after every round. Answers spawn new
  questions ("if the second attempt is a separate session, which one does the progress graph
  use?"), and the ledger is how those do not get lost.
- Draw the next round from two sources: follow-ups the previous answers opened, and the
  categories of `references/question-bank.md` you have not yet covered for this feature. Read
  that file at the start of the interview; it is the checklist the exhaustion rule is measured
  against.
- Skip a category only when it genuinely does not apply, and say so in the ledger, not by
  silently assuming.
- Do not pad rounds. If only two real questions remain, ask two.

### 4. Confirm exhaustion

When a full pass over the question bank produces nothing new, send the owner the numbered list
of rules and edge cases from the ledger (not the whole PRD yet) and ask one question: "Is anything
missing or wrong?" Treat additions as a new round. Only when the owner confirms do you write.

### 5. Write the PRD

Path: `docs/prds/<feature-slug>/prd.md`, kebab-case slug, the same slug the spec will later use
(`docs/specs/<feature-slug>/`). Create the directory if needed. Use the template below exactly;
omit only the sections the template marks as conditional.

After writing, tell the owner the path, that the status is `Draft` until they approve it, and
that the next step is a separate task: writing `docs/specs/<feature-slug>/spec.md` from this PRD
following CLAUDE.md. Do not write the spec in the same session unless the owner asks. Do not
commit unless asked.

## PRD template

```markdown
# PRD: <Feature name>

Status: Draft
Owner: <owner email>
Related: `docs/requirements.md` (source requirement: "<quoted sentence>", or "none — new
requirement"), `docs/prds/<other>/prd.md`, `docs/specs/<touched-feature>/spec.md`
Spec: not yet written (will be `docs/specs/<feature-slug>/spec.md`)

## 1. Summary

What the feature is, who it is for, and why it is worth building. Two to four sentences.

## 2. Actors

Who takes part (trainer, client, both) and, per actor, what they can and cannot do within this
feature. Include "cannot" statements the interview surfaced; they are rules too.

## 3. Flows

The main path per actor, as numbered steps in product terms. One flow per actor that
initiates something. Alternate paths go in Rules or Edge cases, not here.

## 4. Rules

Numbered `R1`, `R2`, ... One sentence each, testable, no "should"; write "A client can ..." /
"A trainer cannot ..." / "When X, then Y". Group under short sub-headings if there are more
than about ten. These double as acceptance criteria, so a reader must be able to tell from
the sentence alone whether an implementation satisfies it.

## 5. Edge cases

Numbered `E1`, `E2`, ... Table with columns Scenario | Expected outcome | Rule. Every edge
case the interview raised, including the ones whose answer was "nothing special happens",
because the spec author needs to know that was considered rather than forgotten.

## 6. Out of scope

Bulleted. Things the owner explicitly excluded or deferred, each with the one-line reason.
"Not asked for" is a valid reason. This is where the spec author learns not to build extra.

## 7. Decisions

Table with columns Question | Decision | Why. One row per decision made during the interview
that a reader could reasonably have expected to go another way. Mark rows where you proposed
the default and the owner accepted it as "(proposed, accepted)".

## 8. Open questions

"None." is the target. Otherwise, each item names what is undecided, why the owner deferred
it, and what the spec author should do meanwhile (block, or assume X).

## 9. Technical constraints (stated by the owner)

Conditional: include only when the owner explicitly stated one. Quote the owner. Omit the
section entirely otherwise; do not write "None".
```

## Self-check before handing over

Read the finished file once more against these, and fix before reporting:

- Every rule and edge case maps to an answer in the ledger or a sentence in
  `docs/requirements.md`. Nothing is there because it "seemed sensible".
- No technical vocabulary outside section 9 (search the file for: rpc, proto, request,
  response, field, table, column, migration, entity, repository, service, id, null, status
  code, exception, validation).
- Rules are single testable sentences. A rule containing "and" that could be split, or
  "should", gets rewritten.
- Every "cannot" the owner stated appears somewhere, usually under Actors or Rules.
- The `Related` line quotes the source requirement when one exists.
