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
const { onRequest } = require("firebase-functions/v2/https");
const crypto = require("crypto");
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

/**
 * Sends one event to a list of devices, and forgets any phone the system says is gone.
 * [what] names what it was about (a machine, a person) and is never an amount.
 */
async function send(devices, event, orgId, what) {
  if (devices.length === 0) return;
  const result = await admin.messaging().sendEachForMulticast({
    tokens: devices.map((d) => d.token),
    data: what ? { event, orgId, what: String(what).slice(0, 60) } : { event, orgId },
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

/** The people of a business who are on the books today. */
async function membersOf(orgId) {
  const snap = await db.collection("orgs").doc(orgId).collection("memberships").where("active", "==", true).get();
  return snap.docs.map((d) => d.data()).filter((m) => m.personId);
}

/** An owner, or somebody who holds any one of these switches. */
function keeps(member, flags) {
  return member.role === "Owner" || flags.some((f) => member[f] === true);
}

/** Tells a set of people, on every phone of theirs that wants this kind, apart from the sender's own. */
async function tell(personIds, kind, writerDevice, event, orgId, what) {
  const lists = await Promise.all([...personIds].map((p) => devicesFor(p, kind, writerDevice)));
  await send(lists.flat(), event, orgId, what);
}

// A machine broke down, or is working again. News for everybody it touches: the owner and
// those who look after machines or plan work, and whoever is booked on it or out on it now.
exports.onBreakdown = onDocumentWritten("orgs/{orgId}/breakdowns/{id}", async (event) => {
  const before = event.data.before.exists ? event.data.before.data() : null;
  const after = event.data.after.exists ? event.data.after.data() : null;
  if (!after || after.deletedAt) return;
  const opened = !before;
  const fixed = !!before && !before.resolvedAt && !!after.resolvedAt;
  if (!opened && !fixed) return;
  const orgId = event.params.orgId;

  const machineSnap = after.machineId ? await db.doc(`orgs/${orgId}/machines/${after.machineId}`).get() : null;
  const what = machineSnap && machineSnap.exists ? machineSnap.get("name") : null;

  const people = new Set(
    (await membersOf(orgId)).filter((m) => keeps(m, ["canEditMachines", "canPlanWork"])).map((m) => m.personId),
  );
  if (after.machineId) {
    const today = Math.floor(Date.now() / 86400000);
    const [booked, running] = await Promise.all([
      db.collection(`orgs/${orgId}/bookings`).where("machineId", "==", after.machineId).where("status", "==", "Planned").get(),
      db.collection(`orgs/${orgId}/jobs`).where("machineId", "==", after.machineId).where("status", "in", ["Running", "Paused"]).get(),
    ]);
    booked.docs.forEach((d) => {
      const b = d.data();
      if (!b.deletedAt && b.operatorPersonId && b.day >= today) people.add(b.operatorPersonId);
    });
    running.docs.forEach((d) => {
      const j = d.data();
      if (!j.deletedAt && j.operatorPersonId) people.add(j.operatorPersonId);
    });
  }
  // The person who reported it already knows.
  if (after.reportedByPersonId) people.delete(after.reportedByPersonId);
  await tell(people, "breakdown", after._device, opened ? "breakdown_new" : "breakdown_fixed", orgId, what);
});

// "I need help": written on a job's timeline by the person on it. The owner and those who
// plan work are told at once, on the loudest channel the phone has.
exports.onHelp = onDocumentCreated("orgs/{orgId}/job_events/{id}", async (event) => {
  const e = event.data.data();
  if (!e || e.type !== "HelpAsked") return;
  const orgId = event.params.orgId;
  const members = await membersOf(orgId);
  const sender = members.find((m) => m.personId === e.byPersonId);
  const people = new Set(members.filter((m) => keeps(m, ["canPlanWork", "canEditTeam"])).map((m) => m.personId));
  if (e.byPersonId) people.delete(e.byPersonId);
  await tell(people, "sos", e._device, "sos", orgId, sender ? sender.displayName : null);
});

// Somebody redeemed a code and joined: tell the owners, and whoever looks after the team.
exports.onJoin = onDocumentCreated("orgs/{orgId}/team_events/{id}", async (event) => {
  const e = event.data.data();
  if (!e || e.kind !== "Joined") return;
  const orgId = event.params.orgId;
  const people = new Set((await membersOf(orgId)).filter((m) => keeps(m, ["canEditTeam"])).map((m) => m.personId));
  if (e.byPersonId) people.delete(e.byPersonId);
  await tell(people, "team", e._device, "member_joined", orgId, e.byName);
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


// ---------------------------------------------------------------------------------------------
// The booking link: a small public page for a business, and the form that sends a request.
//
//   GET  /b/{slug}     the page: the business's name, optionally its rates, and the form
//   POST /api/request  a request for work, which lands in the owner's requests
//
// Nothing here books anything. A request is a document the owner sees, and accepts or declines
// by hand. The slug is the secret in the link; the owner can switch the link off, and then this
// answers "not found".

const WORK_TYPES = [
  "Rotavator", "Ploughing", "Harvesting", "Levelling", "Spraying", "Hauling", "Sowing",
  "Planting", "Baling", "Threshing", "Mulching", "Digging", "Pumping",
  "Puddling", "Ridging", "Loading", "WaterSupply", "MachineHire", "Other",
];
// The stored keys as a person reads them. A key that is not here is a business's own name for
// its work, and is shown as it is.
const WORK_NAMES = { WaterSupply: "Water supply", MachineHire: "Machine hire" };
const workName = (key) => WORK_NAMES[key] || key;
// The business's own names for its work, from its rate card, offered beside ours.
async function ownWork(org) {
  const snap = await org.ref.collection("rates").get();
  return [...new Set(snap.docs.map((d) => String(d.get("workType") || "")).filter((w) => w && w.length <= 40 && !WORK_TYPES.includes(w)))];
}
const MAX_PER_ORG_PER_DAY = 40;
const MAX_PER_PHONE_PER_DAY = 3;

const esc = (v) => String(v == null ? "" : v).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

async function orgBySlug(slug) {
  if (!/^[a-f0-9]{10,32}$/.test(slug || "")) return null;
  const found = await db.collection("orgs").where("bookingSlug", "==", slug).where("bookingLink", "==", true).limit(1).get();
  return found.empty ? null : found.docs[0];
}

const page = (body) => `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:system-ui,sans-serif;background:#f5f3ee;color:#16231c;margin:0}main{max-width:480px;margin:0 auto;padding:20px}
h1{font-size:24px;margin:8px 0 4px}label{display:block;font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:#4a5a51;margin:14px 0 6px}
input,select,textarea{width:100%;box-sizing:border-box;font-size:16px;padding:12px;border:1px solid #e2e0d8;border-radius:12px;background:#fff}
button{width:100%;margin-top:18px;padding:14px;border:0;border-radius:14px;background:#0e6b3d;color:#fff;font-size:17px;font-weight:700}
.card{background:#fff;border:1px solid #e2e0d8;border-radius:16px;padding:14px;margin-top:14px}.sub{color:#4a5a51}.hp{position:absolute;left:-9999px}</style></head>
<body><main>${body}</main></body></html>`;

exports.bookingPage = onRequest({ cors: false }, async (req, res) => {
  const slug = (req.path.match(/^\/b\/([a-f0-9]+)\/?$/) || [])[1];
  const org = slug && (await orgBySlug(slug));
  if (!org) {
    res.status(404).send(page("<h1>This link is not available</h1><p class='sub'>Ask the business for a new one.</p>"));
    return;
  }
  const data = org.data();
  let rates = "";
  if (data.bookingShowRates === true) {
    const snap = await org.ref.collection("rates").get();
    // Whole units for a currency with no minor unit; hundredths otherwise.
    let digits = 2;
    try { digits = new Intl.NumberFormat("en", { style: "currency", currency: data.currencyCode || "USD" }).resolvedOptions().maximumFractionDigits; } catch (e) { /* unknown code: hundredths */ }
    const lines = snap.docs.map((d) => d.data()).filter((r) => !r.customerId && r.amountMinor > 0)
      .map((r) => `<div style="display:flex;justify-content:space-between;padding:4px 0"><span>${esc(workName(r.workType))}</span><span>${esc((r.amountMinor / Math.pow(10, digits)).toFixed(digits))} ${esc(data.currencyCode || "")} / ${esc(String(r.unit || "").toLowerCase())}</span></div>`);
    if (lines.length) rates = `<div class="card"><strong>Usual rates</strong>${lines.join("")}</div>`;
  }
  const own = await ownWork(org);
  const offered = [...WORK_TYPES.filter((w) => w !== "Other"), ...own, "Other"];
  const options = offered.map((w) => `<option value="${esc(w)}">${esc(workName(w))}</option>`).join("");
  res.status(200).send(page(`<h1>${esc(data.name)}</h1><p class="sub">Tell us what work you need. We will call you to confirm; nothing is booked until we do.</p>${rates}
<form method="post" action="/api/request"><input type="hidden" name="slug" value="${esc(slug)}">
<input class="hp" name="website" tabindex="-1" autocomplete="off">
<label>Your name</label><input name="name" required maxlength="80">
<label>Your phone number (with country code)</label><input name="phone" required inputmode="tel" maxlength="20" placeholder="+91 98765 43210">
<label>Village or place</label><input name="village" maxlength="80">
<label>What work</label><select name="workType">${options}</select>
<label>Which day (if you know)</label><input type="date" name="day">
<label>Anything we should know</label><textarea name="note" rows="3" maxlength="300"></textarea>
<button type="submit">Send the request</button></form>`));
});

exports.bookingRequest = onRequest({ cors: false }, async (req, res) => {
  if (req.method !== "POST") { res.status(405).send("Method not allowed"); return; }
  const b = req.body || {};
  // A real person leaves the hidden field empty; a script fills every field it sees.
  if (b.website) { res.status(200).send(page("<h1>Thank you</h1>")); return; }
  const org = await orgBySlug(String(b.slug || ""));
  if (!org) { res.status(404).send(page("<h1>This link is not available</h1>")); return; }
  const name = String(b.name || "").trim().slice(0, 80);
  const phone = String(b.phone || "").replace(/[^0-9+]/g, "").slice(0, 20);
  if (!name || phone.replace(/\D/g, "").length < 7) {
    res.status(400).send(page("<h1>Please give a name and a phone number</h1><p class='sub'>Go back and try again.</p>"));
    return;
  }
  // Ours, or one of the business's own names from its rate card; anything else is "Other".
  const asked = String(b.workType || "");
  const workType = WORK_TYPES.includes(asked) || (await ownWork(org)).includes(asked) ? asked : "Other";
  const day = /^\d{4}-\d{2}-\d{2}$/.test(b.day || "") ? Math.floor(Date.parse(b.day + "T00:00:00Z") / 86400000) : null;

  // A day's limits, kept as counters on the business so one link cannot flood an inbox.
  const today = new Date().toISOString().slice(0, 10);
  const orgCounter = org.ref.collection("meta").doc("requests-" + today);
  const phoneCounter = org.ref.collection("meta").doc("requests-" + today + "-" + crypto.createHash("sha1").update(phone).digest("hex").slice(0, 12));
  const allowed = await db.runTransaction(async (tx) => {
    const [o, p] = await Promise.all([tx.get(orgCounter), tx.get(phoneCounter)]);
    const orgN = (o.exists ? o.get("n") : 0) + 1;
    const phoneN = (p.exists ? p.get("n") : 0) + 1;
    if (orgN > MAX_PER_ORG_PER_DAY || phoneN > MAX_PER_PHONE_PER_DAY) return false;
    tx.set(orgCounter, { n: orgN });
    tx.set(phoneCounter, { n: phoneN });
    return true;
  });
  if (!allowed) { res.status(429).send(page("<h1>Too many requests today</h1><p class='sub'>Please call the business instead.</p>")); return; }

  const now = Date.now();
  const id = crypto.randomUUID();
  const doc = {
    id, orgId: org.id, name, phone,
    village: String(b.village || "").trim().slice(0, 80) || null,
    workType, day,
    note: String(b.note || "").trim().slice(0, 300) || null,
    status: "New", bookingId: null,
    createdAt: now, updatedAt: now, syncState: "Pending",
    _money: 2, _device: "booking-page", _recordedAt: now,
    _serverAt: admin.firestore.FieldValue.serverTimestamp(),
  };
  const batch = db.batch();
  batch.set(org.ref.collection("booking_requests").doc(id), doc);
  // So every phone's one-read "has anything changed?" check sees it.
  batch.set(org.ref.collection("meta").doc("changed"), { at: admin.firestore.FieldValue.serverTimestamp() });
  await batch.commit();

  // Tell those who plan, at once.
  const members = await org.ref.collection("memberships").where("active", "==", true).get();
  const planners = members.docs.map((m) => m.data()).filter((m) => m.personId && (m.role === "Owner" || m.canPlanWork === true));
  const lists = await Promise.all(planners.map((m) => devicesFor(m.personId, "request", "booking-page")));
  await send(lists.flat(), "request_new", org.id);

  res.status(200).send(page("<h1>Thank you</h1><p class='sub'>Your request has been sent. The business will call you to confirm.</p>"));
});


// ---------------------------------------------------------------------------------------------
// Paying. The owner's phone writes a claim ("here is a purchase token") under the business; this
// asks the store whether it is real, whether it is for this business, and when it runs out, and
// writes the answer where every phone in the business reads it. Nothing on a phone can say a
// business has paid. The product and its two base plans ("season", "year") are made in the
// store's console; an offer tagged "founding" is the founding price.
//
// Needs: the Google Play Developer API switched on for the project, this function's service
// account invited to the Play Console with permission to view financial data, and PLAY_PACKAGE
// set to the app's real package name (see docs/server-setup.md).

const { google } = require("googleapis");

exports.onPurchaseClaim = onDocumentWritten("orgs/{orgId}/purchase_claims/{id}", async (event) => {
  if (!event.data.after.exists) return;
  const orgId = event.params.orgId;
  const claim = event.data.after.data();
  const ref = event.data.after.ref;
  try {
    const publisher = google.androidpublisher({
      version: "v3",
      auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }),
    });
    const { data: sub } = await publisher.purchases.subscriptionsv2.get({
      packageName: process.env.PLAY_PACKAGE || "com.example.fieldtrack",
      token: claim.token,
    });
    // Valid while the store says so; a cancelled subscription is still paid until it runs out.
    const live = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"].includes(sub.subscriptionState);
    const forThisBusiness = sub.externalAccountIdentifiers && sub.externalAccountIdentifiers.obfuscatedExternalAccountId === orgId;
    const line = (sub.lineItems || [])[0] || {};
    const until = line.expiryTime ? Math.floor(Date.parse(line.expiryTime) / 86400000) : null;
    if (!live || !forThisBusiness || until == null) {
      await ref.set({ checkedAt: Date.now(), accepted: false }, { merge: true });
      return;
    }
    const entitlement = db.collection("orgs").doc(orgId).collection("entitlement").doc("current");
    const before = (await entitlement.get()).data() || {};
    const tags = (line.offerDetails && line.offerDetails.offerTags) || [];
    await entitlement.set({
      id: orgId, orgId, tier: "Business",
      untilDay: Math.max(until, before.untilDay || 0),
      founding: before.founding === true || tags.includes("founding"),
      updatedAt: Date.now(),
      _money: 2, _device: "server", _recordedAt: Date.now(),
      _serverAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    // So every phone's one-read "has anything changed?" check notices.
    await db.collection("orgs").doc(orgId).collection("meta").doc("changed").set({ at: admin.firestore.FieldValue.serverTimestamp() });
    await ref.set({ checkedAt: Date.now(), accepted: true, untilDay: until }, { merge: true });
  } catch (e) {
    await ref.set({ checkedAt: Date.now(), accepted: false, error: String(e.message || e).slice(0, 200) }, { merge: true });
  }
});
