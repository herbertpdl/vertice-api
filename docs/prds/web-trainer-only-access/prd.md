# PRD: Web Access for Trainers Only

Status: Draft
Design: design-ok — approved by the owner on 2026-10-24 after reviewing the pen.dev section "Web — Acesso só para treinadores" (`kfvY1`).
Owner: hebertpdl@gmail.com
Related: `docs/requirements.md` (source requirements: "The goal of the product is for personal
trainers to create and manage training plans for their costumers/clients", "the clients should be
able to access the app later on", and "They cannot change enything on the training plan, only the
personal trainer can do it"), `docs/specs/user-unification/spec.md` (introduced the trainer /
client / admin roles and left using them to grant access for a later feature, which this is),
`vertice-web-react/docs/prds/client-workout-trainer-side/prd.md` (states the client has no web
screens), `vertice-mobile/docs/prds/client-workout-experience/prd.md` (where clients train)
Spec: not yet written (will be `docs/specs/web-trainer-only-access/spec.md`, with web and BFF
counterparts under the same slug)

## 1. Summary

The web app is the trainer's workspace. Clients use the mobile app. Today a client can log in on
the web and is shown the full trainer workspace, with every screen and button for creating plans,
workouts and exercises. This feature shuts clients out of the web, tells them to use the app, and
spells out what each role is allowed to do. That list becomes a guarantee: whatever app a client
uses, they can never perform a trainer action.

## 2. Actors

- **Trainer**
  - Can log in on the web and use everything the web offers today. Nothing changes for them.
  - Can, from any app: see and add their clients; see the dashboard and their clients' feedback;
    create, change, copy and delete training plans, workouts, the exercises in a workout and
    their sets; create, change and delete their own exercises; see a client's exercise progress;
    edit their profile and the contacts they share with clients.
  - Cannot log a workout as a client, record weights for a client, or submit feedback.
- **Client**
  - Cannot log in on the web. They are told that client access is only through the app.
  - Cannot see any trainer screen on the web, not even briefly.
  - Can still set their password through the link their trainer sends, which opens on the web.
  - Can, from the app: see their own training plans, workouts and exercises; start, log and
    finish a workout with the weights they used; submit feedback; see their own progress and
    their trainer's shared contacts; edit their profile; delete their account.
  - Cannot, from any app: create, change, copy or delete a training plan, workout, exercise or
    set; see the list of clients, the trainer dashboard or the trainer's feedback list; see
    another client's anything.
- **Admin**
  - Cannot log in on this web. Admins will have their own application, separate from the
    trainer and client apps.

## 3. Flows

**Client tries the web**
1. The client opens the web login and enters their correct e-mail and password.
2. The web refuses the login. The client stays on the login page and is not signed in.
3. The login page shows the message "O acesso de alunos é feito somente pelo aplicativo
   Vertice."

**Client sets their password**
1. The client opens the "Definir senha" link from their trainer.
2. They choose a password and confirm it.
3. The success screen tells them to sign in on the Vertice app instead of offering to go to the
   web login.
   - On a phone: an "Abrir o app" button that opens the app's login with their e-mail already
     filled in, and App Store and Google Play links.
   - On a computer: a QR code to scan with the phone instead.

**Trainer logs in**
1. Unchanged.

## 4. Rules

### Web login
- R1. Only a trainer can sign in on the web.
- R2. When a client enters a correct e-mail and password on the web login, the login is refused
  and they are not signed in.
- R3. A refused client sees the message "O acesso de alunos é feito somente pelo aplicativo
  Vertice." on the login page.
- R4. When a client or an admin enters a wrong password, they see the same "invalid e-mail or password"
  message as anyone else, not the "use the app" message.
- R5. An admin who enters a correct e-mail and password on the web login is refused, is not
  signed in, and sees the same message as a client (R3).

### Web workspace
- R6. A client never sees any web page or action behind the login, even if they open its
  address directly.
- R7. Anyone other than a trainer who is signed in on the web is signed out at their next page
  load or action. No notice or transition period is needed.
- R8. A trainer's web experience is the same as before this change.

### Password setup
- R9. A client can still set their password through the "Definir senha" link on the web.
- R10. After a client sets their password, the success screen tells them to sign in on the app
  and does not offer the web login.
- R10a. On a phone, the client's success screen has an "Abrir o app" button that opens the Vertice app's
  login with the client's e-mail already filled in.
- R10b. On a phone, the client's success screen also shows App Store and Google Play links, for a
  client without the app installed.
- R10c. On a computer, the client's success screen shows a QR code to scan with the phone,
  instead of the "Abrir o app" button and the store links.
- R10d. The QR code leads to one link that sends an iPhone to the App Store and an Android phone
  to Google Play.
