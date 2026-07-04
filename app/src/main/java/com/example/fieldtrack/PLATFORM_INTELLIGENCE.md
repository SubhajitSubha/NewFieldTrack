# PLATFORM INTELLIGENCE — Architecture Documentation

**PART 12 — Platform Documentation Pass**

This document describes the internal architecture of FieldTrack's operational intelligence systems. Future contributors can use this to safely evolve system behavior.

---

## Overview

FieldTrack is built on a **calm, operational philosophy**:
- No dashboards or analytics complexity
- Paper-like simplicity
- Offline-first guarantees
- Predictable automation
- Quiet operation over time

---

## Orchestration Layers

### 1. Operational Language Registry
**File:** `OperationalLanguage.kt`

Centralizes all UI-facing operational vocabulary:
- Status labels (Running, Paused, Waiting, Finished)
- Payment labels (Pending, Collected, Partial)
- Action labels (Start, Continue, Finish)
- Feedback messages
- Time labels
- Format helpers

**Usage:**
```kotlin
OperationalLanguage.Status.RUNNING
OperationalLanguage.formatAmount(1500.0)
OperationalLanguage.formatTrips(5)
```

### 2. Intelligence Coordinator
**File:** `OperationalIntelligenceCoordinator.kt`

Single orchestration layer for all intelligence features:
- Suggestion generation
- Quick start actions
- Auto-fill recommendations
- Memory labels
- Continuity hints
- Search ranking

**Never scatter heuristics.** All intelligence flows through this coordinator.

### 3. Presentation Pipeline
**File:** `OperationalPresentation.kt`

Unified presentation formatting:
- Quantity displays
- Timeline labels
- Toolbar labels
- Rate labels
- Payment labels
- Accessibility labels

**Rule:** One canonical way to format each element.

---

## Operational Memory Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    OPERATIONAL MEMORY FLOW                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  JobEntity (History)                                            │
│       │                                                          │
│       ▼                                                          │
│  OperationalMemorySnapshot.kt                                    │
│       │                                                          │
│       ├── ContinuityMemory (last active, streaks)               │
│       ├── WorkPatternMemory (frequent capability, timing)        │
│       ├── PaymentRhythmMemory (payment patterns)                │
│       ├── BillingPreferenceMemory (rates, modes)                │
│       └── OperationalIdentityMemory (primary work type)          │
│                                                                  │
│       ▼                                                          │
│  OperationalIntelligenceCoordinator.kt                          │
│       │                                                          │
│       ├── generateSuggestions()      → SmartWorkSuggestion      │
│       ├── generateQuickStarts()      → QuickStartAction          │
│       ├── getAutoFillForCustomer()   → AutoFillRecommendation   │
│       ├── customerMemoryLabel()      → Passive label             │
│       └── machineContinuityLabel()   → Continuity label          │
│                                                                  │
│       ▼                                                          │
│  UI Components (consume final outputs)                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Fatigue System

**File:** `OperationalFatigueProfile.kt`

Single fatigue engine controlling all UI reduction:

### Fatigue Levels
| Level    | Hours | Description                    |
|----------|-------|--------------------------------|
| FRESH    | 0-2   | Full UI, all decorations       |
| LIGHT    | 2-4   | Slight reduction, warmer tones |
| MODERATE | 4-8   | Reduced helpers, no bounce     |
| DEEP     | 8-12  | Significant reduction          |
| ULTRA    | 12+   | Maximum reduction, quiet mode  |

### Profile Contents
- Alpha levels (primary, secondary, helper, decoration)
- Spacing multipliers
- Typography compression
- Visibility flags (headers, hints, decorations)
- Animation duration adjustments
- Haptic intensity

**Usage:**
```kotlin
val profile = OperationalFatigueProfile.getProfile(sessionHours)
if (profile.showHelperHints) { ... }
textAlpha = profile.helperTextAlpha
```

---

## Recovery Pipeline

**File:** `RecoveryDecisionEngine.kt`

Deterministic recovery behavior:

### Interruption Classification
| Duration   | Severity  | Banner | Auto-Resume |
|------------|-----------|--------|-------------|
| < 1 min    | MOMENTARY | No     | Yes (timer) |
| 1-5 min    | BRIEF     | No     | No          |
| 5-30 min   | SHORT     | Yes    | No          |
| 30m-2h     | MEDIUM    | Yes    | No          |
| 2-8h       | LONG      | Yes    | No          |
| 8h+        | OVERNIGHT | Yes    | No          |

### Recovery Decision Output
```kotlin
data class RecoveryDecision(
    val shouldShowBanner: Boolean,
    val shouldAutoResume: Boolean,
    val shouldPromptContinuation: Boolean,
    val shouldOfferDiscard: Boolean,
    val message: String,
    val warmth: WarmthLevel
)
```

### Warmth Levels
- NEUTRAL: Minimal messaging
- WARM: Friendly reassurance
- EXTRA_WARM: Long gaps, overnight sessions

---

## Session State Architecture

**File:** `OperationalSessionState.kt`

Unified high-level states consumed by UI:

```
SETUP ──────► RUNNING ──────► FINISHING ──────► FINISHED
   │              │ ▲              ▲
   │              ▼ │              │
   └──────► WAITING ├──► PAUSED ───┘
                    │
                    ▼
               RECOVERING
```

