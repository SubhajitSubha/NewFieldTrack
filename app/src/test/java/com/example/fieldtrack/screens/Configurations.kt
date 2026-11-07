package com.example.fieldtrack.screens

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The configurations every screen is rendered in. Phone sizes are a common 6.1"
 * handset; the tablet is a 10" one; landscape is the phone turned sideways, which
 * is how a phone sits in a tractor cab mount.
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*screens.*'
 * then look in app/build/screens/.
 */

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class PhonePortraitScreens : ScreenMatrix("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-night-xxhdpi")
class PhoneDarkScreens : ScreenMatrix("phone-dark")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 350dp tall, not the 411 the glass is: Robolectric draws no status or gesture bar,
// and on a real phone on its side those take about sixty of them. Rendered at the
// full height, the side rail looked fine here while its last tab fell off a real one.
@Config(sdk = [34], qualifiers = "w891dp-h350dp-land-xxhdpi")
class PhoneLandscapeScreens : ScreenMatrix("phone-land")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletScreens : ScreenMatrix("tablet")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-port-night-xhdpi")
class TabletPortraitDarkScreens : ScreenMatrix("tablet-port-dark")

/** "Bigger text" switched on in Display, on an ordinary phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class PhoneBigTextScreens : ScreenMatrix("phone-big", biggerText = true)

/**
 * Right to left: the whole layout mirrors, directional icons turn, numbers stay as they are.
 * Arabic is the longest check of the layout work, so it is rendered like every other size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rEG-w411dp-h891dp-port-xxhdpi")
class PhoneArabicScreens : ScreenMatrix("phone-ar")

/** A language with a long word for most things, with bigger text on top: where a layout gives way first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "hi-rIN-w411dp-h891dp-port-xxhdpi")
class PhoneHindiBigTextScreens : ScreenMatrix("phone-hi-big", biggerText = true)

/** French, the longest of the Latin-script languages in the first wave. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fr-rFR-w411dp-h891dp-port-xxhdpi")
class PhoneFrenchScreens : ScreenMatrix("phone-fr")
