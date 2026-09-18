# PRD: Starter exercise catalog

Status: Draft
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirement: "Create new excercises if they don't find
the one they want"), `docs/prds/create-workout-with-exercises/prd.md`,
`docs/specs/grpc-exercise-catalog/spec.md`, `docs/specs/exercise-video-url/spec.md`,
`docs/specs/workout-exercise-crud/spec.md`, `docs/specs/clone-workout/spec.md`,
`docs/domain-model.md`
Spec: not yet written (will be `docs/specs/exercise-starter-catalog/spec.md`)

## 1. Summary

A trainer building a workout today picks from an exercise catalog that is practically empty, so
every exercise has to be typed in by hand before it can be used. This feature ships a curated
starter set of around two hundred exercises in Brazilian Portuguese, organised by muscle group
and available to every trainer on the platform, so adding an exercise becomes picking from a
list. It also makes a trainer's own exercises private to them, and lets an exercise belong to
more than one muscle group.

## 2. Actors

**Trainer.** Sees the whole starter set and their own exercises. Can create their own exercises,
rename them, and delete them while no workout uses them. Can filter the list by muscle group and
search it by name. Cannot rename or delete any starter-set exercise, cannot see another
trainer's exercises, and cannot add, rename, or remove a muscle group.

**Client.** Takes no action in this feature. Sees every exercise that appears in their own
workouts, including ones their trainer created privately. Cannot browse the catalog, cannot
create an exercise, and cannot change one.

**Platform team.** The only party that can add an exercise to the starter set, or add, rename or
remove a muscle group. Confirms, before the starter set arrives, that the exercises predating it
carry only test data, and keeps any that carries genuine history. Has no ability to see a
trainer's private exercises.

## 3. Flows

**Trainer adds an exercise to a workout.**

1. The trainer opens a workout they are building.
2. The trainer chooses to add an exercise.
3. The trainer narrows the list to a muscle group, types part of a name, or both.
4. The list shows the trainer's own matching exercises first, then the matching starter-set
   exercises. Among those primarily filed under the narrowed group, the most commonly prescribed
   comes first — an exercise matching only through a secondary group can appear among them
   without a specified position.
5. The trainer picks one and it is added to the workout.

**Trainer creates an exercise that is not in the catalog.**

1. The trainer searches and finds nothing suitable.
2. The trainer creates a new exercise, giving it a name and at least one muscle group, and
   optionally a description and a video link.
3. The new exercise is visible only to that trainer, and appears in their list from then on.

## 4. Rules

### Starter set content

- **R1** The catalog contains a starter set of exercises available to every trainer on the
  platform.
- **R2** Every starter-set exercise carries a name.
- **R3** Every starter-set exercise carries at least one muscle group.
- **R4** No starter-set exercise carries a description.
- **R5** No starter-set exercise carries a video link.
- **R6** Every starter-set exercise name is written in Brazilian Portuguese, except for an
  established loanword Brazilian gyms commonly use untranslated for a specific piece of
  equipment or movement (for example "Smith", "Leg press", "Superman", "Sissy squat",
  "Bird dog", or "Assault bike").
- **R7** Every starter-set exercise name states the equipment used, where the equipment
  distinguishes it from another exercise.
- **R8** At launch, before any change the platform team makes under R33, the starter set covers
  exactly these fourteen muscle groups: Peito, Costas, Ombros, Bíceps, Tríceps, Antebraço,
  Quadríceps, Posteriores de coxa, Glúteos, Panturrilhas, Abdômen, Lombar, Trapézio, Cardio.
- **R9** Every muscle group name is written in Brazilian Portuguese.
- **R10** The starter set contains only exercises a trainer would genuinely prescribe, with no
  minimum number per group.
- **R11** An exercise that trains more than one muscle group carries every muscle group it
  trains.
- **R12** At launch, before any addition the platform team makes under R32, the starter set is
  the list in section 10 of this document.

### Visibility

- **R13** Every trainer sees every starter-set exercise.
- **R14** In the catalog, an exercise created by a trainer is visible only to the trainer who
  created it (a client seeing it inside their own workout is the separate case R19 defines).
