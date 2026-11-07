# Reads, writes and speed at scale

## What was measured
`ScaleTest` seeds three seasons of a busy contractor (about 5,000 jobs, 7,500 khata lines,
300 farmers) and times the queries behind Home, Jobs, Customers, the khata and Export.
Every one comes back in tens of milliseconds on a laptop JVM; the limits in the test are
loose on purpose and exist to catch an index that stops being used.

## Server reads
A pull used to ask the org document, then each of the 18 synced collections for rows newer
than the phone's mark, then the person's membership and account documents: about 21 reads
even when nothing had changed. Firestore bills at least one read per query.

Now every commit also touches `orgs/{orgId}/meta/changed` (one extra write per commit, not
per row). A pull first reads that document; if its time is not newer than the phone's mark,
the pull stops there. About 3 reads for an idle poll (marker, membership, account), instead
of 21. A full read of every collection still happens
 - the first time a business is read,
 - whenever the marker cannot be read or does not exist yet (an older writer),
 - and every 30 minutes regardless, so a change written by something that did not leave the
   marker is never hidden for long.

With the freshness poll every two minutes while a team member has the app open:
 - before: about 21 x 30 = 630 reads per hour on screen,
 - now: about 3 x 30 = 90, plus a full read every half hour (about 42).

Price at US rates: about $0.06 per 100,000 reads. An active member for four hours a day is
roughly 500 reads a day now, against 2,500 before; the free allowance of 50,000 reads a day
covers about 100 such members instead of 20.

Writes: each commit is now one extra document. A busy driver day of 60 commits adds 60 writes.
