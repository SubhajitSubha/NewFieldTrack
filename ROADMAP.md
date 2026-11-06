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
Do this first. It touches every screen and every table, it is cheap while there are no real users, and it is very expensive after release.

- [ ] **Adaptive onboarding: detect, confirm only what matters, change any time.** The phone tells us language, country, time zone, number and date style, first day of the week, and the usual currency. One short "we set you up like this, change anything?" screen confirms only currency and land unit (a wrong currency or a wrong bigha is costly). Everything else is silent and lives in Profile / Business settings. Two levels: *business* settings (currency, time zone, land unit, tax, payment methods) belong to the business and a joining driver adopts them; *person* settings (language, number style, theme) belong to the person and device. Changing the currency once there is history never converts silently (500 rupees is not 500 dollars): lock it after the first entry, with a clear path to convert at a stated rate or start a new business.
- [~] **Money in minor units (decided: yes). Study and plan done: see `docs/m1-money-plan.md`; build next.** Today every amount is a whole-rupee Long (`amountRupees`, `rateRupees`, `costRupees`, ...) in Room, Firestore and rules. Dollars, euros and pesos have cents. Move to minor units with a per-currency number of decimals. Needs a Room migration, a Firestore data move, new rules checks, and a rounding rule that is a setting.
- [ ] **Currency per business** (symbol, decimals, where the symbol sits). Replace the hard-coded rupee sign (about 20 files plus the statement documents).
- [ ] **Number grouping setting** (1,00,000 versus 100,000) and date/time format and week start.
- [ ] **Business time zone.** Reverses an earlier decision (see memory): "no time zone" was only safe while everyone was in India. A business sets its zone, and the plan day, day roll hour and "today" all follow it.
- [ ] **Phone numbers worldwide.** Country picker instead of the fixed +91 (sign-in, add member, add customer, edit profile), E.164 storage, and a migration of existing numbers.
- [ ] **Sign in with Google as a second way in (recommended, pending OK).** Phone stays. Both buttons show, ordered by country. After a phone sign-in the app offers "add Google so you can always get back in", which also solves a lost phone or changed SIM (M4). Phone number becomes optional contact detail, not the identity. Invites stay code-based, so a Google-only driver can still join.
- [ ] **Land and output units.** Add hectare, square metre, and counting units such as bale, tonne, bushel, kilogram, litre. Each business picks defaults. Keep the regional units (bigha, kanal, guntha, katha).
- [ ] **Languages framework.** All text already lives in resources. Add language choice inside the app, right-to-left layout (Arabic, Urdu), fonts that cover each script, plural and number rules. The app follows the phone's language by itself, falls back to English, and can be changed in Profile. Translations in waves, so there is no fixed "first language" (proposed wave 1: Hindi, Spanish, Portuguese, French, Indonesian, Swahili, Bengali, Arabic to prove right-to-left; a native speaker reviews each before it ships).
- [ ] **Payment methods per region.** Cash, bank, cheque stay; UPI becomes one of several. Owner can add their own ("M-Pesa", "Pix", "Zelle") as a name plus the details to show on statements.
- [ ] **Optional tax line** (GST, VAT, sales tax, or none) with a name and a percent per business. Off by default.
- [ ] Every screen re-checked at large text and in the longer languages.

## M2. Charging that matches real life
- [ ] Minimum charge (minimum hours, acres or amount).
- [ ] Fuel: "diesel included" versus "customer supplies diesel", and billing fuel to the customer when it is ours.
- [ ] Arrival / transport charge to reach a far field.
- [ ] Token advance taken at booking time, carried onto the job.
- [ ] Discount or round-off at settlement.
- [ ] Per-day and flat season contract charging.
- [ ] Per bale, per tonne, per bushel (follows from the new units in M1).
- [ ] Waiting or idle time: billed or not, per job.
- [ ] Payment in kind, simple version now: a payment whose method is "In kind" with what was given, the quantity, and the value the owner types on the day. It reuses the existing payment and khata, so it adds little. The bigger version (charging as a share of the crop, tracking grain stock) waits until real users ask.
- [ ] Tractor plus implement as one job: which machine earns, which one wears.
- [ ] More than one operator on a job (harvester plus driver plus helper).
- [ ] **Machine types the owner can create** (name, default charging mode and unit, picture) with a starter catalogue by region. Catalogue to add: excavator/JCB, seed drill / Happy Seeder / planter, mulcher / straw reaper, plough, harrow, drone sprayer, water tanker / pump, sugarcane and forage harvesters, potato digger, ridger, grain cart.
- [ ] Rate card learns from all of these.

## M3. Getting paid faster
- [ ] Share a payment reminder (WhatsApp or any app) with the balance owed, in the customer's language.
- [ ] Payment details on statements and reminders: the owner's own UPI id / bank / mobile money text, plus a real UPI pay link where it exists.
- [ ] Receipt for every payment, shareable as an image or PDF.
- [ ] Job summary card to share with the farmer (area, hours, rate, amount) so disputes end early.
- [ ] Credit limit and "owes for N days" warning when booking or starting work for a customer.
- [ ] Reminder to chase a customer on a date the owner picks.
- [ ] Tap to call or message a customer; search across customers, fields, jobs and machines.
- [ ] Optional invoice numbering and tax invoice for those who need them.
- [ ] Driver advances and deductions: a driver's own khata.

## M4. Business and team
- [ ] Invite by one tap share (message with the code and the app link), not just reading a code aloud.
- [ ] Co-owner and ownership transfer.
- [ ] Changing a phone number or losing a phone: a safe way to attach the same person to a new number.
- [ ] Fix the duplicate owner row left behind when an anonymous owner becomes a signed-in one.
- [ ] Short "who changed what" history on money entries.
- [ ] Delete my account and delete my business inside the app (the feature; the store paperwork is a blocker).
- [ ] Leaving a business with money still owed: warn both sides.
- [ ] Read-only role (accountant, family member).

## M5. Trust and safety
- [ ] Optional app lock (fingerprint or PIN), a setting.
- [ ] Update prompt, and a minimum supported version so old installs cannot corrupt shared data.
- [ ] Export everything (CSV and PDF) for the owner: portability, bank proof of income, accountant.
- [ ] Decide what Android automatic backup may copy (the local database, the sync queue, the session) so a restored phone cannot misbehave.
- [ ] Notifications: local reminders for tomorrow's plan, dues, services due. **Instant push (recommended, pending OK):** a small piece of our own code on Google's servers sends a notification when a booking for you is made, changed or cancelled, when a job is handed to you, and when a payment is recorded. Per-person switch for each kind, and no money amounts on the lock screen.
- [ ] Help inside the app, contact support, "try with sample data", a short first-run tour, rating prompt.
- [ ] Accessibility pass: screen reader labels, large text, colour contrast, one-handed reach.

## M6. Fast on any phone, at any size
- [ ] Load three seasons of data (about 5,000 jobs) and fix whatever is slow: Home, Season, lists.
- [ ] Paging for long lists, indexes on hot queries.
- [ ] Confirm the server pull only fetches what changed. Project reads per user per day.
- [ ] Test on a 2-3 GB phone, a slow network, no network, and battery use over a working day.
- [ ] Cold start time measured and kept down.

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
- [ ] Remove the stray `patch_*.sh` and `fix_compile.sh` scripts from the repo root.
- [ ] Final run of every flow on the physical phones (Pixel 9 Pro XL, Samsung) with the release build.
