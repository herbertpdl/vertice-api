# Question bank

The checklist behind the "exhaust before writing" rule in `SKILL.md`. During a PRD interview,
every category below ends in one of three states in your ledger: covered by a rule, explicitly
not applicable (with a reason), or still to ask. You are done asking only when no category is in
the third state.

These are prompts for *your* thinking, not questions to paste. Turn each into a concrete
scenario in the feature's own terms, with the outcomes you can foresee and the default you would
pick. The examples use this product's vocabulary (trainer, client, training plan, workout,
exercise, set, workout log, feedback) because that is what the owner thinks in.

## 1. Actors and permissions

- Who can start this? Trainer, client, both, or something automatic (a date passing, a
  workout being completed)?
- For each actor: what can they see, what can they change, what can they never do? "Cannot"
  answers are rules; write them down.
- Does it matter whose thing it is? A trainer acting on a plan that belongs to another
  trainer's client; a client looking at a workout not assigned to them.
- Is there anything one actor does that the other actor must be told about (feedback reaches the
  trainer; a plan change reaches the client)?

## 2. Triggers and preconditions

- What must already exist or be true before this can happen? (A plan must have a client; a
  workout must be completed before feedback.)
- What happens when a precondition is not met: refused with an explanation, silently ignored,
  or allowed with a warning?
- Can it happen more than once for the same thing? If so, is each occurrence independent, does
  the latest replace the earlier, or is the second refused?

## 3. Inputs

- For each piece of information the actor provides: required or optional? If optional, what
  does its absence mean?
- Limits: minimum, maximum, allowed values, allowed characters, length. Ask about the ones that
  matter to the product ("can a set have zero reps?"), not formats.
- Free text: is there a length the owner cares about? Can it be empty?
- Dates and times: past allowed? future allowed? end before start? whose "today" (the client's
  time zone, the trainer's)?
- Numbers with units: weight in what unit, decimals allowed, negative or zero allowed?
- Choices from a fixed list (level, weekday): can the list grow later, is "none" a valid choice?

## 4. Uniqueness and duplicates

- Can two things have the same name / same day / same target? Within what boundary (per
  plan, per client, per trainer, globally)?
- If duplicates are refused, what does the actor see? If allowed, how are they told apart?

## 5. Lifecycle and state

- What states can the thing be in (draft, active, completed, expired, archived)? What moves it
  between them, and can it move backwards?
- What can and cannot be changed in each state? (Editing a workout a client already logged.)
- What happens on the boundaries: the day a plan ends, the moment a workout is marked done, a
  week rolling over.

## 6. Changes after the fact

- The thing is edited after others have used it: do past uses reflect the change, keep the old
  version, or is the edit refused? (An exercise is renamed after workouts logged it; a set count
  changes after a client recorded weights.)
- Reordering: does order matter (exercises within a workout, sets within an exercise)? Who
  controls it?

## 7. Deletion and removal

- Can it be deleted at all? By whom?
- What happens to everything attached to it: removed with it, kept without a parent, or the
  deletion refused while attachments exist? (Deleting a plan that has logged workouts.)
- Is deletion reversible? Should the actor confirm?
- Does "remove from" differ from "delete" (unassigning a plan from a client vs deleting the
  plan)?

## 8. Quantities and limits

- How many of these can exist per parent? (Plans per client: requirements.md says "N, up to the
  trainer"; confirm there is no cap.)
- Is zero a valid quantity (a workout with no exercises, a plan with no workouts)?
- Is there an ordering when there are many (newest first, by weekday)?

## 9. Time and history

- Does the feature need to remember the past (previous weights, previous feedback)? For how
  long? Can the actor see history or only the latest?
- What counts as "this week" or "today": calendar week starting when?
- Something happens late (feedback submitted days after the workout; a workout logged for a
  past day). Allowed?

## 10. Visibility and privacy

- Who sees the result, and from where (trainer's list, client's plan view)?
- Is anything hidden from one actor that the other can see (a trainer's private notes)?
- Does the client see other clients' anything? (Almost certainly no; confirm once.)

## 11. Simultaneity

- Two actors touch the same thing at the same time (trainer edits a workout while the client is
  doing it). Asked in product terms: whose change wins, or does the client finish on the version
  they started?

## 12. Errors and feedback to the actor

- For each refusal above: does the actor need to know why? Is a generic "not allowed" enough?
- Partial success: a multi-step action fails halfway. Everything undone, or keep what
  succeeded?

## 13. Interaction with existing features

- Walk the existing features this one touches (from `docs/prds/`, `docs/specs/`,
  `docs/requirements.md`). For each: does this feature change how that one behaves, and does
  that one impose a rule on this feature? (Cloning a workout that has feedback: does the clone
  carry the feedback? Presumably not; confirm.)
- Does anything existing become wrong or redundant once this ships?

## 14. Scope boundaries

- What is explicitly *not* part of this? Ask for at least two exclusions; owners rarely think of
  them unprompted and each one is a spec the author does not have to guess about.
- Is there a smaller first version the owner would accept, and what is cut from it?
- What would make the owner consider this feature done? That answer often becomes a rule that
  was not stated anywhere else.

## 15. Naming

- What does the owner call the thing? Use their word everywhere in the PRD. If two words are
  in play (customer / client, student / client), pick one with them and note it in Decisions.
  The product already renamed "student" to "client" once (`docs/specs/user-unification/spec.md`);
  "client" is the current word.
