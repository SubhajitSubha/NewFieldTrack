# Platform Readiness — Architecture Guide

## Overview

FieldTrack is structured as a **platform foundation** — not a monolithic single-purpose app.
Future capabilities (sync, invoices, analytics, multi-user) can be added WITHOUT modifying
the core operational tracking logic.

---

## Domain Packages

| Package | Status | Responsibility |
|---------|--------|----------------|
| `domain/job/` | Active | Job lifecycle, billing, phases, constants |
| `domain/session/` | Prepared | Session abstraction, vocabulary, controller interfaces |
| `domain/machine/` | Active | Machine capability matching |
| `domain/event/` | Prepared | Event bus, work events, observers |
| `domain/observability/` | Prepared | Structured logging, metrics hooks |
| `domain/error/` | Prepared | Failure classification, recovery metadata |
| `domain/sync/` | Prepared | Sync contracts, conflict strategy, state enums |
| `domain/invoice/` | Prepared | Invoice boundary, work aggregates, snapshots |

---

## Migration Safety Rules

### Adding new columns:
1. ALWAYS use `nullable` type with `DEFAULT NULL`
2. ALWAYS increment database version
3. ALWAYS add a `Migration` object in `FieldFlowDatabase`
4. NEVER change existing column types
5. NEVER rename existing columns

### Entity modification checklist:
- [ ] New field is nullable OR has a safe default
- [ ] Migration SQL added to `FieldFlowDatabase`
- [ ] Database version bumped
- [ ] All DAOs that INSERT the entity updated (if non-nullable)
- [ ] `isSameBusinessData()` updated if field is business-relevant

### Schema version tracking:
- `JobEntity.schemaVersion` — tracks business data format changes
- `ActiveJobsEntity.snapshotVersion` — tracks write count for conflict detection
- Database version (currently 30) — tracks table schema changes

---

## Event-Driven Extension Points

To add new behavior (analytics, sync, audit):

```kotlin
// 1. Implement the observer
class MyAnalyticsObserver : WorkEventObserver {
    override fun onEvent(event: WorkEvent) {
        when (event) {
            is WorkEvent.WorkCompleted -> trackCompletion(event)
            is WorkEvent.PaymentRecorded -> trackPayment(event)
            else -> { /* ignore */ }
        }
    }
}

// 2. Register in Application.onCreate()
WorkEventBus.register(MyAnalyticsObserver())
```

Events are fire-and-forget — operational code is NEVER blocked by observers.

---

## Metrics Extension Points

To add metrics collection:

```kotlin
// 1. Implement the collector
class FirebaseMetrics : MetricsCollector {
    override fun record(metric: OperationalMetric) {
        when (metric) {
            is OperationalMetric.SessionCompleted -> {
                Firebase.analytics.logEvent("work_completed") { ... }
            }
        }
    }
}

// 2. Replace the NOOP collector in Application
val metrics: MetricsCollector = FirebaseMetrics()
```

---

## Sync Readiness

### Already prepared:
- `JobEntity` has `syncStatus`, `retryCount`, `lastSyncAttempt`, `updatedAt`, `updatedBy`
- `CustomerEntity` has sync fields
- `JobDao` has `getPendingJobs()`, `updateSyncStatus()`, `insertJobFromRemote()`
- `SyncContract` defines immutable vs mutable entity classification
- `SyncState` enum defines sync lifecycle

### To implement sync:
1. Create `SyncService` implementing `WorkEventObserver`
2. Listen for `WorkEvent.WorkCompleted` → queue for sync
3. Use `JobDao.getPendingJobs()` for retry queue
4. Use `SyncContract.ConflictStrategy` for conflict resolution
5. Immutable entities (jobs, payments) → simple push, no conflicts
6. Mutable entities (customers, machines) → use `localRevision` comparison

---

## Invoice Readiness

