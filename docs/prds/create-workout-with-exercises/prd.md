# PRD: Create Workout With Exercises

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "Create a workut that is a training for a
given weekday" and "Assign the created workout into a training plan for a client/costumer" —
this PRD changes how that creation happens, not what gets created), `docs/domain-model.md`
(Workout / Exercise / ExerciseSet hierarchy)
Spec: not yet written (will be `docs/specs/create-workout-with-exercises/spec.md`)

## 1. Summary

Today a trainer builds a workout (one training day) in two disconnected steps: first create an
empty workout, then separately add each exercise to it one at a time. This breaks the trainer's
"build this day" mental flow and makes authoring a training plan slower than it needs to be. This
feature lets a trainer optionally include the full list of exercises — and each exercise's sets —
at the moment they create the workout, in one action, while still allowing an empty workout for
trainers who prefer to fill it in later. The same nested, all-at-once treatment is also added to
editing an existing workout, so a trainer can rewrite a workout's whole exercise list in one step
instead of editing exercises and sets individually.

## 2. Actors

**Trainer**
- Can create a workout with zero, some, or many exercises included at creation time.
- Can include each exercise's sets (reps, weight, strategy, etc.) inline at creation time, or
  leave sets out and add them later.
- Can still add/edit/remove one exercise or one set at a time using the existing step-by-step
  flow, whether or not the workout was created with exercises inline.
- Can bulk-replace an existing workout's whole exercise list (with its nested sets) in one call.
- Cannot bulk-replace a workout's exercise list in a way that would remove an exercise or set the
  client has already recorded actual performance data against (see R11–R12).

**Client**
- Unaffected by this feature. Sees the same finished workout (name, day, exercises, sets)
  regardless of whether the trainer built it in one step or many.
- Cannot create, edit, or bulk-replace anything (existing rule, unchanged).

## 3. Flows

**Trainer: create a workout with exercises in one step**
1. Trainer starts creating a new workout for a training plan (name, day of week, etc., as today).
2. Trainer optionally adds one or more exercises to the same submission, each picked from the
   exercise catalog, in the order they want them performed.
3. For each exercise, the trainer optionally adds one or more sets (reps, weight, strategy,
   etc.), in the order they want them performed.
4. Trainer submits. The workout, its exercises, and their sets are all created together.

**Trainer: create an empty workout (unchanged path, still available)**
1. Trainer creates a workout with no exercises included.
2. Trainer adds exercises (and their sets) one at a time later, using the existing step-by-step
   flow.

**Trainer: bulk-replace an existing workout's exercises**
1. Trainer opens an existing workout and edits its exercise list as a whole — reordering,
   removing, adding exercises and their sets.
2. Trainer submits the new full list.
3. If none of the removed exercises/sets have client-recorded data against them, the workout's
   exercises and sets are replaced with the submitted list.
4. If any removed exercise or set has client-recorded data against it, nothing changes; the
   trainer is told which exercise/set is blocking the update.

## 4. Rules

**Creating a workout with exercises**

- R1: A trainer can create a workout with no exercises, exactly as today.
- R2: A trainer can create a workout with a list of exercises included in the same action.
- R3: For each exercise in that list, the trainer can optionally include a list of sets in the
  same action.
- R4: An exercise entry does not require an explicit position number — its position in the
  submitted list is what determines its order in the workout.
- R5: A set entry does not require an explicit set number — its position in the submitted list
  is what determines its order within the exercise.
- R6: A set entry that does not specify a strategy defaults to a plain working set (the same
  default the app already treats as "no special strategy").
- R7: A workout's exercise list can include the same catalog exercise more than once (e.g. for a
  superset), the same as the existing one-at-a-time flow already allows.
- R8: A workout submission can include at most 20 exercises.
- R9: An exercise entry can include at most 10 sets.
- R10: If any exercise or set in the submitted list is invalid (e.g. references an exercise that
  does not exist in the catalog, or breaks one of this feature's own rules such as the caps in
  R8/R9), nothing is created — the whole submission is rejected, not just the invalid part.

**Editing an existing workout's exercises**

- R11: A trainer can submit a new full exercise list (with nested sets) for an existing workout,
  replacing what was there before: exercises/sets not in the new list are removed, and the new
  list becomes the workout's exercises/sets, in the submitted order.
- R12: If the replacement would remove an exercise or set that a client has already recorded
  actual performance data against, the update is refused entirely and no part of it is applied.
- R13: When an update is refused under R12, the trainer is told specifically which exercise or
  set is blocking it, not just that something is blocking it.
- R14: A client merely having opened (but not yet recorded anything in) this week's session for
  the workout does not block a bulk edit — only exercises/sets with actual recorded data are
  protected.

**Unchanged flows**

- R15: The existing one-exercise-at-a-time and one-set-at-a-time add/edit/remove actions continue
  to work exactly as they do today, independent of whether this bulk create/replace is used.
- R16: The client's experience of viewing and following a workout is unchanged regardless of
  whether the trainer built it in one step or many.

## 5. Edge cases

