# FieldTrack roadmap

Goal: the best app in the world for people who hire out farm machines and do contract farm work.
Specific where the khata apps are general. Global: any farmer, any country, any language.

Rules we work by (from the product owner):
- Easy and simple for every user. For every default, ask "might a user want it different?" and build the setting if yes.
- Adapt to the person: detect from the phone (language, country, time zone, region formats), confirm at onboarding only what is costly to get wrong, never ask what we can know, and let everything be changed later with the whole app following.
- Every screen x size x light/dark x state. Verify on the emulator, not only in tests.
- Platform APIs and our own logic only. No third-party code.
- Smallest change that is correct, but never leave a real case unhandled.
- **Release blockers are parked at the bottom. Nothing in them is started until every milestone above is done. At that point the product owner is reminded, and we do them together.**

Status key: [ ] not started, [~] in progress, [x] done and checked on a device.

---

## M0. Season-ready (done)
Plan and bookings, job calculator, owner/driver home, org switching, freshness line, rules gap. See git history.

---

## M1. Worldwide foundation
Done first because it touches every screen and every table.

- [x] **Adaptive onboarding: detect, confirm only what matters, change any time.** The phone gives country, language, zone, number and date style, week start and the usual currency; one "How you count" step confirms currency and land unit for every owner. Business settings (currency, rounding, accounting year, time zone, payment methods, tax) live under Profile > Money and region; person settings (language, digit grouping, week start, theme) under Display. The currency locks once an amount exists; the way out is another business (M4 adds "start another business").
- [x] **Money in minor units** (verified on both emulators over their real v16 databases: every amount exactly 100 times what it was, litres and seconds untouched, Home and the khata unchanged). Every amount is a count of the currency's smallest unit; Room 16 to 17 rebuilt the money tables, queued changes and old server documents are converted; each job keeps the rounding it was made with. Plan: `docs/m1-money-plan.md`.
- [x] **Currency per business**, symbol, decimals from the platform, whole-unit or exact bills; rupee sign for rupees and a banknote icon otherwise.
- [x] **Number grouping** (1,00,000 or 100,000), dates and clocks in the region's order, week start (phone, Monday, Sunday, Saturday).
- [x] **Business time zone** and **accounting year**; every "today", plan day, week, season, bill number and statement uses them (Room 17 to 18).
- [x] **Phone numbers worldwide.** Country-code picker on sign-in and every phone field; stored as E.164; old ten-digit numbers are read as +91 and never rewritten; one number has one spelling (Phone numbers: `PhoneNumbers`).
- [x] **Sign in with Google** as the second way in, and "add Google" on the profile so a lost phone or changed SIM can get back in. The button only appears when the project has a Google client configured: **needs the Firebase console step** (enable the Google provider, add the app's SHA-1, download the new `google-services.json`), listed with the release blockers.
- [x] **Units**: hectare, square metre, bale, tonne, bushel, kilogram, litre; walked fields in hectares.
- [x] **Languages framework**: follows the phone, chooser appears as soon as a second language is shipped, whole layout mirrors for right-to-left, directional icons mirror, per-app language in Android settings. **Translations run last** (see M9), because every later milestone adds text.
- [x] **Payment methods per business** (cash, UPI, bank, cheque, mobile money, card, in kind), usual ones by currency, cash always there; **payment details** printed on bills; **optional tax line** (name and rate, prices include it; bills say how much of the total is tax).
- [x] Bills, statements and receipts are written in the business's language (M9).
- [x] Every screen re-checked at large text and in the longer languages (M9).

## M2. Charging that matches real life (done)
- [x] Minimum charge, arrival charge, who pays for the diesel (in the rate, customer gives it, billed extra at cost), waiting time billed from the time a job sits paused, discount or round-off at settlement. One formula in Kotlin and SQL, held together by a test.
- [x] Per-day work; per bale, tonne, bushel and the other units come from M1.
- [x] Token advance at booking, on the farmer's account at once and off the bill when the work is done.
- [x] Payment in kind: a payment that records what was handed over (the amount is its value that day); crop-share charging and grain stock stay for when users ask.
- [x] Tractor plus implement: the implement gets the hours and its service comes round, the tractor earns. More people on a job see it in their own work.
- [x] Twelve more machines (excavator, seed drill, mulcher, plough, harrow, drone sprayer, water tanker, sugarcane harvester, potato digger, ridger, grain cart, pump), six more kinds of work, and the owner's own name for a machine the list lacks. They use the nearest existing icon until drawn.
- [x] The rate card remembers the terms a price comes with, and a job starts from them.

## M3. Getting paid faster (done)
- [x] Payment reminder: opens a WhatsApp chat with the customer when it can (the share sheet otherwise), says how much, how old, where to pay, with a UPI link for rupee businesses, in the customer's language once translations exist.
- [x] Payment details printed on bills and reminders; receipt for every payment (tap a payment, share as PDF); job summary to send the farmer.
- [x] Credit limit per farmer and "owed for N days" warning when booking or starting work; informs, never blocks.
- [x] "Remind me to ask for the money" on a day the owner picks, shown on the home screen while still owed.
- [x] Search across farmers, fields, machines and jobs (More > Search).
- [x] A bill is a TAX INVOICE when the business names a tax and gives its registration number; bills were already numbered by accounting year.
- [x] Driver advances: an advance is money given to a team member, taken off later pay on the pay screen, with what is still owed back shown there.
- [ ] Tap to call is on the khata; message goes through the reminder. Calling from the customer list row is not added.

## M4. Business and team (done)
- [x] Invite by one tap share (the invite code goes out through the share sheet).
- [x] Co-owner and ownership transfer (hand over, or add a second owner).
- [x] Losing a phone or changing a number: add Google on the profile and sign in with it anywhere; a person who comes back under a new number and the same name is offered a merge with the old entry.
- [x] The duplicate owner row left behind when an anonymous owner becomes a signed-in one is hidden on every phone (a membership says whom it replaces).
- [x] "Who changed what": the khata can show entries by name.
- [x] Delete my account inside the app (a request the server acts on; businesses that are yours alone go with it, and you are told which). Starting another business replaces "delete my business" as the way out of a locked currency.
- [x] Leaving a business with an advance still owed warns the leaver; the member screen shows the advance on the owner's side.
- [x] Read-only role (Viewer: accountant, family member), also enforced in the Firestore rules.
- Server side of account deletion (something has to act on `deletionRequests`) comes with the Cloud Function work in M5.

## M5. Trust and safety
- [x] Optional app lock: the phone's own screen lock (fingerprint, face, PIN or pattern) before the app opens; a setting under More > Security, with how long out of sight before it asks. Task-switcher picture hidden while on (Android 13+). A phone with no screen lock is never held shut.
- [x] Update notice, and a minimum supported version: a version document in the console (`appConfig/android`) stops too-old installs from sending (records stay on the phone) and shows an update page.
- [x] Export everything: one zip of plain CSV tables (customers, fields, machines, jobs, money, fuel, repairs, pay, team, bookings), for everything or an accounting year, limited to what the person may see. Statements stay PDF.
- [x] Backup scope decided: records, session and settings go to a new phone; everything that describes one install (device id, sync journal, pull watermarks, clock check, field walk, notification asks, push token) stays behind. A test guards the list.
- [x] Notifications: reminders for tomorrow's work, money to ask for and services due (each its own switch and hour; nothing on by default); instant push for bookings, handed jobs and payments, per-kind switches told to the server, no amounts anywhere, locked screens show only that there is something. The server code is in `functions/` and is **not deployed yet** (see `docs/server-setup.md`).
- [x] Help inside the app (answers, tour again), first-run tour, practice business with made-up work that never leaves the phone and can be removed, rating question after a fortnight and real work (a "yes" goes to the store page, a "not really" to a note to us). "Write to us" appears once a support address is set.
- [~] Accessibility pass: an audit of every screen for unlabelled and too-small controls is in the screen tests; fixes follow what it finds.

## M6. Fast on any phone, at any size
- [x] Three seasons of data (5,000 jobs, 7,500 khata lines, 300 farmers) loaded in a test: every list and total opens in tens of milliseconds, and the indexes that make it so are kept by a test (`ScaleTest`, figures in `build/scale.txt`).
- [x] Long lists: the largest list on screen is one season's finished jobs (about 1,700 rows) drawn lazily; nothing loads a whole table into a screen that does not need it. Export reads whole tables once, off the main thread.
- [x] The server pull only fetches what changed (a server-stamped mark per business, pages of 300). It now also asks one document, "has anything changed?", before running the eighteen collection queries, so an idle poll costs about one read instead of twenty-one (see `docs/m6-scale.md` for the cost figures).
- [x] Checked on a 2 GB emulator: the app holds about 170 MB with a season of data; with no network every screen works and the queue waits (checked all through M1-M5). Battery: nothing runs in the background but the six-hourly backstop, a sync per change, and the two-minute freshness poll while the screen is on and a team exists; the poll is now about a seventh of its former cost. A day on a real phone is for the final run.
- [x] Cold start measured: 4.7 to 7.5 seconds on the debug build on the emulator, almost all of it loading and checking code with no startup profile (the log shows no profile file). That is what code shrinking and a baseline profile fix, and both are in the release list; the figure is to be re-taken on the release build on a real phone.

## M7. Better than every competitor (done)
Ideas we can build because we know the work.
- [x] **True cost and margin per machine-hour and per acre**, and a price advisor ("your cost here is X, you charge Y"): already built before the roadmap (running cost per hour, cost per acre, the hint under the rate, the price calculator, the season screen); the asset register below adds what each machine is worth.
- [x] **Diesel watch:** this week's litres per hour against the four weeks before; flags a machine drinking clearly more (Home).
- [x] **Proof of work:** off until the owner switches it on (Profile). The crew can note the hour meter, photograph it and mark where the phone is, at the start and finish; it sits on the job for the owner to see (readings, a map link, the photos). Photos are shrunk, kept on the phone first and sent when there is a signal (Firebase Storage, `storage.rules`). **A GPS trail of the whole job is not built**: it needs a background location permission and drains the battery of exactly the phones in a cab all day; a start and finish mark answers "were you there". Revisit only if owners ask.
- [x] **Field history:** each field lists what was done in each season, with the last time of each kind of work. **"Due again" reminders are not built**: they need a repeat gap per kind of work and nobody has said what it should be; the last-done list is the base for it.
- [x] **Machine papers:** registration, insurance, fitness, permit and pollution with number, expiry date and a note; a warning on Home and an optional reminder, with how many days before as a setting. A photo of each paper is not on the paper itself yet (the photo store is there to do it).
- [x] **Voice and icons first:** "Say it instead" on Work done earlier takes "rotavator, Harjit, 3 acre, yesterday" and fills in the form, which the person then checks; each Home has a speaker button that reads the day's figures aloud in the phone's language.
- [x] **Customer booking link:** a public page for each business with a request form; requests land in "Farmers' requests", where whoever plans accepts (a booking is made, the farmer matched by phone or added) or declines; the owner can switch it off and choose whether rates are shown; a push tells those who plan. The page and its form run on our own server code (`functions/`, `firebase.json`, `web/`), **not deployed yet**.
- [x] **Income statement for a bank loan:** a year or a season, income and costs and the business in figures, as a PDF; only for those who see both the money and the wages.
- [x] **Asset register:** purchase date, price, expected resale and life per machine, what each is worth today, and a total.
- Marketplace (renting your idle machine to strangers): **out for now.** Needs supply nearby, trust, disputes and moderation. Revisit only after launch if the booking link shows demand.

## M8. How the app earns (built; figures to confirm, store setup is release work)
Details and the lines between free and paid: `docs/pricing.md`.
- [x] Plans: **Free** for one person with up to three machines and everything for running the work and keeping the khata; **Business** adds a team, any number of machines, the booking link and proof of work. The lines are in one file (`data/plan/Plans.kt`) for the owner to move.
- [x] Price by season or by year (a store subscription with a `season` and a `year` base plan), in the person's own currency as the store sets it per country.
- [x] Never hold data hostage: a lapsed plan stops *adding* and nothing else; every record stays, every export works, everybody keeps recording. Tested.
- [x] Founding users: an offer tagged `founding`, shown where it exists; the server keeps the flag while the plan renews.
- [x] Store billing and plan checks that work offline: Play Billing for payment; our server checks every purchase with the store and writes the entitlement; phones read it like any record, with 14 days of grace after the end date. Restoring a purchase from "I already paid".
- [ ] To do in the store before anyone pays: create the subscription, base plans and founding offers; give the server's account Play Console access; set the real application id (see the release list). Nothing could be tested against the real store from here.

## M9. Translations (drafted; native reading is the last step)
Details and what to read first: `docs/translations.md`.
- [x] Every string translated into the first wave: Hindi, Spanish, Portuguese, French, Indonesian, Swahili, Bengali, Arabic. **Drafts.** A test holds each to the English (same strings, same blanks, every plural form its language needs). Not one has been read by a native speaker yet.
- [x] Bill, statement, receipt, dues and income statement wording is in string resources (`DocLabels`), and a bill follows the business's language; right-to-left pages are laid out right to left. The amount in words stays English-only and is left off in other languages.
- [x] The language chooser lists all nine; the phone's language is followed by default; numbers use the digits 0 to 9 everywhere; a phone number stays left to right inside Arabic.
- [x] Every screen rendered in Arabic (mirrored), French (the longest Latin script) and Hindi at larger text, and looked at; the screens in the other languages are covered by the same matrix runs.
- [ ] A native speaker reads each language before it is offered to people who speak it.

## N. Field feedback round (2026-11-08): simpler, and the cases real crews hit
Source: the product owner's ten cases after showing friends a demo. Friends said "too complicated; our users are not that smart."
**Simplicity rule for every item below:** one obvious way to do each thing, plain words, short screens, the common choice already
picked, a long list becomes a dropdown, and what a role cannot do is hidden, never moved. Each item ends with the emulator check and
the eight translations. When N is done the release blockers are next; the owner wants to ship soon.

Order is chosen so each step builds on the one before (dependencies noted).

- [x] **N1. Machine screens, short and honest to the type** (case 10). The type picker (24 tiles, 8 rows) becomes one dropdown with
  the common kinds first and a search; same for `NewMachineSheet`, work types, units, billing modes, land units, payment methods,
  months, languages and machine/driver pickers once the list is long (more than 5 choices becomes a dropdown, 5 or fewer stay as chips).
  Name and plate hints, size field (HP, working width, litres, tonnes), meter (hours or km) and "needs a plate" follow the chosen kind.
  Attachments (plough, harrow, trolley...) are not asked for a plate.
  Built: `FtDropdown`/`FtPick` (5 or fewer = pills, more = list), `MachineChoice`; size/power field left as a suggestion (needs a migration).
  New English strings from N-series are translated in one wave at N10 (TranslationsTest is red until then).
- [x] **N2. One profile edit screen** (case 8). Name and business details edit on one form with one Save; money/region, seasons and
  plan stay as their own short screens because they need pickers.
- [x] **N3. Menu, profile icon, search; same places for every role** (cases 4, 5, 7). The More tab goes. Top bar on the main screens:
  profile icon (opens a side menu with profile, business switcher, settings, help) and a search icon. Same four bottom tabs and the
  same positions for everyone (Home, Work, Customers, Machines); a role only hides what it may not use, nothing moves. Search
  also finds team, payments and recent activity, and shows the last things opened.
- [x] **N4. Notifications that matter** (built; the three new server functions are not deployed or testable from here) (case 9). Breakdown tells the owner, planners and whoever is booked on that machine at once,
  high priority, with the machine and what broke. Also: handed over, job finished, booking request answered, papers/service due,
  a team member joined. An SOS button on a running job (tell the owner "need help here" with the place). A notification list
  in the app so nothing is lost when a phone was off. Every kind has a switch.
- [x] **N5. Machine changes during work** (case 6, second half). Change the machine on a running job: work charged by quantity
  or hour is closed and billed on the first machine and carried on in a new linked job on the second (so every machine keeps its
  own hours, diesel and earnings); flat-price and by-the-day work stays one job and the machine changes. The timeline says
  "work went on with B" and "carried on with A's work". When a machine goes down,
  the app lists its booked work for the next days and offers a free machine of the same kind in one tap.
- [x] **N6. Machines that belong to someone else** (case 6, first half). A machine can be: ours, a team member's, or hired in from an
  outside person. For the last two: who owns it, how they are paid (per hour, per day, per acre or a share of what the job earned),
  and the amount owed builds up by itself from the work done on it. Reports count it as hire cost, not as our own wear.
  Needs N5 (hours per machine).
- [x] **N7. Paying people, with history** (a member seeing their own pay is NOT built: see Suggestions) (case 2). Every team member gets one pay screen: what was paid, when, how, for what, notes,
  advances and what is still to recover, plus what is owed for their machine (N6). Payments can be corrected with a reversing line
  (never deleted). A member sees their own pay only. Needs N6.
- [x] **N8. One money history** (case 3). A single list of every money movement, newest first: customer payments and bills, pay-outs,
  fuel, service, job costs, hire payments; filter by type, person, machine, customer, date. Each row opens its source. Shown by role:
  customer money needs See money, wages need See people cost. Needs N6 and N7.
- [x] **N9. Your own roles** (case 1). The owner makes a named role (Manager, Accountant, Mechanic...) from a list of plain-word
  switches, keeps it, and gives it to any member; changing the role changes everyone who has it. The five built-in roles stay.
- [x] **N10. Simplify pass and sign-off.** Walk every main flow as a first-time driver and a first-time owner, cut steps and words,
  full test suite, emulator check at both sizes, light and dark, and the eight translations read again for the new words.
  Done: the pay form asks "who" in one dropdown; the new screens are in the screen matrix (all nine configurations green);
  full suite 1,137 tests green after one fix (permission rows are named toggles); translations drafted, unread.

## P. Arrange the app around the person (2026-11-08) - built; waiting for the owner's check
Why: N put every feature somewhere, but arranged them by what was built, not by what a person is trying to do. The owner's verdict
after trying it: more options in the drawer, not better arranged; no easy business switch; no real Settings screen; profile still
has the old flow; money visibility unclear; team screens thin and unorganised. Seen on the emulator: the drawer is the old More list
(about 18 rows, Machines and Customers repeated from the tabs, Language and Display opening the same screen, icons that mean
nothing); Profile has an "Edit your details" button AND every field is also a row that opens its own editor, and holds preferences,
the plan, business mode and sign-out; the team list shows a name and "Free"; a member page is a settings form.

Rule: arrange by the question a person asks. Home = what is happening today. Jobs = the work. Customers = who I work for. Machines =
what I work with. Team = who works for me. Money = where the money is. Settings = me and the app. A thing lives in ONE place.
Two more rules from the owner (2026-11-08): **nothing looks tappable unless it is** (an arrow means "opens something";
a fact is shown as plain text, a setting changes in place) and **search happens in the top bar itself**, full width over the
screen, like other apps; long lists get their own search box at the top.
Test: a list of 15 everyday tasks (docs/ux-tasks.md), each reachable from Home in 3 taps or fewer, measured on the emulator before and
after, for an owner and for a driver. A step is not done until its tasks pass.

- [x] **P1. A short menu with a business switch.** Header = you + the business you are in; one tap opens a sheet of all your businesses
  (tick the current one, "join another", "start my own"). Rows, only those the role may use, never moved: Team, Money, Farmers'
  requests (only when the booking link is on), Settings, Help. Machines and Customers leave the menu (they are tabs). Backup status
  leaves (Home already shows it; full detail goes in Settings). Every row gets an icon that means what it says.
  Built: menu = you (profile) + the business you are in with a Change pill (sheet of businesses, join, start one) + Team, Money,
  Farmers' requests (only when the link is on or some wait) + Settings, Help. New icons: settings, help, language, display, export.
  Search opens in the bar (the separate search screen is gone; the box keeps every key, it no longer waits on the results).
  The arrow is drawn only on rows that open something, in the shared row itself, so no screen can get it wrong.
- [x] **P2. Settings, one screen for preferences.** Grouped by plain headings, with the common ones changed in place (language,
  light/dark, text size, phone lock as switches or a dropdown, no deeper screen): Notifications, Business (owner: money and region,
  land unit, seasons, day start, drivers see own work, business mode, proof of work, booking link), Plan and billing, Data (backup,
  export), Account (signed in as, sign out, delete). Nothing about preferences stays in Profile or the menu.
  Built: language (dropdown), colours (pills), bigger text and phone lock (switches) change in place; the old Security screen
  is gone; Display keeps only digit grouping and week start ("Numbers and weeks"); land unit moved here from the profile form.
- [x] **P3. Profile is the form.** Name, phone (fixed), role, and for an owner the business details, all editable on the page with
  one Save that appears once something changes. No per-field rows, no separate "edit" screen.
  Built: leaving with unsaved changes asks (save, or leave without saving); "Saved." confirms; the old edit route is gone.
- [x] **P4. Money, one place.** A hub: top figures (to collect, paid out this month, left this season, each only if the role may see it),
  two big buttons (Money received, Pay someone), then one list of everything with In / Out and a period, then Reports (season,
  income statement, machine values, rates and the price sum). Money history, Money out and the pay form become parts of it, not
  separate doors. Visibility by kind of transaction, set per role in the role editor under "Money they see": customer money,
  wages and machine hire, diesel and repairs; the list says plainly what is hidden for the role.
  Built: the menu's Money opens one page: in and out for the period, what farmers still owe, Money received (pick the
  farmer, then the payment form) and Pay someone, People and hire, Reports and prices (season, income statement, machine
  values, rates, the price sum), then every movement with only the kinds this person may see offered as filters and a line
  saying what is kept back. A payment out says what it was for, and unnamed ones read "Labourers".
  Decided: no separate diesel switch. The rules let every member read diesel and repairs, so a switch would hide nothing
  on the server and needs a migration plus a rules change; diesel and repairs show to anybody who sees either kind of
  money. Job costs are not listed: when labour is paid it is a payment out already, listing the cost too would count it twice.