- R10e. Until the app is published, the store links and the QR code's link are placeholders.
- R11. After a trainer sets their password, the success screen still offers the web login.

### Server guarantee (any app)
- R12. A client who asks to create, change, copy or delete a training plan, workout, workout
  exercise, set or exercise is refused, whichever app it comes from.
- R13. A client who asks for the list of clients, the trainer dashboard or the trainer's
  feedback list is refused, whichever app it comes from.
- R14. A client who asks to add a client is refused, whichever app it comes from.
- R15. A trainer who asks to start, log or finish a workout as a client, or to submit feedback,
  is refused, whichever app it comes from.
- R16. A refused action changes nothing.
- R17. Every trainer action listed in §2 is checked against a client account before this
  feature is considered done, and each check confirms R12–R16.

## 5. Edge cases

| # | Scenario | Expected outcome | Rule |
|---|---|---|---|
| E1 | A client enters a correct password on the web login | Refused, not signed in, "use the app" message shown | R2, R3 |
| E2 | A client enters a wrong password on the web login | Usual "invalid e-mail or password" message; the account's role is not revealed | R4 |
| E3 | A client opens a trainer page address on the web while signed out (bookmark, shared link) | Taken to the login; logging in is then refused as in E1 | R6, R2 |
| E4 | A non-trainer is somehow signed in on the web | Signed out at the next page load or action | R7 |
| E5 | A client attempts a trainer action from outside the web (another tool, a modified app) | Refused; nothing changes | R12–R16 |
| E6 | A client opens the "Definir senha" link | Can set the password; success screen points to the app with an "Abrir o app" button | R9, R10, R10a |
| E7 | A trainer opens the "Definir senha" link | Unchanged; success screen offers the web login | R11 |
| E8 | An admin logs in on the web | Refused, not signed in, same message as a client | R5 |
| E11 | A client on a phone taps "Abrir o app" without the app installed | The app does not open; the App Store and Google Play links on the same screen are the way forward | R10a, R10b |
| E14 | A client sets their password on a computer | The success screen shows the QR code, not the button or the store links | R10c |
| E15 | A client opens the success screen before the app is published | Placeholder links and QR destination | R10e |
| E16 | A client scans the QR code with an iPhone, or with an Android phone | The phone opens the matching store's page for the Vertice app | R10d |
| E12 | A client taps "Abrir o app" with the app installed | The app opens on its login with their e-mail filled in | R10a |
| E13 | An admin enters a wrong password on the web login | Usual "invalid e-mail or password" message | R4, R5 |
| E9 | Someone with a client's e-mail tries to create an account on the web | Nothing special: refused as an e-mail already registered, as today | none (unchanged) |
| E10 | A trainer logs in on the web | Unchanged | R8 |

## 6. Out of scope

- Admin screens or an admin workspace on this web. Admins will have their own separate
  application.
- Moving client password setup into the mobile app. A bigger change, and a separate feature if
  wanted.
- Any other change to the mobile app beyond opening its login, with the e-mail filled in, from
  the "Abrir o app" button. Clients
  already train there; trainers using the app keep what they have.

## 7. Decisions

| Question | Decision | Why |
|---|---|---|
| Can an admin log in on the web? | No | Admins will have their own application, separate from this one |
| What happens to a non-trainer already signed in on the web? | Signed out at the next page load or action, with no notice or transition | The product is not in production yet, so no real user is affected |
| Where does a client go after setting their password? | The success screen sends them to the app with an "Abrir o app" button | The web login would only refuse them |
| Which message does a refused admin see? | The same message as a client | Owner's choice; one message to keep for every refused login |
| What if the app isn't installed, or the client is on a computer? | App Store and Google Play links on the success screen (proposed, accepted) | The "Abrir o app" button alone would leave them stuck |
| What does a client on a computer see? | A QR code instead of the button and the store links | Owner's choice: a deep link or store link is useless on a computer |
| Where does the QR code lead? | One link that picks the store by phone type | Owner's choice: one code works for every phone |
| Store links before the app is published | Placeholders | Owner's choice: the app is not published yet |
| Where does "Abrir o app" land? | The app's login with the client's e-mail filled in (proposed, accepted) | They only need to type the password they just chose |
| Did the reported client test save data? | No; the client account only saw the trainer screens | The server already refused the actions; R12–R17 make that a verified guarantee |
| A client enters a wrong password | The usual "invalid e-mail or password" message (proposed, accepted) | The web never reveals that an e-mail belongs to a client unless the password is correct |
| Wording of the refusal message | "O acesso de alunos é feito somente pelo aplicativo Vertice." (proposed, accepted) | Matches the "aluno" wording the web already uses for clients |

## 8. Open questions

None.
