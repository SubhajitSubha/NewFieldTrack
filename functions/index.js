// Our own code on Google's servers. Two jobs:
//
//   1. Tell a phone the moment something happens that is for that person: work booked
//      for them, a job handed to them, a payment recorded in a business they look
//      after. The phone decides how to word it; the message carries only which thing
//      happened, never an amount or a name, because it can be read off a lock screen.
//
//   2. Carry out account deletion requests (`deletionRequests/{personId}`), because
//      nothing on a handset is allowed to delete a business.
//
// Deploy with `firebase deploy --only functions`. Needs the Blaze plan. Costs are a
// document read per recipient per event, which the free tier covers at first.

const { onDocumentWritten, onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * The phones to tell: the person's devices that asked for this kind, apart from the one
 * that made the change (it already knows).
 */
async function devicesFor(personId, kind, writerDevice) {
  const snap = await db.collection("people").doc(personId).collection("devices").get();
  return snap.docs
    .filter((d) => d.get(kind) !== false && d.get("token") && d.id !== writerDevice)
    .map((d) => ({ id: d.id, token: d.get("token"), personId }));
}

/** Sends one event to a list of devices, and forgets any phone the system says is gone. */
async function send(devices, event, orgId) {
  if (devices.length === 0) return;
  const result = await admin.messaging().sendEachForMulticast({
    tokens: devices.map((d) => d.token),
    data: { event, orgId },
    android: { priority: "high" },
  });
  const dead = [];
  result.responses.forEach((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") dead.push(devices[i]);
  });
  await Promise.all(dead.map((d) => db.collection("people").doc(d.personId).collection("devices").doc(d.id).delete()));
}

// A booking made for somebody, moved, or cancelled.
exports.onBooking = onDocumentWritten("orgs/{orgId}/bookings/{id}", async (event) => {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.exists ? event.data.after.data() : null;
  const orgId = event.params.orgId;
  const writer = after && after._device;

  const gone = !after || after.deletedAt || after.status === "Cancelled";
  const wasLive = before && !before.deletedAt && before.status !== "Cancelled";

  if (gone) {
    if (wasLive && before.operatorPersonId) {
      await send(await devicesFor(before.operatorPersonId, "booking", writer), "booking_cancelled", orgId);
    }
    return;
  }
  const driver = after.operatorPersonId;
  if (before && before.operatorPersonId && before.operatorPersonId !== driver && wasLive) {
    // Taken off one person's day.
    await send(await devicesFor(before.operatorPersonId, "booking", writer), "booking_cancelled", orgId);
  }
  if (!driver) return;
  if (!before || before.operatorPersonId !== driver || !wasLive) {
    await send(await devicesFor(driver, "booking", writer), "booking_new", orgId);
  } else if (
    before.day !== after.day || before.part !== after.part || before.machineId !== after.machineId ||
    before.fieldId !== after.fieldId || before.workType !== after.workType || before.startMinute !== after.startMinute
  ) {
    await send(await devicesFor(driver, "booking", writer), "booking_changed", orgId);
  }
});

// A job handed to somebody.
exports.onJob = onDocumentWritten("orgs/{orgId}/jobs/{id}", async (event) => {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.exists ? event.data.after.data() : null;
  if (!after || after.deletedAt || after.status === "Completed" || after.status === "Discarded") return;
  const to = after.operatorPersonId;
  if (!to || (before && before.operatorPersonId === to)) return;
  await send(await devicesFor(to, "job", after._device), "job_handed", event.params.orgId);
});

// A payment recorded: tell those who look after the money, apart from whoever recorded it.
exports.onPayment = onDocumentCreated("orgs/{orgId}/ledger_entries/{id}", async (event) => {
  const entry = event.data.data();
  if (!entry || entry.kind !== "Payment") return;
  const orgId = event.params.orgId;
  const members = await db.collection("orgs").doc(orgId).collection("memberships").where("active", "==", true).get();
  const keepers = members.docs
    .map((m) => m.data())
    .filter((m) => (m.role === "Owner" || m.canSeeMoney === true) && m.personId && m.personId !== entry.byPersonId);
  const lists = await Promise.all(keepers.map((m) => devicesFor(m.personId, "payment", entry._device)));
  await send(lists.flat(), "payment_recorded", orgId);
});

// An account deletion request: remove the person's sign-in and the businesses that are
// theirs alone. What they recorded in a shared business stays, as the business's own
// records; the app told them so before they asked.
exports.onDeletionRequest = onDocumentWritten("deletionRequests/{personId}", async (event) => {
  if (!event.data.after.exists) return;
  const request = event.data.after.data();
  const personId = event.params.personId;
  for (const orgId of request.ownedAlone || []) {
    await db.recursiveDelete(db.collection("orgs").doc(orgId));
  }
  await db.recursiveDelete(db.collection("people").doc(personId));
  try {
    await admin.auth().deleteUser(personId);
  } catch (e) {
    if (e.code !== "auth/user-not-found") throw e;
  }
  await event.data.after.ref.set({ doneAt: Date.now() }, { merge: true });
});
