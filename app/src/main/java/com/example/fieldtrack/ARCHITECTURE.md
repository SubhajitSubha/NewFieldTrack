# Architecture Guide — System Extraction

## Dependency Layering

```
┌──────────────────────────────────────────────────────────────┐
│                         UI LAYER                              │
│  NewJobScreen, HomeScreen, SummaryScreen, CustomerScreen     │
│  Composables, Design Tokens, Navigation                      │
├──────────────────────────────────────────────────────────────┤
│                      VIEWMODEL LAYER                          │
│  NewJobViewModel (orchestration)                             │
│  HomeViewModel, SummaryViewModel, CustomerDetailsViewModel   │
├──────────────────────────────────────────────────────────────┤
│                      DOMAIN LAYER                             │
│  domain/session/  — TimerController, ValidationController,   │
│                     PersistenceController, StopConfirmController│
│  domain/job/      — JobPhase, JobConstants, JobCapability     │
│  domain/machine/  — MachineCapabilityMatcher                 │
│  domain/event/    — WorkEvent, WorkEventBus                  │
│  domain/error/    — OperationalFailure, RecoveryMetadata     │
│  domain/observability/ — FieldTrackLogger, MetricsCollector  │
├──────────────────────────────────────────────────────────────┤
│                    REPOSITORY LAYER                           │
│  JobRepository, CustomerRepository, MachineRepository        │
│  ActiveJobsRepository, ActiveJobRepository, PaymentRepository│
├──────────────────────────────────────────────────────────────┤
│                      DATA LAYER                               │
│  Room Database, DAOs, Entities                               │
│  SharedPreferences (RatePreferences, WorkPatternPreferences) │
└──────────────────────────────────────────────────────────────┘
```

## Dependency Rules

| Layer | Can depend on | Cannot depend on |
|-------|--------------|-----------------|
| UI | ViewModel, Domain, Theme | Repository, Data |
| ViewModel | Domain, Repository | UI, Data (entities OK via repo) |
| Domain | Nothing (pure Kotlin) | UI, ViewModel, Repository, Data |
| Repository | Data (DAOs, entities) | UI, ViewModel, Domain |
| Data | Nothing (Room/SQLite) | Everything above |

**Exception:** Repositories may use Domain constants (e.g., `JobConstants`).

---

## ViewModel Decomposition Status

`NewJobViewModel` (~3400 lines) is the orchestration hub.
Extracted controllers handle specific concerns:

| Controller | File | Responsibility | Status |
|-----------|------|----------------|--------|
| `TimerController` | `domain/session/TimerController.kt` | Timer lifecycle, ticking, elapsed | ✅ Created |
| `ValidationController` | `domain/session/ValidationController.kt` | Input sanitization, bounds | ✅ Created |
| `PersistenceController` | `domain/session/PersistenceController.kt` | Throttle, version, gating | ✅ Created |
| `StopConfirmController` | `domain/session/StopConfirmController.kt` | Two-tap stop pattern | ✅ Created |
| `WorkspaceController` | (future) | Job switching, multi-job | Planned |
| `RecoveryController` | (future) | Process death restore | Planned |
| `CompletionController` | (future) | Save flow, summary creation | Planned |

**Migration path:**
1. Controllers are created alongside ViewModel (parallel)
2. ViewModel gradually delegates to controllers
3. Eventually ViewModel becomes thin orchestration layer
4. Controllers are independently testable

---

## Terminology Standard (PART 14)

### Canonical Terms

| Concept | Canonical Term | Avoid |
|---------|---------------|-------|
| Work in progress | **work session** | "job" (= saved only) |
| Saved work record | **job** | "session", "record" |
| Timer active | **running** | "active", "started", "ticking" |
| Timer suspended | **paused** | "stopped" (= completed) |
| Work done | **completed** | "stopped", "finished", "done" |
| Saved to DB | **saved** | "finalized", "committed" |
| Fresh setup | **draft** | "new", "blank", "empty" |
| Back to setup | **discarded** | "cancelled", "deleted" |
| Work type | **capability** | "template", "job type" |
| Pricing method | **billing mode** | "pricing mode" (legacy) |
| Group of active work | **workspace** | "context", "session group" |
| Operator action | **tap** | "click", "press" |
| Database write | **persist** | "save" (= final save only) |
| Auto-background-save | **heartbeat** | "auto-save", "sync" |

### Status Labels (user-facing)

| State | Label | Emoji |
|-------|-------|-------|
| Timer running | "Running" | 🟢 |
| Timer paused | "Paused" | ⏸ |
| Timer stopped | "Completed" | ✔ |
| Trips counted | "N trips" | — |
| No work yet | "Ready" | — |
| Stale (>24h) | "Stale" | ⚠ |

---

## Module Extraction Boundaries (PART 13)

When the app grows, these are the clean extraction points:

| Future Module | Current Package | Extraction Surface |
|--------------|----------------|-------------------|
| `:feature:invoice` | `domain/invoice/` | `CompletedWorkAggregate`, `DocumentRenderer` |
| `:feature:sync` | `domain/sync/` | `SyncContract`, `WorkEventObserver` |
| `:feature:analytics` | `domain/observability/` | `MetricsCollector`, `WorkEventBus` |
| `:feature:staff` | (new) | Entity `operatorId` fields |
| `:feature:reports` | `util/ExportUtils`, `PdfGenerator` | `DocumentRenderer` |
| `:core:domain` | `domain/` | All domain classes (pure Kotlin) |
| `:core:data` | `data/` | Room, DAOs, Repositories |
| `:core:ui` | `ui/theme/` | Design tokens, shared components |

**Rule:** Each module depends only downward (`:feature` → `:core`).

---

## File Organization

```
domain/
├── job/              — JobPhase, JobConstants, JobCapability, BillingMode
├── session/          — TimerController, ValidationController, etc.
├── machine/          — MachineCapabilityMatcher
├── event/            — WorkEvent, WorkEventBus
├── error/            — OperationalFailure, RecoveryMetadata
├── observability/    — FieldTrackLogger, MetricsCollector
├── sync/             — SyncContract
├── invoice/          — InvoiceBoundary
└── DomainBoundary.kt — Architecture overview

ui/
├── theme/            — DesignTokens, Color, Theme
├── components/       — Shared composables
├── screens/
│   ├── newjob/       — Work session screen
│   ├── home/         — Dashboard
│   ├── summary/      — History
│   └── customer/     — Customer details
└── navigation/       — NavGraph, routes

data/
├── local/
│   ├── database/     — FieldFlowDatabase, migrations
│   ├── dao/          — All DAOs
│   └── entity/       — All Room entities
├── model/            — Non-entity data models
└── repository/       — All repositories
```