### State Properties
| State      | isActive | locksMachine | canFinish |
|------------|----------|--------------|-----------|
| SETUP      | No       | No           | No        |
| RUNNING    | Yes      | Yes          | Yes       |
| PAUSED     | Yes      | Yes          | Yes       |
| WAITING    | Yes      | No           | Yes       |
| FINISHING  | Yes      | Yes          | No        |
| FINISHED   | No       | No           | No        |
| RECOVERING | Yes      | Depends      | Yes       |

---

## Billing Capability Architecture

**File:** `BillingCapabilityMatrix.kt`

Configuration-driven billing modes:

### Capability Matrix
| Mode        | Timer | Counter | Quantity | Hybrid |
|-------------|-------|---------|----------|--------|
| PER_HOUR    | ✓ REQ | ✗       | ✗        | ✗      |
| PER_TRIP    | ✗     | ✓ REQ   | ✗        | ✗      |
| PER_ACRE    | ○     | ✗       | ✓ REQ    | ✗      |
| PER_PACKET  | ✗     | ✓ REQ   | ✗        | ✗      |
| FIXED       | ○     | ✗       | ✗        | ✗      |

Legend: ✓ = Supported, REQ = Required, ○ = Optional, ✗ = Not supported

### Future Hybrid Support
**File:** `TransportBillingProfile.kt`

Transport profiles support:
- Primary quantity (trips, packets, tonnage)
- Distance modifier
- Wait time modifier
- Unloading fee modifier
- Route-based pricing

**Foundation only — no UI exposure yet.**

---

## Continuity Systems

### Machine Lock Contract
- One machine can only be in ONE active job
- Lock acquired when: timer running, trips recorded, or paused with work
- Lock released when: job saved or discarded

### Workspace Continuity
- Multiple active workspaces supported (max 5)
- Each workspace isolated
- Rapid switching preserves all state
- Stale empty workspaces cleaned automatically

### Data Persistence
- All active work persisted to `active_jobs` table
- Debounced auto-save every 10 seconds
- Segment data persisted as JSON
- Recovery from process death guaranteed

---

## History Performance Strategy

**File:** `HistoryWindowingStrategy.kt`

Ensures 10,000+ jobs feel fast:

### Time Windows
- TODAY (expanded by default)
- YESTERDAY (expanded)
- THIS_WEEK (expanded)
- LAST_WEEK (collapsed)
- THIS_MONTH (collapsed)
- OLDER (collapsed)

### Loading Strategy
| Job Count | Initial Load | Page Size | Cache Windows |
|-----------|--------------|-----------|---------------|
| < 100     | All          | 50        | 6             |
| < 1000    | 50           | 50        | 4             |
| < 5000    | 30           | 30        | 3             |
| 5000+     | 20           | 20        | 2             |

### Grouping Thresholds
- Customer grouping: > 10 jobs
- Time grouping: > 5 jobs
- Virtualization: > 100 jobs

---

## Scaling Strategy

### Current Limits (Tested)
- Active workspaces: 5
- Jobs per day: 50+
- Historical jobs: 10,000+
- Customer count: 500+

### Future Scaling
- History pagination: HistoryWindowingStrategy
- Search optimization: Indexed search for large datasets
- Memory management: LRU cache for windows
- Offline sync: Batched background sync

---

## Safety Guarantees

**File:** `OperationalSafetyScenarios.kt`

Core invariants that must always hold:

1. **NO_WORK_LOSS** — No work data loss under any condition
2. **TIMER_INTEGRITY** — Timer calculations always correct
3. **MUTEX_PROTECTION** — Machine can only be in one active job
4. **RECONCILIATION** — Customer totals = sum of job amounts
5. **IDEMPOTENT_SAVE** — Save operation is idempotent
6. **STATE_VALIDITY** — State transitions are valid
7. **PERSISTENCE** — Active work persisted to database
8. **OFFLINE_FIRST** — All operations work offline

---

## Adding New Features Safely

### Before adding a feature:
1. Review existing orchestration layers
2. Check if feature fits existing patterns
3. Verify no invariant violations
4. Consider fatigue mode implications
5. Test recovery scenarios

### New billing mode:
1. Add to `BillingMode` enum
2. Add capability to `BillingCapabilityMatrix`
3. Update `OperationalPresentation` formatters
4. Test persistence scenarios

### New state:
1. Add to `OperationalSessionState`
2. Define valid transitions
3. Update `OperationalStateTransitions`
4. Test recovery with new state

### New intelligence:
1. Add to `OperationalIntelligenceCoordinator`
2. Derive from `OperationalMemorySnapshot`
3. Ensure stable ordering
4. Test recommendation stability

---

## File Index

| File | Purpose |
|------|---------|
| `OperationalLanguage.kt` | UI vocabulary registry |
| `OperationalIntelligenceCoordinator.kt` | Intelligence orchestration |
| `OperationalPresentation.kt` | Display formatting |
| `OperationalSessionState.kt` | Session state machine |
| `RecoveryDecisionEngine.kt` | Recovery logic |
| `BillingCapabilityMatrix.kt` | Billing configuration |
| `TransportBillingProfile.kt` | Transport foundation |
| `HistoryWindowingStrategy.kt` | Performance strategy |
| `OperationalMemorySnapshot.kt` | Memory normalization |
| `OperationalFatigueProfile.kt` | Fatigue system |
| `OperationalSafetyScenarios.kt` | Test scenarios |

---

*Last Updated: May 2026*
*Version: Platform Stabilization Pass*

