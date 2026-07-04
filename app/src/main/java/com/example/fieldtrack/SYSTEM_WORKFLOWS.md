# System Workflows — Architecture Documentation

## Overview

This document describes the key operational lifecycles in FieldTrack.
All workflows follow the same principles:
- State machine governs transitions (JobPhase)
- Timestamps are source of truth (never tick accumulation)
- Persistence is throttled and debounced
- Recovery is automatic and invisible

---

## 1. Timer Lifecycle

```
┌─────────┐   startTimer()   ┌─────────┐   pauseTimer()   ┌─────────┐
│  SETUP  │ ───────────────→ │ RUNNING │ ───────────────→ │ PAUSED  │
└─────────┘                   └─────────┘                   └─────────┘
                                   │          resumeTimer()      │
                                   │      ←──────────────────────┘
                                   │
                              stopTimer()
                              (two-tap)
                                   │
                                   ▼
                              ┌─────────┐   saveJob()   ┌─────────┐
                              │ STOPPED │ ────────────→ │  SAVED  │
                              └─────────┘               └─────────┘
```

**Key invariants:**
- `elapsed = (now - startTime - totalPausedDuration) / 1000`
- Timer survives: backgrounding, doze, process death
- Auto-save heartbeat every 20 ticks (20s)
- Stop requires two taps within 3 seconds

---

## 2. Save Lifecycle

```
User taps Save
       │
       ▼
┌──────────────────┐
│ Validate canSave │ → fails → show error, return
└──────────────────┘
       │ passes
       ▼
┌──────────────────┐
│ Phase → SAVING   │
│ isSaving = true  │
│ hasSavedCurrentJob guard │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ Recalculate total│ (from raw segments — immutable snapshot)
│ Build JobEntity  │
│ saveMutex.withLock │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ jobRepository.saveJob() │
│ Remove from active_jobs │
│ Clear legacy active_job │
└──────────────────┘
       │
       ├── success ──→ Phase → SAVED, show completion summary
       │
       └── failure ──→ Phase → STOPPED, show retry message
                       (work preserved, never discarded)
```

**Guards against double-save:**
1. `hasSavedCurrentJob` flag
2. `isSaving` UI state check
3. `jobPhase == SAVING || SAVED` phase check
4. `saveMutex` prevents concurrent DB writes

---

## 3. Workspace Lifecycle

```
┌───────────────┐
│ App Launch    │
└───────────────┘
       │
       ▼
┌───────────────────────┐
│ cleanupStaleJobs()    │ → Remove jobs idle >8h
│ loadActiveJobsOnStart()│ → Remove phantoms (>60s no work)
│                       │ → Remove orphans (>4h no setup)
└───────────────────────┘
       │
       ▼
┌───────────────────────┐
│ Restore most recent   │  (or specific job via restoreJobId)
│ active job from DB    │
└───────────────────────┘
       │
       ▼
┌───────────────────────┐
│ User works...         │
│ (timer/trips/config)  │
└───────────────────────┘
       │
       ├── switchToJob() ──→ save current → load target
       ├── startNewJob() ──→ save current → fresh slate
       ├── saveJob()     ──→ remove from active_jobs
       └── discardJob()  ──→ remove from active_jobs
```

**Multi-job rules:**
- Max 20 active jobs (MAX_ACTIVE_JOBS)
- Machine locked when job has work (>3s timer OR segments/trips)
- Same machine cannot be in two active jobs
- Jobs auto-save every 20s while timer running

---

## 4. Recovery Lifecycle

```
┌───────────────────┐
│ Process Death     │
│ (system kills app)│
└───────────────────┘
       │
       ▼
┌───────────────────────┐
│ App relaunches        │
│ ViewModel recreated   │
└───────────────────────┘
       │
       ▼
┌───────────────────────┐
│ loadActiveJobsOnStart()│
│ Finds persisted job   │
│ (last auto-save ≤20s) │
└───────────────────────┘
       │
       ▼
┌───────────────────────────────┐
│ restoreFromActiveJobsEntity() │
│ - Reconstruct UI state        │
│ - Restore timer timestamps    │
│ - Resume ticking if was running│
│ - Show recovery banner (4s)   │
└───────────────────────────────┘
       │
       ▼
┌───────────────────────┐
│ Timer re-derives      │
│ elapsed from wall clock│
│ (no data loss for     │
│  time-based work)     │
└───────────────────────┘
```

**Data loss window:** Max ~20 seconds (last heartbeat save to death)
**Trip-based data loss:** Zero (trips saved immediately on add)

---

## 5. Cleanup Lifecycle

```
On ViewModel init:
       │
       ├── Phantom Cleanup: Jobs with no work, older than 60s → DELETE
       │
       ├── Orphan Cleanup: Jobs with no machine/rate/segments,
       │                    not paused/stopped, older than 4h → DELETE
       │
       └── Stale Cleanup: Jobs with no ongoing work (no segments,
                           no trips, not paused/stopped),
                           older than 8h → DELETE

On ensureActiveJobCreated():
       │
       └── Remove existing drafts (no work) before creating new

On onCleared() (ViewModel destroyed):
       │
       └── If current job has no work → DELETE from active_jobs
           (fire-and-forget on IO dispatcher)
```

---

## 6. Completion Lifecycle

```
┌───────────────┐
│ Timer stopped │ OR │ Trips added │
└───────────────┘
       │
       ▼
┌───────────────────────┐
│ canSave == true       │
│ User taps Save        │
└───────────────────────┘
       │
       ▼
┌───────────────────────────────────┐
│ WorkCompletionSummary created     │
│ (IMMUTABLE snapshot of:           │
│  - customer, machine, work type   │
│  - elapsed, trips, rate, total    │
│  - completedAt timestamp)         │
└───────────────────────────────────┘
       │
       ▼
┌───────────────────────────────────┐
│ Completion Sheet shown            │
│ Options:                          │
│  - Record Payment                 │
│  - Share (WhatsApp/image)         │
│  - Start Another (same customer)  │
│  - Repeat Same Work               │
│  - Done (dismiss)                 │
└───────────────────────────────────┘
```

**Immutability guarantee:**
- `completionSummary.total` is computed ONCE at save time
- Never recomputed from live entities
- Payment snapshot attached separately (post-completion)
- Share card renders from snapshot, not live state

---

## 7. Navigation Routes

| Route | Intent | State |
|-------|--------|-------|
| `Screen.NewJob.route` | Legacy: load most recent active job | Workspace |
| `Screen.NewJob.route + /draft` | Fresh blank slate | Draft |
| `Screen.NewJob.route + /restore/{jobId}` | Restore specific job | Workspace |
| `Screen.NewJob.route + /template/{id}` | Quick-job from capability | Template |
| `Screen.NewJob.route + /customer/{id}` | Pre-select customer | Draft + Customer |

**Navigation safety:**
- BackHandler saves active work silently (if job started)
- BackHandler confirms discard (if meaningful edits in draft)
- No hidden side effects — all state changes are explicit

