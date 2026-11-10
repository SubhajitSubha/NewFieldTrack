package com.example.fieldtrack.data.measure

import android.content.Context
import com.example.fieldtrack.data.db.MeasureUnit

/**
 * What a measuring walk needs to remember on this phone.
 *
 * The walk so far, so that a phone which closes the app in the middle of a field
 * (a call, low memory) does not lose a quarter of an hour of walking; and what a
 * bigha or a katha is where this business is, asked once. Per phone and not sent
 * anywhere: neither is the business's record, and the second is one person's word
 * about their own district.
 */
class WalkStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("field_walk", Context.MODE_PRIVATE)

    fun track(orgId: String): List<Point> =
        prefs.getString("track.$orgId", null)?.split(';')?.mapNotNull { row ->
            row.split(',').takeIf { it.size == 3 }?.let { (lat, lon, at) ->
                runCatching { Point(lat.toDouble(), lon.toDouble(), at.toLong()) }.getOrNull()
            }
        }.orEmpty()

    fun saveTrack(orgId: String, points: List<Point>) {
        val text = points.joinToString(";") { "${it.latitude},${it.longitude},${it.atMillis}" }
        prefs.edit().putString("track.$orgId", text).apply()
    }

    fun clearTrack(orgId: String) = prefs.edit().remove("track.$orgId").apply()

    fun perAcre(orgId: String, unit: MeasureUnit): Double? =
        prefs.getString("perAcre.$orgId.${unit.name}", null)?.toDoubleOrNull()?.takeIf(Land::plausiblePerAcre)

    fun setPerAcre(orgId: String, unit: MeasureUnit, value: Double) =
        prefs.edit().putString("perAcre.$orgId.${unit.name}", value.toString()).apply()
}
