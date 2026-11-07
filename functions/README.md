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

The messages never carry an amount. Most say only which thing happened
(`booking_new`, `booking_changed`, `booking_cancelled`, `job_handed`, `payment_recorded`); breakdown, help and join add the machine's or person's name;
the phone words it, in the person's language, and shows only "you have a reminder" on a
locked screen. Which phones to tell comes from `people/{personId}/devices/{deviceId}`,
which each phone writes itself, with one true/false per kind.

## What sends a notification
`onBooking` (work booked, moved, cancelled), `onJob` (handed over), `onPayment`, `bookingRequest`,
and, added with the field-feedback round: `onBreakdown` (a machine breaks down or is working again:
told to the owner, machine and planning managers, and whoever is booked or running on it),
`onHelp` (someone on a job taps "I need help": owner and planners, on the urgent channel, no switch),
`onJoin` (someone redeemed a code: owners and team managers). None carries an amount; the breakdown,
help and join messages carry the machine's or person's name. **Redeploy `functions/` after pulling this.**
