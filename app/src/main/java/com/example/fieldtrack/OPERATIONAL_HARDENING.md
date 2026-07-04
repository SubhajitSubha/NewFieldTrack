# Real-World Operational Hardening

## Summary of Changes

This document tracks all hardening changes made for production-grade field operation
under unstable conditions, long sessions, operator mistakes, and low-end devices.

**Phase completion status:**
- ✅ Operational Hardening (15 parts)
- ✅ Platform Readiness (15 parts)
- ✅ System Extraction (15 parts)
- ✅ Production Readiness (15 parts)

---

## PART 1 — State Machine Validation ✔

**File:** `domain/job/JobPhase.kt`
- Formal state machine with `allowedTransitions()` — illegal transitions are logged and blocked
- `transitionTo()` in ViewModel validates every phase change
- Added `RUNNING→SAVING` transition for trip-mode direct save

**File:** `ui/screens/newjob/NewJobViewModel.kt`
- `validateState()` self-heals 5 invariants:
  1. Dialog without pending mode → auto-dismiss
  2. Timer running without startTime → force stop
  3. Timer running AND paused simultaneously → force running only
  4. Negative totalPausedDuration → clamp to 0
  5. PausedDuration exceeds wallclock → reset to 0
- Trip adds (onAddTrip, incrementTrip) properly transition SETUP→RUNNING

---

## PART 2 — Timer Integrity ✔

- Timer ALWAYS derives from timestamps: `elapsed = (now - startTime - totalPausedDuration) / 1000`
- Never accumulates ticks — survives sleep, process death, doze
- `coerceAtLeast(0)` on all elapsed calculations (6+ places)
- `resumeTimer()` guards negative pausedMs: `.coerceAtLeast(0)`
- `validateState()` catches totalPausedDuration > wallclock corruption

---

## PART 3 — Database Write Safety ✔

- `saveMutex` (Kotlin Mutex) protects concurrent writes in:
  - `saveCurrentJobToMultiTable()`
  - `saveJobInternal()`
- `hasSavedCurrentJob` flag prevents duplicate final saves
- `persistActiveJob()` blocked in SAVED/SAVING phases
- `snapshotVersion` incremented on every persist (conflict detection)
- `lastUpdatedAt` explicitly set on every multi-table persist
- Throttled persist calls (MIN_PERSIST_INTERVAL_MS = 500ms)
- `startTime` saved as `0L` instead of `System.currentTimeMillis()` when null (prevents masking)

---

## PART 4 — Machine Locking ✔

- `isLockingMachine` requires actual work (segments/trips/timer > 3s) or configured > 30s
- `cleanupStaleJobs()` runs on every ViewModel init — removes jobs idle > 8h
- Prevents ghost machine locks from process death
- Completed jobs removed from active_jobs instantly on save

---

## PART 5 — Active Job Cleanup ✔

- Phantom jobs (no work, > 60s) cleaned on startup
- Orphan jobs (no machine/rate/segments, > 4h) cleaned on startup
- Stale jobs (no ongoing work, > 8h) cleaned on startup
- Empty drafts cleaned in `ensureActiveJobCreated()` before new creation
- Empty drafts cleaned in `onCleared()` using GlobalScope.launch (no deadlock)
- MAX_ACTIVE_JOBS (20) cap prevents unbounded growth

---

## PART 6 — Multi-Workspace Stress ✔

- `switchToJob()` saves current → loads new atomically
- `saveCurrentJobDebounced()` prevents write spam during rapid switching
- Timer isolation on job switch (cancel existing before restore)
- Job count capped at MAX_ACTIVE_JOBS = 20

---

## PART 7 — UI Recovery ✔

- `restoreFromActiveJobsEntity()` fully reconstructs state from DB
- Timer auto-resumes if was running
- Customer/machine resolved from DB
- Paused state preserved (pauseStartTime, totalPausedDuration)
- Resume banner shows time tracked for confidence

---

## PART 8 — Input Resilience ✔

