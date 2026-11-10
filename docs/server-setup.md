# Things only the Firebase console can do

None of these can be done from the app's code. Each is needed before the feature it names
works for real people. They are listed again, with the rest, in the release list at the
bottom of ROADMAP.md.

1. **Rules.** `firestore.rules` has changed (viewer role, account deletion requests, the
   version document, per-phone notification rows). Publish the **whole file**.
   **Changed again with the trust-and-money round (Q):** a person may read payments made to them;
   new blocks for `cash_handovers`, `member_details` and `member_pay_terms`; the blanket grant leaves
   all three out. Until it is published, those three are covered by the blanket grant (every member
   may read them), so publish it before anybody real enters a bank account or a wage. No index is
   needed: a person's own payments are asked for by `toPersonId` alone.
2. **Version document.** Create `appConfig/android` with two numbers:
   `minVersionCode` (the oldest app version still allowed to share data) and
   `latestVersionCode` (the newest one in the store). Leave both at 0 until a first
   release exists. Raising `minVersionCode` stops older installs from sending, and
   shows them the update page; their records stay on the phone.
3. **Functions.** `functions/` holds our notification and account-deletion code. Deploy with
   `firebase deploy --only functions`. Needs the Blaze plan. It now also sends the breakdown,
   "I need help" and "somebody joined" messages (`onBreakdown`, `onHelp`, `onJoin`), so deploy it
   again after pulling the field-feedback round. The rules file did not change in that round.
4. **Cloud Messaging.** Nothing to switch on beyond the project already being linked;
   the new `google-services.json` should be downloaded again after the changes below.
5. **Google sign-in.** Enable the Google provider, add the app's SHA-1 key, download the new
   `google-services.json`.
6. **Support address.** Fill `support_email` in `app/src/main/res/values/config.xml`.
   While it is empty, the "Write to us" rows are not shown.
7. **Photo storage.** Proof-of-work photos are held in Firebase Storage. Turn Storage on for the
   project, then publish `storage.rules` (the console asks to allow the rules to read Firestore
   the first time). Until it is published, photos stay on the phone that took them and the owner
   sees the readings and the place but not the picture.
8. **The booking link.** Deploy functions and hosting together (`firebase deploy --only functions,hosting`):
   hosting serves `web/`, and `firebase.json` sends `/b/{secret}` to the page and `/api/request` to the
   form. The link a business shares is `https://<project>.web.app/b/<secret>`. Needs Hosting switched
   on for the project; both pieces need the Blaze plan. Publish the whole rules file again: it now has
   a rule for `booking_requests`.
9. **Paying.** `docs/pricing.md` lists what to create in the Play Console and what to give the server.
   Deploy the functions again (they now include the purchase check) and publish the whole rules file:
   it has rules for `entitlement` and `purchase_claims`.