- **R15** A trainer cannot see an exercise created by another trainer.
- **R16** A trainer's attempt to fetch another trainer's exercise by identifier is refused, not
  merely absent from their list, even when they know the identifier.
- **R17** A trainer's attempt to add another trainer's exercise to a workout is refused, even
  when they know the identifier.
- **R18** Cloning a workout is refused unless both the source workout and the target training
  plan belong to the trainer attempting it, since the clone otherwise carries the source's
  private exercises with it.
- **R19** A client sees every exercise that appears in their own workouts, including exercises
  their trainer created.
- **R20** A client's attempt to browse the catalog is refused, not merely unoffered by the app.
- **R21** A client's attempt to fetch an exercise by identifier outside their own workouts is
  refused, not merely unoffered by the app, so another trainer's private exercise cannot be
  reached by guessing its identifier.
- **R22** The platform team cannot see an exercise created by a trainer.

### Changing exercises

- **R23** A trainer cannot rename a starter-set exercise.
- **R24** A trainer cannot delete a starter-set exercise.
- **R25** The trainer's app does not offer the rename or delete action for a starter-set
  exercise.
- **R26** A trainer can rename an exercise they created.
- **R27** A trainer's attempt to rename an exercise they did not create is refused, even when
  they know its identifier, the same as fetching it (R16).
- **R28** When a trainer renames an exercise they created, the new name appears in every workout
  using it, including workouts already completed.
- **R29** A trainer cannot delete an exercise they created while any workout uses it.
- **R30** A trainer can delete an exercise they created when no workout uses it.
- **R31** A trainer's attempt to delete an exercise they did not create is refused, even when
  they know its identifier, the same as R27.
- **R32** Only the platform team can add an exercise to the starter set.
- **R33** Only the platform team can add, rename or remove a muscle group.

### Creating exercises

- **R34** A trainer can create an exercise by giving it a name and at least one muscle group.
- **R35** A trainer cannot create an exercise with no muscle group.
- **R36** A trainer can give an exercise they create a description.
- **R37** A trainer can give an exercise they create a video link.
- **R38** A trainer can give an exercise they create more than one muscle group.
- **R39** A trainer can create an exercise whose name already exists in the catalog.

### Finding exercises

- **R40** A trainer can narrow the exercise list to one muscle group.
- **R41** A trainer can search the exercise list by name.
- **R42** Narrowing to a muscle group shows every exercise carrying that group.
- **R43** Within a muscle group, the exercises the trainer created appear before the starter-set
  exercises.
- **R44** Within a muscle group, the starter-set exercises listed under that group in section 10
  appear in the order given there, most commonly prescribed first.
- **R45** When an exercise is shown for one of its other groups instead (per R42), it is
  included, but its position relative to the exercises listed under that group in section 10 is
  not specified.

### Exercises that predate the starter set

- **R46** Every exercise that existed before the starter set is removed from the catalog when
  the starter set arrives, unless the platform team keeps it under R51.
- **R47** When an exercise that existed before the starter set is removed, every workout entry
  referring to it is removed with it.
- **R48** Removing a workout entry under R47 discards the logged weights and reps recorded
  against that entry.
- **R49** The session a workout entry removed under R47 belonged to keeps its own record, with
  its written feedback and its other exercises unaffected, since those aren't tied to any one
  exercise.
- **R50** The removal under R46 happens once, when the starter set arrives, so an exercise a
  trainer creates afterward is never removed this way while a workout uses it (R29).
- **R51** An exercise predating the starter set that the platform team finds to carry genuine
  trainer or client history rather than test data is kept, together with every workout entry
  and logged weight referring to it, instead of being removed.

## 5. Edge cases

