package com.example.fieldtrack.data.region

/**
 * What the business in front of the person calls its own land unit, for every screen that names a unit.
 *
 * Set with the business, as the money and the clock are, so a switch of business renames it everywhere at once.
 * Null where the business has not named one.
 */
object OwnLandUnit {
    @Volatile
    var name: String? = null
}