- [x] **P5. Team, organised.** List: each person a card with role, what they are doing today, their machine, what you owe them (if you may
  see it) and a call button; invited people appear in the list as waiting; one Invite button; Roles and Team changes under "Manage".
  Member page in three parts: Work (today, this season, recent jobs), Money (owed, paid, advances, machine hire, Pay; the current
  separate pay screen merges in), Access (role as one dropdown with its sentence, fine-tune switches folded away, name and phone,
  remove from team at the very bottom).
  Built: one "Add a person" asks "will they use the app?" (send a code / just write them down); each card says their job
  (built-in, the owner's own, or "set by hand"), what they are doing in words, any advance to take back or machine hire
  owed, and has a call button; Roles and Team changes sit under Manage; the one-owner warning says what tapping it does.
  Member page: who they are, a call button, then Work (figures, latest work) / Money (the pay page, moved in; each payment
  has a visible "Take this back") / What they can do (role, fine-tune, details, remove at the foot). Save only after a change.
  Invited people still show as a "codes waiting" line rather than as people (an invite has no name until it is used).
- [x] **P6. Walk Home, Jobs, Customers, Machines, machine detail and job detail** as a first-time owner and driver with screenshots;
  list what is out of place (not audited yet) and report it before changing anything big.
  Done (small, safe changes): Fleet lists the machines itself (it used to need "See all machines"), with "+" to add one and
  a search box past eight machines (the Machines list too); Jobs got "+" to start work, an "All" filter, a search box on
  "This week" and "All", and the date on past jobs; cards that open something now carry the arrow (Home's owed/season
  figures, a machine's service, breakdown and hire cards, a field's map card, field cards) through one switch on the
  shared card. Customers already had its search box and "+".
  Reported, not changed (bigger): the owner's Home puts "Start work" below the fold under the figures; the person who
  drives too may want it first. Machine detail and job detail are long but in order; left as they are.
- [x] **P7. Words, icons, translations, sign-off.** New strings in the eight languages, screen matrix and accessibility audit, full
  suite, the 15 tasks measured again, owner's check.
  Done: 61 new strings drafted in the eight languages (strings_11, Indonesian strings_12; unread, notes in
  docs/translations.md); full suite 1,147 tests green including every screen in all nine configurations; the tasks are
  counted in docs/ux-tasks.md (most in three taps or fewer; the four that take more are named with the reason).
  Waiting on: the owner trying it.

Needs the owner's yes before P1 (defaults in brackets): "team switch" means switching between businesses [yes]; Money is a menu row, the
four tabs stay [yes]; Profile is the form itself [yes]; diesel and repairs get their own visibility switch [yes, but if it needs a
rules change I will say so first].

## Q. Trust and money that moves by itself (2026-11-08) - done
Why: the app is full of trust and money; if every rupee is typed by hand it stays a data-entry app. The owner asked for the
details we should hold on people and the business, the owner's power to check and change them, and money handled in the app.
Decisions from the owner: add this before the release blockers; a driving licence is optional.

- [x] **Q1. Who did what with the money.** Every line of the money page says who recorded it; a payment taken back says who
  took it back and when; a correction says what it corrects; typing a person's name finds everything they recorded.
- [x] **Q2. Cash in hand.** Cash a team member takes from a farmer is "held by them" until the owner records that it was
  handed over. Shown on the team card, the member page, the money page, and to the person holding it.
- [x] **Q3. A team member sees their own pay** (rules change: a payout readable by the person it was paid to; a second
  restore query for them). From the menu: "My pay".
- [x] **Q4. Member details.** How to pay them (UPI id or bank), how they are paid and at what rate (per day, per acre, per
  hour, monthly) with what that adds up to from their work, driving licence number and expiry (optional), an emergency
  contact. Personal details in a record the owner, the team managers and the person can read; pay terms where only the
  money people and the person can read. Every change is in Team changes and shown to the person.
  Built: member page parts are Work / Money / Details. Details: name, phone, licence and its end date, emergency contact
  (fields for whoever runs the team, plain facts for anybody else). Money: how they are paid (by the day, hour, land or
  month) with earned / paid / still to pay worked out from the jobs they drove, and where their pay goes (UPI, bank).
  A team member keeps their own particulars on Profile ("For your pay and your safety") and is told when somebody else
  changed them; the change is in Team changes (the log never carries the rate). A licence run out, or running out within
  30 days, shows on the team card. "My pay" in the menu for a member who may not see everybody's pay.
- [x] **Q5. UPI, India first.** Pay a person: opens the UPI app with their id and the amount filled in, and records the
  payment when the UPI app says it went through (asks when it cannot tell). Get paid: a QR code with the amount on the
  farmer's page and on the bill, drawn by our own code, and one tap to record it once the money is seen.
  Other countries keep "tap Paid" until their own route (Pix, M-Pesa) is done.
  Built: the QR shows on the payment form when UPI is chosen, and on the bill and the dues list (in place of the box that
  only looked like one); a reminder's link writes the id the same way. Not yet proven by a real UPI app scanning it.
- [x] **Q6. Close-out.** The whole rules file for the console, translations, screen matrix, full suite, emulator check.
  Found on the emulator and fixed: the pay sheets hid Save behind the keyboard; pay terms now say the day the pay was
  counted from (it can start before the day it is written down); "Look first" on a bill or statement failed for anybody
  who had chosen a language in the app (the print service refused the language's context).

Later, decided after the closed test: payment links through a payment company from our server (bills mark themselves
paid; each owner does that company's KYC; fees per payment). Not planned: holding or paying out money on owners' behalf.

Suggestions kept for later (not built unless asked):
- A lender's share of a hired machine's earnings across several jobs as a statement; sharing a machine's pay slip on
  WhatsApp. (A team member's own pay and a wage rate per person were built in Q3 and Q4; the own-pay query asks only for
  `toPersonId`, so it needs no extra index.)

## R. Work that follows the machine, and every "Other" nameable (2026-11-09)
Why: the owner saw Start work and Add past work list all fourteen kinds of work whatever the machine, the implement asked
last and folded under "terms", and "Other" printed as "Other" on the bill. Real use: a tractor's work is whatever is on the
back; a rotavator can go out alone behind a farmer's own tractor; one machine is charged several ways; two combines are two
prices; and the world has work, papers, payment ways and land units this app has never heard of. Owner's rule: "Other" by
default, and the person may name it whatever they like; same everywhere an "Other" exists. Fix it final, screens and data.

- [x] **R1. The machine decides which work comes first.** Its own history first, then what that kind of machine is for (a
  table: tractor by what is attached, rotavator, harvester, tanker, JCB...), the rest one tap away under "More work". With
  no machine, the business's usual work. The work is charged the way it was last time (mode and unit), not a fixed default.
- [x] **R2. Tractor and implement asked where the machine is.** A tractor: "What is attached?" (the rotavator filled in when
  there is one). A tool picked on its own: "Pulled by?" (the one tractor filled in, "Not one of ours" for a farmer's own
  tractor or a self-driven one). Saved as tractor + implement either way. Start work and Add past work; the old question
  under "terms" goes.
- [x] **R3. Missing work:** puddling, ridging, loading, water supply, machine hire (by the day or hour). Server's list too.
- [x] **R4. Work names.** "Other" by default; a box to name it, empty keeps "Other". A typed name that matches one of ours
  becomes ours. The business's own names are offered next time everywhere work is picked (start, past, booking, rate card,
  price calculator, the public booking page). Fixed on the way: a booking with an own name was saved back as "Other", a
  job started from one took the default work, the rate card hid own-named rates, and many screens, the bill, the clock
  notification, statements and the advance note printed the English key instead of the translated name.
- [x] **R5. The machine's own rate first:** the rate last charged on this machine (this customer's first) leads the chips.
- [x] **R6. Every other "Other" nameable and shown:** machine type in the quick-add sheet and onboarding; a paper's own name;
  a breakdown's kind translated in the job's history with its note; a cancelled booking's own reason; each "other"
  spending's note on the job page; a payment way of their own ("Other" + name).
  Built for R1-R6, and found on the emulator: an implement filled in by itself must not reorder the list (the card
  just tapped jumped away), so only one the person picks does; the implement now shows as out working while its tractor
  is, and the job reads "tractor + rotavator"; a fixed-price bill no longer says "0 Piece"; the pay-out form now asks how
  it was paid (it always saved cash); "Other" is always offered as a way of paying, last, like cash is first.
- [ ] **R7. Land units:** every land unit offered when the business is set up and in Settings (Katha, square metre were
  missing), the common units of other countries added, and a business's own unit with a name and its size.
- [ ] **R8. Close-out:** translations, screen matrix, full suite, emulator check of each screen touched in light and dark.

---

## Decisions
Decided:
1. Money in minor units: yes.
2. Languages: follow the phone's language, no single first language, translations in waves.
4. Payment in kind: simple version now (M2), crop-share charging later.
6. Earn money: yes (M8).

3. Google sign-in as the second way in (agreed).
5. Instant push to drivers (agreed).
7. Booking link in (M7); marketplace out for now (agreed).

---

## RELEASE BLOCKERS (parked, do NOT start until M1-M7 are done)
**Reminder to the product owner is due the moment the last milestone above is finished.**

- [ ] Real application id (it is `com.example.fieldtrack`; it cannot change after publishing).
- [ ] Release signing key, store app signing, key backed up in two places.
- [ ] Code shrinking on, and the release build tested end to end on real phones (apps often work in debug and crash in release). Startup profile for speed.
- [ ] Separate Firebase project for release, apart from the one holding test numbers and test data. Re-publish the whole rules file there.
- [ ] App Check so only the real app can reach the data.
- [ ] SMS sign-in: allowed countries, spending cap, budget alerts on the project.
- [ ] Scheduled backups of the database.
- [ ] Cost projection for the server (reads, writes, SMS) at 1,000 and 10,000 users.
- [ ] Store paperwork: privacy policy page, data safety form, account deletion page on the web, location permission wording, content rating, store listing in each language.
- [ ] Closed test with real contractors (if the developer account is a personal one, the store asks for about 12 testers for 14 days before production).
- [ ] Version numbering scheme, build as an app bundle.
- [ ] Crash and ANR monitoring through the store's own reports.
- [ ] Store setup for paying (`docs/pricing.md`): the subscription, its base plans and founding offers, the Play Developer API, the functions' service account, `PLAY_PACKAGE`.
- [ ] Server hand-offs in `docs/server-setup.md`: publish the whole rules file, create `appConfig/android`, deploy `functions/` (Blaze plan), set `support_email`.
- [ ] Remove the stray `patch_*.sh` and `fix_compile.sh` scripts from the repo root.
- [ ] Final run of every flow on the physical phones (Pixel 9 Pro XL, Samsung) with the release build.