| # | Scenario | Expected outcome | Rule |
|---|---|---|---|
| E1 | A trainer attempts to rename a starter-set exercise even though the app does not offer it | Refused, and the trainer is told the exercise belongs to the shared starter set | R23, R25 |
| E2 | A trainer attempts to delete a starter-set exercise even though the app does not offer it | Refused, and the trainer is told the exercise belongs to the shared starter set | R24, R25 |
| E3 | A trainer creates an exercise named "Supino reto com barra", which is already in the starter set | Allowed; both exist, and the trainer's own copy is visible only to them | R39, R14 |
| E4 | Two trainers each create an exercise with the same name | Both exist, and each trainer sees only their own | R14, R39 |
| E5 | A trainer deletes an exercise they created that a client has already logged weights against | Refused, because a workout uses it | R29 |
| E6 | A trainer renames an exercise they created after a client logged weights against it | The new name appears on the past session; the logged weights are unchanged | R28 |
| E7 | A client opens a workout containing an exercise their trainer created privately | The client sees the exercise like any other | R19 |
| E8 | A trainer narrows the list to Lombar and sees "Levantamento terra com barra" | Expected, because that exercise carries both Costas and Lombar | R11, R42 |
| E9 | A trainer wants a video on a starter-set exercise | Not possible; the trainer creates their own exercise with a video instead | R5, R37 |
| E10 | A trainer wants to prescribe a treadmill session | Cardio is one of the fourteen groups and has starter-set exercises | R8 |
| E11 | A group such as Panturrilhas has far fewer than twenty exercises | Expected; the starter set is not padded to reach a count | R10 |
| E12 | An exercise predating the starter set is used by a workout when it is removed | The workout entry referring to it is removed as well, discarding the logged weights and reps recorded against it; the session's feedback and its other exercises are unaffected | R47, R48, R49 |
| E13 | A trainer creates an exercise without choosing a muscle group | Refused | R35 |
| E14 | A trainer searches for a name that exists only in another trainer's exercises | Nothing is found | R15 |
| E15 | A trainer searches for an exercise whose group they guessed wrong | The name search finds it regardless of group | R41 |
| E16 | A client tries to open the catalog directly, or to fetch an exercise by identifier, outside their own workouts, even though the app does not offer it | Refused | R20, R21 |
| E17 | A trainer who learns another trainer's private exercise identifier fetches it directly, or adds it to their own workout | Refused | R16, R17 |
| E18 | A trainer clones a workout that belongs to another trainer, or clones their own workout into another trainer's training plan | Refused | R18 |
| E19 | A trainer who learns another trainer's private exercise identifier attempts to rename or delete it | Refused | R27, R31 |

## 6. Out of scope

- Videos on starter-set exercises. Deferred to separate platform work; trainers cannot supply
  them.
- Descriptions on starter-set exercises. The owner specified name and muscle group only.
- A trainer proposing that one of their exercises join the starter set. A review flow was
  considered and rejected as far beyond this feature.
- Any surface for the platform team to manage exercises or browse trainers' private exercises.
  No such surface exists in the product.
- Trainers creating or renaming muscle groups. The group list is fixed for trainers so the
  filter stays meaningful across the platform.
- Sharing exercises between two trainers. Not asked for.
- Redefining the muscle-group list after launch, and what happens to an exercise carrying a
  group the platform team later renames or removes. R33 grants that ability; the remapping
  behavior for affected exercises is deferred until the platform team needs it.

## 7. Decisions

