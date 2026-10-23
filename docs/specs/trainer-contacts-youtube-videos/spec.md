# Spec: Trainer contacts for clients, and YouTube-only exercise videos

Status: Implemented
Owner: hebertpdl@gmail.com
Related: vertice-mobile `docs/prds/client-workout-experience/prd.md` (R61, R62, R74–R79) and
`docs/specs/client-workout-experience/spec.md` §2.2 items A5–A7

## 0. Scope decisions

- **Contacts live on the trainer's user row** (V33): `whatsapp` (10–11 digits, area code and
  number), `instagram` (the handle without "@", at most 30 characters), and `show_email`,
  `show_whatsapp`, `show_instagram`. Every switch defaults to `false`: contacts stay hidden until
  the trainer switches them on (the owner's decision).
- **Set through `UpdateUser`, as `optional` fields.** A field left out keeps its value, so a
  caller that doesn't know them (today's vertice-bff) never clears them. Only a TRAINER can set
  them (INVALID_ARGUMENT otherwise). They are returned in the detail and self views.
- **`TrainerClientService.GetClientTrainer`.** It answers a client's active trainer (V21's
  one-active-relationship index): the name always, plus each contact that is both switched on
  and filled in.
  - Callers: the client as themself, or ADMIN. Never a trainer.
  - No active trainer, or a deleted one, is NOT_FOUND.
- **Exercise videos are YouTube only.** Create and update accept a watch, shorts, embed or live
  link, or youtu.be (with `www.`, `m.` and youtube-nocookie.com), carrying an 11-character id: the
  links vertice-mobile's `youtubeVideoId` reads. Existing non-YouTube rows stay until edited; the
  client app hides them (R62).
- **LGPD.** Account deletion clears the contacts and switches. The export's profile carries them.
  `docs/data-inventory.md` lists them.

## 1. Tests

- `TrainerContactsIntegrationTest` covers:
  - visibility: only shown and filled contacts;
  - hidden by default;
  - an update without the fields keeps them;
  - access: the client only, PERMISSION_DENIED for a trainer, NOT_FOUND after the relationship
    ends;
  - trainers only, and validation.
- `ExerciseControllerTest`: accepted YouTube forms, and refused Vimeo, Drive, short ids and
  look-alike hosts.
