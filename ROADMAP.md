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
- [ ] Bills, statements and receipts are still written in English (their labels live in the document builder). They move to string resources with the translations in M9, so a bill can be in the business's language.
- [ ] Every screen re-checked at large text and in the longer languages (done together with the translations).

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

## M7. Better than every competitor
Ideas we can build because we know the work. To be ordered with the product owner.
- [ ] **True cost and margin per machine-hour and per acre**, and a price advisor: "your cost here is X, you charge Y".
- [ ] **Diesel watch:** litres per hour or per acre against the machine's usual, flags a tank that drains too fast.
- [ ] **Proof of work:** optional photo of the hour meter at start and finish, and an optional GPS trail of the job. Ends disputes with farmers and with drivers.
- [ ] **Field history:** what was done on each field each season, and when it is due again.
- [ ] **Machine papers:** registration, insurance, fitness and permit expiry with reminders and a photo of each.
- [ ] **Voice and icons first:** say "rotavator, Ramesh, 3 acre" to log a job; read-aloud of key screens, for people who do not read well.
- [ ] **Customer booking link (in, recommended):** a farmer opens the owner's link, picks the work, day and place, and leaves a name and number. It lands in the owner's Plan as a *request*; nothing is booked until the owner accepts. The owner can switch the link off. Doubles as a small public page for the business ("Harpal Agro Works: machines and rates").
- [ ] **Income statement for a bank loan:** a clean season income and cost report.
- [ ] **Asset register:** purchase date and value, depreciation, resale.
- Marketplace (renting your idle machine to strangers): **out for now.** Needs supply nearby, trust, disputes and moderation. Revisit only after launch if the booking link shows demand.

---

## M8. How the app earns
The product owner wants to charge. Build it before release so early users are never surprised.
- [ ] Plans: free for one person with a few machines, paid when there are team members or many machines. Exact limits to be decided with the owner.
- [ ] Price by season or by year (farm income is seasonal), not only by month; regional prices through the store.
- [ ] Never hold data hostage: if a plan lapses the owner can still see everything, export everything, and record work; only the paid extras stop.
- [ ] Early users keep an early price ("founding users").
- [ ] Store billing, restoring purchases, and plan checks that work offline.

---

## M9. Translations (last, once the text stops changing)
- [ ] Translate every string into the first wave (Hindi, Spanish, Portuguese, French, Indonesian, Swahili, Bengali, Arabic), drafted here and marked for a native speaker to read before it ships.
- [ ] Move the bill, statement and receipt wording into string resources, so a bill is written in the business's language.
- [ ] Check every screen at large text and in each language, and the right-to-left layout in Arabic.

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
- [ ] Server hand-offs in `docs/server-setup.md`: publish the whole rules file, create `appConfig/android`, deploy `functions/` (Blaze plan), set `support_email`.
- [ ] Remove the stray `patch_*.sh` and `fix_compile.sh` scripts from the repo root.
- [ ] Final run of every flow on the physical phones (Pixel 9 Pro XL, Samsung) with the release build.