| Question | Decision | Why |
|---|---|---|
| Are exercises shared or private? | The starter set is shared with every trainer; an exercise a trainer creates is private to them | The catalog is fully shared today, so this introduces trainer ownership deliberately, matching the owner's framing of "the ones they have added by themselves" |
| How specific are muscle groups? | Fourteen specific groups, splitting the current Braços and Pernas | The owner named biceps as a group, which the current seven do not provide; a precise filter is the point of having groups |
| Is Cardio a muscle group? | Yes, kept and given starter-set exercises | Dropping it left no home for treadmill or bike work, so a trainer could not catalogue cardio at all |
| Can a starter-set exercise be changed? | No trainer can rename or delete one, and the app does not offer the action | One trainer's edit would otherwise change the shared list for everyone |
| What happens to exercises created before this? | Removed, along with any workout entry referring to them | They are a few test entries carrying groups that no longer exist; keeping them would leave ownerless exercises in a shared list |
| A trainer creates a name that already exists | Allowed, both exist (proposed, accepted) | The catalog already tolerates near-duplicates by design, and a trainer's copy only clutters their own list |
| Deleting an exercise already used in a workout | Refused while any workout uses it (proposed, accepted) | A client would otherwise lose an exercise from their logged history |
| Renaming an exercise already used in a workout | Applies everywhere, past workouts included (proposed, accepted) | It is the same exercise being corrected, not a different one |
| Minimum exercises per group | No minimum; only exercises a trainer would genuinely prescribe | Padding thin groups such as Antebraço with contrived variations would make the list worse, not better |
| How are exercise names written? | Naming the equipment, for example "Supino reto com barra" (proposed, accepted) | Barbell and dumbbell versions differ in how a trainer prescribes load, and this is how they are known in a Brazilian gym |
| Where an exercise trains two groups | It carries both, rather than being filed under one | The owner chose a list of groups per exercise over a single group; the group an exercise is listed under in section 10 only fixes its ordering there (R44), it is not the only group the exercise carries |
| Who maintains the starter set and the groups | The platform team only | Trainers changing either would make the shared list and the filter drift |
| Ordering within a group | The trainer's own first, then the starter set with the most commonly prescribed first | The owner asked for the most popular exercises, and a trainer's own are few and deliberately created |
| Where the exercise list lives | In this document, section 10 | Which exercises ship is a product decision the owner wanted to review before implementation |

## 8. Open questions

None.

## 9. Technical constraints (stated by the owner)

Two constraints were stated explicitly by the owner and are recorded verbatim.

> "We need to add this exercises in the database via a migration and it should be available for
> every trainer using the platform."

> "The exercise can have a list of groups when recorded on a database, then we create a new table
> for the muscular groups and on the exercise records we refer the group ids that it belong to"

## 10. Starter set

199 exercises. Each exercise is listed once, under the group it trains most, and every group it
carries is named beside it. An exercise appears under each of its groups when a trainer narrows
the list, so one listed here under Costas and also carrying Lombar appears under Lombar too.
Within the group an exercise is listed under here, the order below is the order a trainer sees,
most commonly prescribed first; when an exercise instead appears under one of its other groups,
its position there is not specified (R45).

### Peito

| # | Exercise | Groups |
|---|---|---|
| 1 | Supino reto com barra | Peito, Tríceps |
| 2 | Supino inclinado com halteres | Peito, Ombros |
| 3 | Supino inclinado com barra | Peito, Ombros |
| 4 | Supino reto com halteres | Peito, Tríceps |
| 5 | Crucifixo reto com halteres | Peito |
| 6 | Peck deck na máquina | Peito |
| 7 | Crossover na polia alta | Peito |
| 8 | Supino declinado com barra | Peito, Tríceps |
| 9 | Flexão de braço | Peito, Tríceps |
| 10 | Supino reto na máquina | Peito, Tríceps |
| 11 | Crucifixo inclinado com halteres | Peito, Ombros |
| 12 | Crossover na polia baixa | Peito |
| 13 | Supino inclinado na máquina | Peito, Ombros |
| 14 | Supino reto no Smith | Peito, Tríceps |
| 15 | Pullover com halter | Peito, Costas |
| 16 | Supino declinado com halteres | Peito, Tríceps |
| 17 | Mergulho nas paralelas com tronco inclinado | Peito, Tríceps |
| 18 | Supino inclinado no Smith | Peito, Ombros |
| 19 | Crucifixo na máquina inclinada | Peito |
| 20 | Flexão de braço com pés elevados | Peito, Ombros |

### Costas