- **Rate:** Filtered to digits+dot, single dot only, capped at 8 chars, min/max validation
- **Distance:** Filtered to digits+dot, single dot only, capped at 8 chars
- **Notes:** Capped at MAX_NOTES_LENGTH (500 chars)
- **Field name:** Capped at MAX_FIELD_NAME_LENGTH (100 chars)
- **Trips:** Capped at MAX_TRIP_SEGMENTS (999)
- **Segment add:** 400ms debounce guard (isAddingSegment)
- Graceful normalization — no aggressive blocking

---

## PART 9 — Save + Completion Trust ✔

- `WorkCompletionSummary` is immutable after creation
- Total computed from raw segments at save time (immutable snapshot)
- Payment snapshot attached post-completion (separate concern)
- `completionSummary.total` never recomputed from live data
- Double-save prevented by `hasSavedCurrentJob` + `isSaving` + phase checks

---

## PART 10 — Performance ✔

- Timer cost display throttled to every 5s (reduces flicker)
- Auto-save heartbeat every 20s (not every tick)
- `persistActiveJob()` throttled to 500ms minimum interval
- `saveCurrentJobDebounced()` with 400ms debounce
- `activeJobs` flow uses `WhileSubscribed(5000)` for resource efficiency

---

## PART 11 — Field Interruption ✔

- Timer survives backgrounding (derives from timestamps on return)
- Auto-save ensures < 20s of data loss on process death
- Recovery banner shows automatically on timer restore
- Stale banner appears for jobs inactive > 24h

---

## PART 12 — Visual Stability ✔

- Timer display always `HH:MM:SS` format (no layout jump at hour boundary)
- `ActiveJobSummary.formattedTime` uses fixed `%02d:%02d:%02d`
- `ActiveJobSummary.progressLabel` uses fixed format
- `tnum` font feature for tabular numerals (digits don't shift)
- Pulse animations calmed (1500ms/1800ms tween)

---

## PART 13 — Operator Error Recovery ✔

- **Accidental stop:** Two-tap confirmation (3s armed timeout)
- **Accidental back:** BackHandler saves active work silently, confirms for drafts
- **Machine switch:** Confirmation dialog when meaningful work exists
- **Discard:** Explicit confirmation dialog
- **Save failure:** Returns to STOPPED phase, retryable, work preserved

---

## PART 14 — Long-Term Data Model Safety ✔

- `ActiveJobsEntity.snapshotVersion` actively tracked and incremented
- Schema history documented in entity class comments
- `JobEntity` documented with schema versions and future extension plan
- Nullable columns with defaults = migration-safe additions
- Immutable completion boundary (WorkCompletionSummary)

---

## PART 15 — Operational Coherence ✔

- Status vocabulary unified: "Running" / "Paused" / "Completed" / "Ready"
- Status dots: 🟢 Running, ⏸ Paused, ✔ Completed
- JobPhase enum is single source of truth for lifecycle
- Persist status: IDLE → SAVING → SAVED → DELAYED → ERROR
- All constants centralized in `JobConstants`

---

## Constants Reference (JobConstants.kt)

| Constant | Value | Purpose |
|----------|-------|---------|
| MAX_RATE | ₹99,999 | Upper bound for rate input |
| MIN_RATE | ₹0.01 | Lower bound for rate |
| MAX_TRIP_SEGMENTS | 999 | Prevent infinite tap |
| MAX_TIMER_SECONDS | 86,400 | 24h auto-warning |
| MAX_RATE_INPUT_LENGTH | 8 | Input field cap |
| MAX_NOTES_LENGTH | 500 | Notes truncation |
| MAX_FIELD_NAME_LENGTH | 100 | Field name cap |
| MAX_DISTANCE_KM | 9,999 | Distance sanity |
| AUTO_SAVE_DEBOUNCE_MS | 2,000 | Event-save delay |
| MIN_PERSIST_INTERVAL_MS | 500 | Write throttle |
| MACHINE_LOCK_GRACE_PERIOD_MS | 30,000 | Lock delay |
| MAX_ACTIVE_JOBS | 20 | Job count cap |
| STALE_JOB_THRESHOLD_MS | 28,800,000 | 8h stale cleanup |