| # | Scenario | Expected outcome | Rule |
|---|---|---|---|
| E1 | Trainer creates a workout with an empty exercise list. | Same as omitting the list entirely — an empty workout is created. | R1 |
| E2 | Trainer creates a workout with 21 exercises. | Rejected; no workout is created. | R8, R10 |
| E3 | Trainer creates a workout where one exercise entry points at a catalog exercise that doesn't exist. | Nothing is created — workout, valid exercises, and their sets all fail together. | R10 |
| E4 | Trainer creates a workout with an exercise that has 11 sets. | Rejected; no workout is created. | R9, R10 |
| E5 | Trainer includes the same catalog exercise twice in one workout (e.g. as a superset). | Allowed; both entries are created. | R7 |
| E6 | Trainer creates a workout with exercises but leaves one exercise's sets empty. | Allowed — that exercise is created with zero sets, to be filled in later via the existing per-set flow. | R3 |
| E7 | Trainer bulk-replaces a workout's exercise list and none of the removed exercises have client-recorded data. | Replacement proceeds; old exercises/sets not in the new list are gone, new list is in place. | R11 |
| E8 | Trainer bulk-replaces a workout's exercise list, and one of the removed exercises has a client-recorded set against it. | Whole update refused; nothing changes; trainer is told which exercise/set blocked it. | R12, R13 |
| E9 | Client has opened this week's session for the workout but hasn't recorded any set yet, then trainer bulk-replaces exercises that would have been part of that session. | Allowed — an opened-but-empty session doesn't block the edit. | R14 |
| E10 | Two trainers (or the same trainer on two devices) submit conflicting bulk edits to the same workout close together. | Whichever edit is processed last is what sticks; no conflict detection. | — |
| E11 | Trainer still wants to add a single exercise to an already-created workout, one at a time. | Works exactly as it does today, whether or not the workout was created with exercises inline. | R15 |

## 6. Out of scope

- **Nesting one level higher (creating a whole TrainingPlan with Workouts, and their exercises,
  all nested in one call).** Not asked for — this feature closes the friction at workout
  creation specifically; a plan-level version can be a separate future feature if needed.
- **Any change to how a client views or interacts with a workout.** This is purely a
  trainer-authoring change; nothing about the client experience changes.
- **Conflict detection for concurrent edits to the same workout.** Not asked for — matches how
  every other edit in this app already behaves (last write wins).
- **Cloning a workout (`CloneWorkout`).** That feature already deep-copies a workout's full
  exercise/set tree in one call; this PRD doesn't change it.
- **Notifying the client when a trainer changes a workout's exercises.** Not asked for.

## 7. Decisions

| Question | Decision | Why |
|---|---|---|
| Does "training" mean Workout or TrainingPlan? | Workout | Exercises attach to workouts, not plans, per the domain model; that's where the described friction is. (proposed, accepted) |
| Should sets also be nestable at creation, or just exercises? | Both exercises and sets, both optional | Owner wants the full "build this day in one step" experience, not just placing exercises. |
| Reject-whole-submission vs. create-what's-valid on a bad entry? | Reject whole submission | Owner's explicit choice; keeps behavior predictable — the trainer fixes and resubmits. (proposed, accepted) |
| Cap on exercises per submission? | 20 | Owner's explicit number. |
| Cap on sets per exercise per submission? | 10 | Owner's explicit number. (proposed, accepted) |
| Explicit order/set-number per entry, or auto-numbered by list position? | Auto-numbered by list position | Owner's explicit choice; less for the trainer to fill in. (proposed, accepted) |
| Default set strategy when omitted? | A plain/straight working set | Owner's explicit choice; removes a required choice from the common case. (proposed, accepted) |
| Should the existing one-at-a-time add flows still exist? | Yes, unchanged | Owner wants this as an addition, not a replacement. (proposed, accepted) |
| Should updating an existing workout also get bulk-replace? | Yes | Owner's explicit choice, expanding scope beyond creation-only. |
| Bulk-replace semantics: full replace vs. diff/merge? | Full replace | Owner's explicit choice; simpler mental model ("this is now what the workout looks like"). (proposed, accepted) |
| What happens when bulk-replace would remove client-logged data? | Refuse the whole update | Owner's explicit choice; protects client history over trainer convenience. |
| Does an empty started session (no logs yet) block a bulk edit? | No, only actual recorded data blocks it | Owner's explicit choice; avoids blocking on sessions with nothing to protect yet. (proposed, accepted) |
| Can the same exercise appear twice in one workout? | Yes | Matches existing single-add behavior; not a new restriction. (proposed, accepted) |
| Extend nesting to TrainingPlan → Workout level? | No, out of scope | Owner's explicit choice; not asked for. (proposed, accepted) |
| Does R6's default-strategy rule change the existing single-set-create rule (which rejects an unset strategy outright)? | No — the two rules coexist unchanged. The nested create/replace path gets its own default-to-plain-working-set logic; the existing one-exercise/one-set-at-a-time flow keeps rejecting an unset strategy exactly as it does today. | Technical assessment (F1) flagged that R6 is the literal opposite of the existing rule; reusing the existing validation/mapping for both would break one or the other. Keeping them separate satisfies R6 for the new path and R15 ("existing actions continue to work exactly as they do today") for the old one. (proposed, accepted) |

## 8. Open questions

None.