| # | Exercise | Groups |
|---|---|---|
| 1 | Puxada alta na polia com pegada pronada | Costas |
| 2 | Remada curvada com barra | Costas, Lombar |
| 3 | Remada baixa na polia com triângulo | Costas |
| 4 | Barra fixa pronada | Costas, Bíceps |
| 5 | Remada unilateral com halter | Costas |
| 6 | Puxada alta na polia com pegada supinada | Costas, Bíceps |
| 7 | Levantamento terra com barra | Costas, Lombar, Posteriores de coxa |
| 8 | Remada cavalinho | Costas |
| 9 | Barra fixa supinada | Costas, Bíceps |
| 10 | Remada na máquina articulada | Costas |
| 11 | Puxada alta na polia com triângulo | Costas |
| 12 | Remada sentada na máquina | Costas |
| 13 | Pulldown com braços estendidos na polia | Costas |
| 14 | Remada curvada com halteres | Costas, Lombar |
| 15 | Puxada alta com pegada aberta | Costas |
| 16 | Remada com pegada neutra na polia | Costas |
| 17 | Remada no Smith | Costas |
| 18 | Pullover na polia alta | Costas |
| 19 | Barra fixa australiana | Costas |
| 20 | Puxada frontal na máquina | Costas |
| 21 | Levantamento terra sumô | Costas, Glúteos, Posteriores de coxa |
| 22 | Remada curvada com pegada supinada | Costas, Bíceps |

### Ombros

| # | Exercise | Groups |
|---|---|---|
| 1 | Desenvolvimento com halteres sentado | Ombros, Tríceps |
| 2 | Elevação lateral com halteres | Ombros |
| 3 | Desenvolvimento com barra à frente | Ombros, Tríceps |
| 4 | Elevação frontal com halteres | Ombros |
| 5 | Crucifixo inverso com halteres | Ombros |
| 6 | Desenvolvimento na máquina | Ombros, Tríceps |
| 7 | Elevação lateral na polia | Ombros |
| 8 | Face pull na polia | Ombros, Trapézio |
| 9 | Desenvolvimento militar em pé com barra | Ombros, Tríceps |
| 10 | Voador inverso na máquina | Ombros |
| 11 | Elevação frontal com barra | Ombros |
| 12 | Remada alta com barra | Ombros, Trapézio |
| 13 | Elevação lateral na máquina | Ombros |
| 14 | Desenvolvimento Arnold com halteres | Ombros, Tríceps |
| 15 | Elevação frontal na polia | Ombros |
| 16 | Elevação lateral inclinada com halter | Ombros |
| 17 | Desenvolvimento no Smith | Ombros, Tríceps |
| 18 | Remada alta com halteres | Ombros, Trapézio |
| 19 | Elevação posterior na polia | Ombros |
| 20 | Elevação lateral com anilha | Ombros |

### Bíceps

| # | Exercise | Groups |
|---|---|---|
| 1 | Rosca direta com barra | Bíceps |
| 2 | Rosca alternada com halteres | Bíceps |
| 3 | Rosca martelo com halteres | Bíceps, Antebraço |
| 4 | Rosca direta com barra W | Bíceps |
| 5 | Rosca scott com barra W | Bíceps |
| 6 | Rosca concentrada com halter | Bíceps |
| 7 | Rosca na polia baixa com barra | Bíceps |
| 8 | Rosca scott na máquina | Bíceps |
| 9 | Rosca inclinada com halteres | Bíceps |
| 10 | Rosca martelo na polia com corda | Bíceps, Antebraço |
| 11 | Rosca inversa com barra W | Bíceps, Antebraço |
| 12 | Rosca scott com halteres | Bíceps |
| 13 | Rosca 21 com barra | Bíceps |
| 14 | Rosca direta com halteres | Bíceps |
| 15 | Rosca na polia alta unilateral | Bíceps |
| 16 | Rosca martelo cruzada com halter | Bíceps, Antebraço |

### Tríceps

