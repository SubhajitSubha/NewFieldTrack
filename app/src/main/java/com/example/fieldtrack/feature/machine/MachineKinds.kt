package com.example.fieldtrack.feature.machine

import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.MachineKind

/**
 * The machine types in the order a person looks for them: what pulls and drives first,
 * the tools it pulls next, then the trailers and the rest. "Other" is last, as in any list.
 */
val MachineKinds: List<MachineKind> = listOf(
    MachineKind.Tractor, MachineKind.Harvester, MachineKind.PowerTiller,
    MachineKind.Rotavator, MachineKind.Cultivator, MachineKind.Plough, MachineKind.Harrow,
    MachineKind.Leveller, MachineKind.Seeder, MachineKind.Transplanter, MachineKind.Ridger,
    MachineKind.Mulcher, MachineKind.Sprayer, MachineKind.DroneSprayer, MachineKind.Baler,
    MachineKind.Thresher, MachineKind.SugarcaneHarvester, MachineKind.PotatoDigger,
    MachineKind.Trolley, MachineKind.GrainCart, MachineKind.WaterTanker, MachineKind.Pump,
    MachineKind.Excavator, MachineKind.Other,
)

/**
 * An example of how somebody would name this kind, so the box does not say "Mahindra 575"
 * to a person adding a pump. Models and numbers only, so the same hint reads right in
 * every language.
 */
fun MachineKind.nameHintRes(): Int = when (this) {
    MachineKind.Tractor, MachineKind.PowerTiller, MachineKind.Other -> R.string.machines_name_hint
    MachineKind.Harvester, MachineKind.SugarcaneHarvester, MachineKind.PotatoDigger ->
        R.string.machines_name_hint_harvester
    MachineKind.Rotavator, MachineKind.Cultivator, MachineKind.Plough, MachineKind.Harrow,
    MachineKind.Leveller, MachineKind.Seeder, MachineKind.Transplanter, MachineKind.Ridger,
    MachineKind.Mulcher, MachineKind.Baler, MachineKind.Thresher ->
        R.string.machines_name_hint_tool
    MachineKind.Sprayer, MachineKind.DroneSprayer -> R.string.machines_name_hint_sprayer
    MachineKind.Trolley, MachineKind.GrainCart, MachineKind.WaterTanker ->
        R.string.machines_name_hint_trailer
    MachineKind.Pump -> R.string.machines_name_hint_pump
    MachineKind.Excavator -> R.string.machines_name_hint_excavator
}

/**
 * Whether this kind normally has a number plate. A rotavator or a pump does not, and asking
 * for one is a question the person has to skip. A machine that already has a plate keeps
 * its box, so choosing a different type never hides what was typed.
 */
val MachineKind.hasPlate: Boolean
    get() = when (this) {
        MachineKind.Rotavator, MachineKind.Cultivator, MachineKind.Plough, MachineKind.Harrow,
        MachineKind.Leveller, MachineKind.Seeder, MachineKind.Ridger, MachineKind.Mulcher,
        MachineKind.Baler, MachineKind.PotatoDigger, MachineKind.Pump -> false
        else -> true
    }
