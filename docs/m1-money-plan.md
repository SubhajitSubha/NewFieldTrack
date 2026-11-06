# M1.1 Money in minor units: study and plan

Status: study finished, nothing changed in the app yet. Written 2026-11-07.

## 1. What the study found

Money is a whole-rupee `Long` end to end. It is not hidden anywhere I did not find.

| Where | Finding |
|---|---|
| Stored columns (Room, DB v16) | 9 columns in 8 tables: `fuel_entries.costRupees`, `service_records.costRupees`, `machine_finance.monthlyRupees`, `payouts.amountRupees`, `job_costs.amountRupees`, `rates.amountRupees`, `jobs.rateRupees`, `jobs.tripTotalRupees`, `ledger_entries.amountRupees`. |
| Hidden money in a Double | `job_events.numericValue` holds **money** for `TripAdded`, `TripRemoved` and `RateChanged` events (and litres/seconds/quantity for other events). Easy to miss: only those three types convert. `JobDao.tripEvents` reads it back by SQL. |
| Server documents | Rows are `@Serializable` entities written to Firestore with the Kotlin property name as the field name, so the field names `amountRupees` etc. are in every server document. |
| Server rules | `firestore.rules` never mentions an amount field (only comments). The rename needs **no rules change** for amounts. The new org currency field needs a check that the org rules allow it (to confirm in the build; if a change is needed it is handed over as the whole file). |
| Queued writes | `outbox.payloadJson` holds pending documents with the old field names. The migration must rewrite them, or an old payload will push old names to the server. |
| Calculation | `Billing.amount` rounds `quantity x rate` to a whole rupee. The same rounding is repeated in 5 SQL queries (`CAST(ROUND(quantity * rateRupees) AS INTEGER)` in `Daos.kt`), so Kotlin and SQL must change together. |
| Formatting | `Money.kt` hard-codes the rupee sign and Indian 3-2-2 grouping, plus `L`/`Cr` shorthand. `AmountInWords.kt` writes amounts in Indian words on bills. |
| Size | 285+ uses of `amountRupees`, about 60 other `...Rupees` names, 82 main files, 33 test files, 127 call sites of `rupees()/rupeesPlain()/rupeesCompact()` in 35 files, a rupee sign in 34 Kotlin files (mostly comments and doc strings). |
| Tests | One migration test file exists (`MigrationTest.kt`); exported schemas run to v16. |

Also touching the same assumptions (belong to later M1 steps, listed so none is lost): `+91` in 6 screens; day logic in `PlanDay`, `SeasonCalendar`, `BillNumber`, `HomeViewModel`, `YardHomeViewModel`, `SeasonViewModel`, `StatementOptions` using the device zone (time zone step); 20 spots using `Locale/NumberFormat/String.format` (formats step).

## 2. Design

**Stored value.** Every amount is a `Long` count of the currency's *minor unit* (paise, cents). The number of decimals comes from the platform (`java.util.Currency.getDefaultFractionDigits()`), so yen and shillings (0), rupees and dollars (2) and dinars (3) all work.

**Names.** Rename every `...Rupees` identifier to `...Minor` (`amountMinor`, `rateMinor`, `costMinor`). Keeping the old names with a new meaning would plant a bug in every future change. The compiler then finds every use for me, which is why the rename is mechanical and safe.