| # | Exercise | Groups |
|---|---|---|
| 1 | Tríceps na polia com barra reta | Tríceps |
| 2 | Tríceps na polia com corda | Tríceps |
| 3 | Tríceps testa com barra W | Tríceps |
| 4 | Mergulho nas paralelas | Tríceps, Peito |
| 5 | Supino fechado com barra | Tríceps, Peito |
| 6 | Tríceps francês com halter | Tríceps |
| 7 | Tríceps coice com halter | Tríceps |
| 8 | Tríceps testa com halteres | Tríceps |
| 9 | Tríceps na máquina | Tríceps |
| 10 | Mergulho no banco | Tríceps |
| 11 | Tríceps na polia com barra V | Tríceps |
| 12 | Extensão de tríceps acima da cabeça na polia com corda | Tríceps |
| 13 | Tríceps coice na polia | Tríceps |
| 14 | Tríceps unilateral na polia com pegada inversa | Tríceps |
| 15 | Flexão diamante | Tríceps, Peito |
| 16 | Supino fechado no Smith | Tríceps, Peito |

### Antebraço

| # | Exercise | Groups |
|---|---|---|
| 1 | Rosca de punho com barra | Antebraço |
| 2 | Rosca de punho inversa com barra | Antebraço |
| 3 | Rosca de punho com halteres | Antebraço |
| 4 | Rolo de punho | Antebraço |
| 5 | Farmer's walk com halteres | Antebraço, Trapézio |
| 6 | Pegada em pinça com anilhas | Antebraço |
| 7 | Suspensão na barra fixa | Antebraço |
| 8 | Rosca de punho na polia | Antebraço |
| 9 | Rosca inversa na polia com barra reta | Antebraço, Bíceps |

### Quadríceps

| # | Exercise | Groups |
|---|---|---|
| 1 | Agachamento livre com barra | Quadríceps, Glúteos |
| 2 | Leg press 45 | Quadríceps, Glúteos |
| 3 | Cadeira extensora | Quadríceps |
| 4 | Agachamento no Smith | Quadríceps, Glúteos |
| 5 | Hack machine | Quadríceps, Glúteos |
| 6 | Afundo com halteres | Quadríceps, Glúteos |
| 7 | Agachamento búlgaro com halteres | Quadríceps, Glúteos |
| 8 | Agachamento frontal com barra | Quadríceps |
| 9 | Leg press horizontal | Quadríceps, Glúteos |
| 10 | Passada com halteres | Quadríceps, Glúteos |
| 11 | Agachamento goblet com halter | Quadríceps, Glúteos |
| 12 | Afundo no Smith | Quadríceps, Glúteos |
| 13 | Cadeira extensora unilateral | Quadríceps |
| 14 | Step up no banco com halteres | Quadríceps, Glúteos |
| 15 | Agachamento sumô com halter | Quadríceps, Glúteos |
| 16 | Agachamento pêndulo | Quadríceps, Glúteos |
| 17 | Leg press unilateral | Quadríceps, Glúteos |
| 18 | Agachamento livre com peso corporal | Quadríceps, Glúteos |
| 19 | Sissy squat | Quadríceps |
| 20 | Agachamento na barra guiada | Quadríceps, Glúteos |

### Posteriores de coxa

| # | Exercise | Groups |
|---|---|---|
| 1 | Mesa flexora | Posteriores de coxa |
| 2 | Cadeira flexora | Posteriores de coxa |
| 3 | Stiff com barra | Posteriores de coxa, Glúteos, Lombar |
| 4 | Levantamento terra romeno com barra | Posteriores de coxa, Glúteos, Lombar |
| 5 | Stiff com halteres | Posteriores de coxa, Glúteos, Lombar |
| 6 | Flexora em pé unilateral | Posteriores de coxa |
| 7 | Levantamento terra romeno com halteres | Posteriores de coxa, Glúteos, Lombar |
| 8 | Bom dia com barra | Posteriores de coxa, Lombar |
| 9 | Mesa flexora unilateral | Posteriores de coxa |
| 10 | Nordic curl | Posteriores de coxa |
| 11 | Stiff no Smith | Posteriores de coxa, Glúteos, Lombar |
| 12 | Flexora na polia | Posteriores de coxa |
| 13 | Levantamento terra com pernas estendidas | Posteriores de coxa, Lombar |

### Glúteos

