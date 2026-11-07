# FieldTrack functions

Our own small server code. Two jobs: instant notifications, and carrying out account
deletion requests. It is **not deployed yet**; it is part of the release work.

```
cd functions
npm install
firebase deploy --only functions
```

Needs the Blaze (pay as you go) plan on the Firebase project. At the sizes this app
starts at, the free allowance covers it.

The messages never carry an amount or a name. They say only which thing happened
(`booking_new`, `booking_changed`, `booking_cancelled`, `job_handed`, `payment_recorded`);
the phone words it, in the person's language, and shows only "you have a reminder" on a
locked screen. Which phones to tell comes from `people/{personId}/devices/{deviceId}`,
which each phone writes itself, with one true/false per kind.
