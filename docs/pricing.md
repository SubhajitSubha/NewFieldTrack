# How the app earns

The product owner wants to charge, and early users must never be surprised. The shape below is
built; the figures and the lines between free and paid are the owner's to confirm or change.

## The promise
**Nothing is ever taken away.** If a plan lapses, a business can still see every record, export
everything, and keep recording work, and so can everybody in it. A plan only ever stops *adding*:
a team member, a machine over the free number, or a paid extra. This is written into the code
(`PlanLimits`, `PlanGate`) and tested (`PlansTest`: "a plan that ran out takes nothing away").

## Free
One person, up to **3 machines**, everything for running the work and keeping the khata: any number
of farmers, jobs, bills, payments, reminders, statements, receipts, the income statement, the asset
register, export, help and the practice business.

## Business (paid)
A team (drivers, managers, viewers), any number of machines, the booking link, proof of work.

Change the lines in one file: `data/plan/Plans.kt` (`PlanLimits.FREE_MACHINES`, `FREE_PEOPLE`, and
which needs are paid in `Plans.verdict`).

## Price
- One subscription in the store, `fieldtrack_business`, with two base plans: **`season`** (6 months)
  and **`year`**. Farm income comes in seasons, so a season is offered beside a year.
- Prices are set per country in the store console; the app shows whatever the store says for the
  person's country, in their currency, and never works a price out itself.
- **Founding price:** an offer on each base plan tagged `founding`, available only to the first
  users (the console limits who is eligible). The app shows it where it exists, and the server
  remembers that a business bought at it and keeps the flag while it renews.
- Suggested starting point to decide: a season at roughly the price of a day's work for one machine,
  a year at about 1.6 seasons.

## How a business is known to have paid
1. The owner pays in the store; the purchase is tied to the business (`obfuscatedAccountId`).
2. The owner's phone writes a claim under the business (`orgs/{id}/purchase_claims`).
3. Our server (`onPurchaseClaim`) asks the store whether it is real, whether it is for this
   business and when it runs out, and writes `orgs/{id}/entitlement/current`.
4. Every phone reads that like any other record, so the check works offline. The extras follow it
   with a **14 day grace** after the end date, so a slow renewal or a month with no signal never
   switches anything off in the middle of a season.
5. Renewals reach the server by the owner's phone claiming again about once a day while a purchase
   is active. (Later, the store's real-time notifications can do it with no phone involved.)

No phone can write the entitlement, the owner's included (see the rules).

## Needs doing in the stores (release work)
- Create the subscription and its two base plans, and the founding offers, in the Play Console.
- Switch on the Google Play Developer API for the project, invite the functions' service account
  to the Play Console with permission to view financial data, and set `PLAY_PACKAGE` for the
  functions to the real application id.
- Test with licence testers before anyone real pays.