| # | Exercise | Groups |
|---|---|---|
| 1 | Elevação pélvica com barra | Glúteos, Posteriores de coxa |
| 2 | Coice na polia | Glúteos |
| 3 | Cadeira abdutora | Glúteos |
| 4 | Abdução de quadril na polia | Glúteos |
| 5 | Elevação pélvica na máquina | Glúteos, Posteriores de coxa |
| 6 | Coice na máquina | Glúteos |
| 7 | Ponte de glúteo no solo | Glúteos |
| 8 | Elevação pélvica unilateral | Glúteos, Posteriores de coxa |
| 9 | Afundo com barra | Glúteos, Quadríceps |
| 10 | Extensão de quadril no banco | Glúteos |
| 11 | Step up alto no banco com halteres | Glúteos, Quadríceps |
| 12 | Passada lateral com elástico | Glúteos |
| 13 | Abdução de quadril na máquina em pé | Glúteos |
| 14 | Agachamento sumô com barra | Glúteos, Quadríceps |

### Panturrilhas

| # | Exercise | Groups |
|---|---|---|
| 1 | Panturrilha em pé na máquina | Panturrilhas |
| 2 | Panturrilha sentada na máquina | Panturrilhas |
| 3 | Panturrilha no leg press | Panturrilhas |
| 4 | Panturrilha em pé com halteres | Panturrilhas |
| 5 | Panturrilha no Smith | Panturrilhas |
| 6 | Panturrilha unilateral com halter | Panturrilhas |
| 7 | Panturrilha no step | Panturrilhas |
| 8 | Panturrilha burrinho | Panturrilhas |

### Abdômen

| # | Exercise | Groups |
|---|---|---|
| 1 | Abdominal supra no solo | Abdômen |
| 2 | Prancha isométrica | Abdômen, Lombar |
| 3 | Elevação de pernas na barra fixa | Abdômen |
| 4 | Abdominal na polia ajoelhado com corda | Abdômen |
| 5 | Abdominal infra no solo | Abdômen |
| 6 | Abdominal na máquina | Abdômen |
| 7 | Elevação de joelhos no paralelo | Abdômen |
| 8 | Prancha lateral | Abdômen |
| 9 | Abdominal bicicleta | Abdômen |
| 10 | Rotação russa com anilha | Abdômen |
| 11 | Elevação de pernas no banco | Abdômen |
| 12 | Abdominal oblíquo no solo | Abdômen |
| 13 | Rodinha abdominal | Abdômen, Lombar |
| 14 | Abdominal remador | Abdômen |
| 15 | Escalador | Abdômen |
| 16 | Abdominal canivete | Abdômen |
| 17 | Prancha com elevação de braço | Abdômen |

### Lombar

| # | Exercise | Groups |
|---|---|---|
| 1 | Hiperextensão lombar no banco romano | Lombar, Glúteos |
| 2 | Hiperextensão lombar na máquina | Lombar |
| 3 | Superman no solo | Lombar |
| 4 | Extensão lombar unilateral no banco | Lombar |
| 5 | Prancha reversa | Lombar, Abdômen |
| 6 | Bird dog no solo | Lombar, Abdômen |
| 7 | Ponte isométrica no banco romano | Lombar |

### Trapézio

| # | Exercise | Groups |
|---|---|---|
| 1 | Encolhimento com halteres | Trapézio |
| 2 | Encolhimento com barra | Trapézio |
| 3 | Encolhimento no Smith | Trapézio |
| 4 | Encolhimento na polia | Trapézio |
| 5 | Encolhimento com anilhas | Trapézio |
| 6 | Remada alta na polia | Trapézio, Ombros |
| 7 | Encolhimento unilateral com halter | Trapézio |

### Cardio

| # | Exercise | Groups |
|---|---|---|
| 1 | Esteira | Cardio |
| 2 | Bicicleta ergométrica | Cardio |
| 3 | Elíptico | Cardio |
| 4 | Escada simuladora | Cardio |
| 5 | Remo ergômetro | Cardio |
| 6 | Bicicleta de spinning | Cardio |
| 7 | Pular corda | Cardio |
| 8 | Caminhada inclinada na esteira | Cardio |
| 9 | Corda naval | Cardio |
| 10 | Assault bike | Cardio |
