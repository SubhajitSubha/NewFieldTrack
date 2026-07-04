package com.example.fieldtrack.core.debug

/**
 * R5 — Local-First Pilot Mode.
 *
 * Single compile-time flag controlling whether the app behaves like a
 * brand-new farmer install (no Firestore pull/push, just local Room +
 * user-driven empty states) or like normal production/dev (full
 * Firestore sync).
 *
 * Reversibility guarantee:
 *  - Flip [ENABLED] to `false` and rebuild → production behavior is
 *    restored byte-for-byte. No migration, no cleanup step, no data
 *    transformation, no repository rewrite. The flag is the only
 *    control surface.
 *
 * Scope of the pilot:
 *  - Intercepts ONLY [com.example.fieldtrack.data.repository.FirestoreRepository].
 *  - Local Room storage continues to work normally — anything the user
 *    creates inside the app is persisted locally and visible immediately.
 *  - Sync orchestration (workers, queues, replay) is NOT modified. The
 *    sync layer continues to call repository methods, which simply return
 *    a "no-op success" while the flag is on.
 *
 * STRICT RULES enforced by this file:
 *  - No dynamic toggle. Compile-time only.
 *  - No settings UI to flip it.
 *  - No fake / seeded business data is ever inserted.
 *
 * TO DISABLE: change `ENABLED` to `false`, rebuild. Nothing else.
 */
object LocalPilotMode {
    /**
     * When `true`, all Firestore reads return empty results and all
     * Firestore writes short-circuit to [Result.success]. When `false`,
     * the repository behaves exactly as before.
     */
    const val ENABLED: Boolean = true

    /** Logcat tag used by every pilot-mode short-circuit log line. */
    const val TAG: String = "LOCAL_PILOT"
}