### Already prepared:
- `CompletedWorkAggregate` — aggregates completed jobs for invoicing
- `CustomerContactSnapshot` — immutable customer info at time of work
- `WorkLineItem` — single line item on an invoice
- `DocumentRenderer<T>` — interface for PDF/image rendering

### To implement invoices:
1. Query `JobDao.getJobsByCustomerIdOnce()` for a date range
2. Map to `CompletedWorkAggregate` using customer snapshot
3. Render via `DocumentRenderer<Bitmap>` or `DocumentRenderer<PdfDocument>`
4. NEVER recompute totals — use `JobEntity.totalAmount` as-is

---

## Multi-User Readiness

### Future entity fields (add when implementing):
- `operatorId: String?` — who performed the work
- `createdBy: String?` — who created the record
- `assignedTo: String?` — who the work is assigned to
- `deviceId: String?` — which device created/modified

### Rules:
- All new fields are nullable (backward-compatible)
- Existing single-user data has `null` = "default operator"
- No UI changes until role system is implemented

---

## Data Integrity Guarantees

| Data Type | Mutability | Guarantee |
|-----------|-----------|-----------|
| `JobEntity` (saved) | IMMUTABLE | `totalAmount` never changes after save |
| `PaymentEntity` | IMMUTABLE | Amount/date never change |
| `WorkCompletionSummary` | IMMUTABLE | Snapshot at save time |
| `CustomerContactSnapshot` | IMMUTABLE | Name/phone at work time |
| `ActiveJobsEntity` | MUTABLE | Can change while in progress |
| `CustomerEntity` | MUTABLE | Name/phone can be edited |
| `MachineEntity` | MUTABLE | Can be renamed/deleted |

### History accuracy rules:
- `JobEntity.machineName` = snapshot at save time (survives machine rename)
- `JobEntity.customerId` = link to customer (for live queries)
- `WorkLineItem.machineName` = snapshot (for invoice rendering)
- Deleted machines/customers NEVER corrupt historical job records

---

## Canonical Vocabulary

| Concept | Term | NOT |
|---------|------|-----|
| Active work in progress | "work session" | "job" (reserved for completed) |
| Completed & saved work | "job" | "session" |
| Timer running | "Running" | "Active", "Started" |
| Timer paused | "Paused" | "Stopped", "Halted" |
| Work done, ready to save | "Completed" | "Stopped", "Done" |
| Saved to database | "Saved" | "Finalized", "Committed" |
| Customer | "Customer" | "Farmer", "Client" |
| Machine | "Machine" | "Vehicle", "Equipment" |
| Work type | "Work type" / "Capability" | "Template", "Job type" |

See `SessionVocabulary` in `domain/session/SessionState.kt` for constants.

---

## Performance at Scale

### Current optimizations:
- `WhileSubscribed(5000)` on all StateFlows (stops collection when UI invisible)
- Timer tick throttled (cost display every 5s)
- Persist throttled (500ms minimum interval)
- Auto-save debounced (400ms)
- Customer summaries via GROUP BY SQL (no N+1)

### For 1000+ jobs:
- Use `getJobsPaginated(limit, offset)` instead of `getAllJobs()`
- Add database indices: `@Entity(indices = [Index("customerId"), Index("createdAt")])`
- Use `getTotalJobCount()` for pagination headers
- Consider Paging 3 library for lazy list rendering

---

## ViewModel Decomposition Path

`NewJobViewModel` (~3400 lines) has clear internal boundaries:

| Responsibility | Future Class | Interface |
|---------------|-------------|-----------|
| Timer control | `TimerDelegate` | `SessionController` |
| Segment management | `SegmentDelegate` | `WorkAggregator` |
| Persistence | `PersistenceDelegate` | (internal) |
| Machine/Customer selection | `ConfigurationDelegate` | (internal) |
| Multi-job switching | `WorkspaceDelegate` | (internal) |

Decomposition can happen incrementally — each delegate extracts behind its interface.
No refactor required now — interfaces define the cut points.