**Business settings** (new columns on `organisations`, synced like the rest of the org):
- `currencyCode` (ISO 4217, for example `INR`, `USD`). Default for existing rows: `INR`.
- `billRoundingMinor`: the smallest step a bill is rounded to. `1` is exact, `100` is whole currency unit (today's behaviour). Default `100` for INR so India behaves exactly as now, `1` for a currency whose users expect cents. One number drives both Kotlin and the 5 SQL queries (passed in as a query parameter), so they can never disagree.

**Person settings** (device): number grouping (Indian 3-2-2 or international 3-3-3), decimal and grouping separators and symbol position from the device locale. Grouping stays spelled out in our own code, as `Money.kt` does today, so the same amount reads the same on every handset.

**Formatting API.** One `MoneyStyle(currencyCode, digits, grouping, locale symbols)` replaces `rupees(...)`:
- UI gets it through a Compose `CompositionLocal` fed from the session.
- ViewModels and the statement documents receive it explicitly (a statement is always for one business; it must not pick up a different currency because the user switched business meanwhile).
- Shorthand: `L/Cr` with Indian grouping, `k/M/B` with international.
- Amount in words: kept for the Indian system on rupee bills; for other currencies and languages the line is left off rather than written wrongly (it comes back per language later).

**Input.** Money fields accept as many decimals as the currency has and no more, with the existing digit cap kept. The same cleaning helper serves rates, payments and amendments.

**Cross-business totals.** Anywhere one screen adds money from two businesses it would add rupees to dollars. I will search for every such place during the build; any found shows per-business figures instead of one total.

## 3. Migration

1. **Room v16 to v17** (`Migrations.kt`, `MIGRATION_16_17`):
   - add `organisations.currencyCode` (default `INR`) and `billRoundingMinor` (default `100`);
   - multiply the 9 money columns by 100;
   - multiply `job_events.numericValue` by 100 **only** where `type` is `TripAdded`, `TripRemoved` or `RateChanged`;
   - rewrite every `outbox.payloadJson` (done in Kotlin with the platform JSON classes, because SQLite JSON functions are not on every supported Android version): rename the fields, multiply by 100, add the format marker.
2. **Server documents.** New documents carry the new field names and a format marker. A document *without* the marker is read as legacy (old names, multiply by 100) by `RestoreApplier`. This keeps the existing test business and any phone still on an older build working; before release there are no real users, so it can be removed once the test data is rewritten. An older build cannot read new documents and skips them row by row, which is acceptable before release. After release this is the reason for the "minimum supported version" gate in M5.
3. **Tests.** The existing 793 tests are renamed mechanically; formatting tests are rewritten; arithmetic tests keep their meaning because the rupee amounts simply become minor units. New tests:
   - currencies with 0, 2 and 3 decimals through input, storage, formatting and rounding;
   - both groupings and both rounding steps (exact, whole unit) agreeing between Kotlin and SQL;
   - the migration with real rows including a trip event, a rate-change event and a pending outbox payload, and the legacy server document read;
   - mutation checks on every fix, as before.
4. **Device check.** Install over a v16 database on the emulators and confirm every figure on Home, Finish, Job detail, customer khata, statement, Season and Machine screens is unchanged for INR; then switch a test business to USD (and to a no-decimal currency) and walk the same screens in light and dark, phone and tablet.

## 4. Order of work inside M1

- **M1.1** Money (this plan). India behaves exactly as today after it.
- **M1.2** Adaptive onboarding and the settings screen: detect country, language, zone and currency from the phone; confirm currency and land unit; change later; currency locks after the first entry.
- **M1.3** Units (hectare, square metre, bale, tonne, bushel, kilogram, litre) and number/date/week formats.
- **M1.4** Business time zone (day roll, plan day, bill numbers, seasons, statements).
- **M1.5** Phone numbers worldwide, then Google sign-in.
- **M1.6** Languages framework, right-to-left layout, scripts.
- **M1.7** Payment methods per region and the optional tax line.

## 5. Risks

- A missed `numericValue` conversion would show a trip priced at 1/100th. Covered by the migration test above and a device check of an existing job's timeline.
- A missed SQL rounding site would make a list total disagree with a bill by a few minor units. Covered by one test that compares every total query with `Billing.amount` for the same job.
- The Pixel 9 Pro XL and Samsung still carry an older build; they will need the new build installed together with the emulators or they will write old-format documents.
- Cross-business sums (above).

## 6. Not decided here (nothing blocks the work)

- Which currencies appear first in the picker: the device's country currency on top, then a search across all platform currencies.
