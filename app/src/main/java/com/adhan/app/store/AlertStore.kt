package com.adhan.app.store

import com.adhan.app.domain.Alert

/**
 * Persistence for alerts. Phase 1 stores a small list in preferences.
 * A later store can replace this without changing callers.
 */
interface AlertStore {
    fun get(id: String): Alert?

    fun put(alert: Alert)

    fun remove(id: String)

    fun all(): List<Alert>
}
