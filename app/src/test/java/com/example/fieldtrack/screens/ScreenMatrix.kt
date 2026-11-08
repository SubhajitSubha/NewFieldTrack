package com.example.fieldtrack.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import com.example.fieldtrack.feature.fields.GpsAccess
import com.example.fieldtrack.feature.fields.Signal
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.data.db.BreakdownCategory
import com.example.fieldtrack.data.db.JobCostKind
import com.example.fieldtrack.data.db.MachineKind
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.db.PaymentMethod
import com.example.fieldtrack.data.db.PayoutCovers
import com.example.fieldtrack.data.db.entity.MembershipEntity
import com.example.fieldtrack.data.db.entity.OrgEntity
import com.example.fieldtrack.data.db.entity.membershipId
import com.example.fieldtrack.data.session.Capabilities
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.FieldTrackApp
import com.example.fieldtrack.feature.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Test
import java.io.File

/**
 * Every screen, rendered in one configuration.
 *
 * The real app — real view models over a real database seeded with a believable
 * business — not mock-ups of it, so what is checked is what a farmer would see.
 * Each subclass pins a size, orientation or theme with Robolectric qualifiers; the
 * images land in `app/build/screens/<config>/` for review, two per screen: as it
 * opens, and scrolled to the end.
 *
 * This is the check behind the rule that a UI change covers every screen in every
 * configuration. A screen that is added and not listed here is a screen nobody
 * looked at sideways or in the dark.
 */
abstract class ScreenMatrix(
    private val config: String,
    private val biggerText: Boolean = false,
) {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<FieldFlowApplication>()
    private val owner = Capabilities.ofOwner()

    private lateinit var nav: NavHostController

    // ------------------------------------------------------------------ passes

    /** An owner with a crew, on every screen the app has. */
    @Test fun owner() {
        val ids = runBlocking { seedBusiness(business = true) }
        start()
        routes(ids).forEach { (name, route) -> shoot("owner", name, route) }
    }

    /** What stands in front of the app: the lock, the update notice, and the page that stops an old version. */
    @Test fun gates() {
        var page by mutableStateOf(0)
        compose.setContent {
            com.example.fieldtrack.feature.AppTheme {
                when (page) {
                    0 -> com.example.fieldtrack.feature.lock.LockScreen(onUnlock = {})
                    1 -> com.example.fieldtrack.feature.update.UpdateRequiredScreen(onUpdate = {})
                    2 -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        com.example.fieldtrack.feature.update.UpdateBar(onUpdate = {}, onLater = {})
                    }
                    3 -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        com.example.fieldtrack.feature.help.PracticeBar(onLeave = {})
                    }
                    else -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        com.example.fieldtrack.feature.help.RatingBar(
                            step = com.example.fieldtrack.data.feedback.RatingStep.Ask, canWrite = true,
                            onAnswer = {}, onLater = {}, onRate = {}, onWrite = {}, onNo = {},
                        )
                    }
                }
            }
        }
        settle()
        capture("gates", "lock")
        compose.runOnUiThread { page = 1 }
        settle()
        capture("gates", "update-required")
        compose.runOnUiThread { page = 2 }
        settle()
        capture("gates", "update-bar")
        compose.runOnUiThread { page = 3 }
        settle()
        capture("gates", "practice-bar")
        compose.runOnUiThread { page = 4 }
        settle()
        capture("gates", "rating-bar")
    }

    /** Setting up a job on a machine that has history: the cost hint under the rate. */
    @Test fun startHint() {
        runBlocking { seedBusiness(business = true) }
        start()
        compose.runOnUiThread { nav.navigate(Routes.START) }
        settle()
        // Scrolled to before each tap: on a phone on its side the row is below the
        // fold, and a node that is not on screen cannot be clicked.
        fun tap(text: String, substring: Boolean = false) {
            runCatching { compose.onNode(hasText(text, substring = substring)).performScrollTo() }
            runCatching { compose.onNode(hasText(text, substring = substring)).performClick() }
            settle()
        }
        // Searched for rather than scrolled to: on a phone on its side the farmer is
        // below a list that has no room for him.
        runCatching { compose.onNode(hasSetTextAction()).performTextInput("Ramesh") }
        settle()
        tap("Ramesh Singh")
        tap("Swaraj 855", substring = true)
        tap("Next")
        tap("Next")
        capture("sheets", "start-hint-no-rate")
        // The rate arrives filled in from the last time (₹1,000); typing replaces it.
        runCatching { compose.onNode(hasSetTextAction()).performTextReplacement("1500") }
        settle()
        capture("sheets", "start-hint-profit")
        runCatching { compose.onNode(hasSetTextAction()).performTextReplacement("200") }
        settle()
        capture("sheets", "start-hint-loss")
    }

    /** The price calculator, through the questions a contractor would put to it. */
    @Test fun price() {
        runBlocking { seedBusiness(business = true) }
        start()
        compose.runOnUiThread { nav.navigate(Routes.PRICE) }
        settle()
        fun tap(text: String) {
            runCatching { compose.onNode(hasText(text)).performScrollTo() }
            runCatching { compose.onNode(hasText(text)).performClick() }
            settle()
        }
        // The fields in the order they sit: rate first, then the quantity (or hours, minutes).
        fun type(index: Int, text: String) {
            runCatching { compose.onAllNodes(hasSetTextAction())[index].performTextReplacement(text) }
            settle()
        }
        // Rate filled in from the rate card; nothing asked of it yet.
        capture("price", "price-start")
        type(1, "4")
        capture("price", "price-bigha")
        scrollEverythingToEnd(); settle()
        capture("price", "price-bigha.end")
        tap("Per hour")
        capture("price", "price-hour-empty")
        type(0, "900"); type(1, "2"); type(2, "30")
        capture("price", "price-hour")
        tap("Hauling")
        type(1, "5")
        capture("price", "price-trips")
        tap("Other")
        tap("Fixed price")
        capture("price", "price-fixed")
    }

    /** A business with three named seasons: where they show, and the editor with mistakes in it. */
    @Test fun seasons() {
        runBlocking {
            seedBusiness(business = true)
            val org = app.db.orgDao().findOrg(ORG)!!
            val south = com.example.fieldtrack.data.season.SeasonCalendar.of(
                listOf(
                    com.example.fieldtrack.data.season.SeasonStart(4, 1, "Virippu"),
                    com.example.fieldtrack.data.season.SeasonStart(8, 15, "Mundakan"),
                    com.example.fieldtrack.data.season.SeasonStart(12, 1, "Puncha"),
                )
            )!!
            app.db.orgDao().upsertOrg(org.copy(seasons = south.encode()))
        }
        start()
        listOf(
            "home" to Routes.HOME, "season" to Routes.SEASON, "profile" to Routes.PROFILE,
            "seasons-edit" to Routes.SEASONS_EDIT,
        ).forEach { (name, route) -> shoot("seasons", name, route) }

        compose.runOnUiThread { nav.navigate(Routes.SEASON) }
        settle()
        runCatching { compose.onNode(hasText("Last season")).performClick() }
        settle()
        capture("seasons", "season-last")

        // The editor, with two mistakes in turn. Fields in the order they sit: each
        // season's name, then its day.
        compose.runOnUiThread { nav.navigate(Routes.SEASONS_EDIT) }
        settle()
        fun typeIn(index: Int, text: String) {
            runCatching { compose.onAllNodes(hasSetTextAction())[index].performTextReplacement(text) }
            settle()
        }
        fun chip(text: String, index: Int) {
            runCatching { compose.onAllNodes(hasText(text))[index].performScrollTo() }
            runCatching { compose.onAllNodes(hasText(text))[index].performClick() }
            settle()
        }
        chip("Apr", 1)           // the second season, moved onto the first one's month...
        typeIn(3, "1")           // ...and day: two seasons on one day
        capture("seasons", "seasons-edit-duplicate")
        chip("Feb", 2)           // the third season in February...
        typeIn(5, "30")          // ...on a day February does not have
        capture("seasons", "seasons-edit-bad-day")
        scrollEverythingToEnd(); settle()
        capture("seasons", "seasons-edit-bad-day.end")
    }

    /** Measuring a plot by walking, in every state the screen can be in: no GPS, a walk, a result. */
    @Test fun measure() {
        val lat0 = 30.9; val lon0 = 75.85
        val metreLat = 1.0 / 111_194.9266
        val metreLon = metreLat / Math.cos(Math.toRadians(lat0))
        fun walk(vararg m: Pair<Int, Int>) = m.mapIndexed { i, (e, n) ->
            com.example.fieldtrack.data.measure.Point(lat0 + n * metreLat, lon0 + e * metreLon, i * 10_000L)
        }
        val rectangle = walk(0 to 0, 60 to 0, 120 to 0, 120 to 45, 60 to 45, 0 to 45)
        val ready = com.example.fieldtrack.feature.fields.MeasureState(
            loaded = true, signal = Signal.Good, accuracyMetres = 6,
            here = rectangle.first(),
        )
        var shown by mutableStateOf(ready)
        var refused by mutableStateOf(false)
        val none = com.example.fieldtrack.feature.fields.MeasureActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            com.example.fieldtrack.feature.AppTheme {
                androidx.compose.foundation.layout.BoxWithConstraints(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.example.fieldtrack.designsystem.layout.LocalWidthClass provides
                            com.example.fieldtrack.designsystem.layout.WidthClass.of(maxWidth)
                    ) { com.example.fieldtrack.feature.fields.MeasureScreen(shown, none, permanentlyRefused = refused) }
                }
            }
        }
        fun show(name: String, state: com.example.fieldtrack.feature.fields.MeasureState, permanentlyRefused: Boolean = false) {
            shown = state; refused = permanentlyRefused; settle(); capture("measure", name)
        }
        show("setup-permission", ready.copy(access = GpsAccess.NotPermitted))
        show("setup-refused", ready.copy(access = GpsAccess.NotPermitted), permanentlyRefused = true)
        show("setup-precise", ready.copy(access = GpsAccess.NotPrecise))
        show("setup-off", ready.copy(access = GpsAccess.SwitchedOff))
        show("searching", ready.copy(signal = Signal.None, accuracyMetres = null, here = null))
        show("weak", ready.copy(signal = Signal.Weak, accuracyMetres = 34))
        show("ready", ready)
        show("walking", ready.copy(walking = true, points = rectangle.take(4), here = rectangle[4]))
        show(
            "walking-cut-off",
            ready.copy(walking = true, interrupted = true, signal = Signal.None, points = rectangle.take(4), here = rectangle[3]),
        )
        fun finished(points: List<com.example.fieldtrack.data.measure.Point>, unit: MeasureUnit, perAcre: String = "", interrupted: Boolean = false) =
            ready.copy(
                finished = true, points = points, here = points.last(), unit = unit, perAcreText = perAcre,
                interrupted = interrupted, shape = com.example.fieldtrack.data.measure.Shape(points),
            )
        show("result", finished(rectangle, MeasureUnit.Acre))
        show("result-bigha-unknown", finished(rectangle, MeasureUnit.Bigha))
        show("result-bigha", finished(rectangle, MeasureUnit.Bigha, "1.6"))
        show("result-warnings", finished(walk(0 to 0, 120 to 45, 120 to 0, 0 to 45, 40 to 80), MeasureUnit.Kanal, interrupted = true))
        show("result-too-small", finished(walk(0 to 0, 5 to 0, 5 to 5), MeasureUnit.Acre))
        scrollEverythingToEnd(); settle()
        capture("measure", "result-too-small.end")
    }

    /** Planning a day: the plan, the booking form and every question it can ask, and starting from a booking. */
    @Test fun plan() {
        val ids = runBlocking { seedBusiness(business = true) }
        start()
        fun tap(text: String, index: Int = 0) {
            runCatching { compose.onAllNodes(hasText(text))[index].performScrollTo() }
            runCatching { compose.onAllNodes(hasText(text))[index].performClick() }
            settle()
        }
        fun open(route: String) { compose.runOnUiThread { nav.navigate(route) }; settle() }

        open(Routes.PLAN)
        capture("plan", "today")
        tap("Tomorrow")
        capture("plan", "tomorrow")
        tap("Pick a day")
        capture("plan", "pick-day")
        runCatching { compose.onNode(hasText("Cancel")).performClick() }; settle()

        open(Routes.booking())
        capture("plan", "form-new")
        tap("Choose the farmer")
        capture("plan", "form-farmer")
        // Searched for: on a phone on its side the farmer is below a list with no room for him.
        runCatching { compose.onNode(hasSetTextAction()).performTextInput("Ramesh") }; settle()
        tap("Ramesh Singh")
        tap("Another day")
        capture("plan", "form-day-picker")
        runCatching { compose.onNode(hasText("Cancel")).performClick() }; settle()
        tap("Set a time")
        capture("plan", "form-time")
        runCatching { compose.onNode(hasText("Cancel")).performClick() }; settle()
        // Today morning on the Swaraj with Gurmeet: already booked, twice over.
        tap("Swaraj 855")
        tap("Gurmeet Singh")
        capture("plan", "form-clash")
        scrollEverythingToEnd(); settle()
        capture("plan", "form-clash.end")

        open(Routes.booking(ids.bookingId))
        capture("plan", "form-down")
        tap("Cancel booking")
        capture("plan", "form-cancel-why")
        runCatching { compose.onNode(hasText("Keep booking")).performClick() }; settle()
        open(Routes.booking(ids.missedBookingId))
        capture("plan", "form-missed")
        scrollEverythingToEnd(); settle()
        capture("plan", "form-missed.end")

        // Starting: from a booking, and a customer with a booking asking whether this is it.
        open(Routes.startBooking(ids.missedBookingId))
        capture("plan", "start-from-booking")
        open(Routes.START)
        runCatching { compose.onNode(hasSetTextAction()).performTextInput("Ramesh") }; settle()
        tap("Ramesh Singh")
        capture("plan", "start-asks-booking")

        // The switch that lets somebody plan, on a member's own page.
        open("more/team/${ids.driverMembershipId}")
        tap("Set each one by hand")
        scrollEverythingToEnd(); settle()
        capture("plan", "member-switches")
    }

    /** The Jobs list on "Done today", where the amounts and the loss mark are. */
    @Test fun jobsDone() {
        runBlocking { seedBusiness(business = true) }
        start()
        jobsDone("owner")
    }

    private fun jobsDone(pass: String) {
        compose.runOnUiThread { nav.navigate(Routes.JOBS) { launchSingleTop = true } }
        settle()
        runCatching { compose.onNode(hasText("Done today")).performClick() }
        settle()
        capture(pass, "jobs-done-today")
        runCatching { compose.onNode(hasText("This week")).performClick() }
        settle()
        capture(pass, "jobs-this-week")
    }

    /** What only opens on a tap: the job's action menu, and the overnight-clock question. */
    @Test fun sheets() {
        val ids = runBlocking {
            val ids = seedBusiness(business = true)
            // The driver's clock left running since yesterday morning.
            val yesterday = System.currentTimeMillis() - 30L * 60 * 60 * 1000
            app.db.openHelper.writableDatabase.execSQL(
                "UPDATE jobs SET runningSince = $yesterday, startedAt = $yesterday WHERE id = '${ids.crewJobId}'"
            )
            ids
        }
        start()
        // The owner's home, now with a crew clock left on.
        capture("sheets", "home-clock-left-on")
        compose.runOnUiThread { nav.navigate(Routes.live(ids.crewJobId)) }
        settle()
        capture("sheets", "clock-check")
        compose.runOnUiThread { nav.popBackStack() }
        settle()
        compose.runOnUiThread { nav.navigate(Routes.live(ids.ownJobId)) }
        settle()
        runCatching { compose.onNode(hasText("More")).performClick() }
        settle()
        capture("sheets", "work-menu")
        // With proof of work asked for: the row in the menu, and the sheet behind it.
        runBlocking {
            app.db.orgDao().upsertEntitlement(com.example.fieldtrack.data.db.entity.EntitlementEntity(id = ORG, orgId = ORG, tier = com.example.fieldtrack.data.db.PlanTier.Business, untilDay = java.time.LocalDate.now().toEpochDay() + 30))
            app.sessionStore.setProofOfWork(true)
        }
        compose.runOnUiThread { nav.popBackStack() }
        settle()
        compose.runOnUiThread { nav.navigate(Routes.live(ids.ownJobId)) }
        settle()
        runCatching { compose.onNode(hasText("More")).performClick() }
        settle()
        capture("sheets", "work-menu-proof")
        runCatching { compose.onNode(hasText("Proof of work")).performClick() }
        settle()
        capture("sheets", "proof-sheet")
    }

    /** A business set up this morning: every list empty, every total nought. */
    @Test fun empty() {
        runBlocking { seedSession(ORG, OWNER, MemberRole.Owner, business = true) }
        start()
        listOf(
            "home" to Routes.HOME, "jobs" to Routes.JOBS, "customers" to Routes.CUSTOMERS,
            "fleet" to Routes.FLEET, "machines" to Routes.MACHINES, "money-out" to Routes.MONEY_OUT,
            "season" to Routes.SEASON, "team" to Routes.TEAM, "rates" to Routes.RATES,
            "price" to Routes.PRICE, "seasons-edit" to Routes.SEASONS_EDIT,
            "plan" to Routes.PLAN, "plan-settings" to Routes.PLAN_SETTINGS, "money-settings" to Routes.MONEY_SETTINGS, "settings" to Routes.SETTINGS, "money" to Routes.MONEY, "booking-new" to Routes.booking(),
            "upkeep" to Routes.UPKEEP, "backup" to Routes.BACKUP,
        ).forEach { (name, route) -> shoot("empty", name, route) }
    }

    /** A one-person business: the solo home and its tabs. */
    @Test fun solo() {
        runBlocking { seedBusiness(business = false) }
        start()
        listOf(
            "home" to Routes.HOME, "customers" to Routes.CUSTOMERS, "machines" to Routes.MACHINES,
            "plan" to Routes.PLAN, "booking-new" to Routes.booking(),
        ).forEach { (name, route) -> shoot("solo", name, route) }
    }

    /** A plain driver: no money anywhere, their own work only. */
    @Test fun driver() {
        val ids = runBlocking {
            val ids = seedBusiness(business = true)
            seedSession(ORG, ids.driverPersonId, MemberRole.Operator, business = false)
            ids
        }
        start()
        listOf(
            "home" to Routes.HOME, "my-work" to Routes.JOBS, "machines" to Routes.MACHINES,
            "more" to Routes.MORE, "price" to Routes.PRICE, "profile" to Routes.PROFILE,
            "seasons-edit" to Routes.SEASONS_EDIT, "notices" to Routes.NOTICES, "history" to Routes.MONEY_HISTORY, "my-pay" to Routes.MY_PAY,
            "plan" to Routes.PLAN, "plan-settings" to Routes.PLAN_SETTINGS, "money-settings" to Routes.MONEY_SETTINGS, "settings" to Routes.SETTINGS, "money" to Routes.MONEY, "booking-new" to Routes.booking(),
            "booking" to Routes.booking(ids.bookingId), "start-booking" to Routes.startBooking(ids.missedBookingId),
            "job" to "jobs/${ids.doneJobId}", "live" to Routes.live(ids.crewJobId),
        ).forEach { (name, route) -> shoot("driver", name, route) }
    }

    /** Given the machines but not the money: no rupees anywhere, and no cost screens. */
    @Test fun machinesOnlyManager() {
        val ids = runBlocking {
            val ids = seedBusiness(business = true)
            val now = System.currentTimeMillis()
            app.db.orgDao().upsertMembership(
                MembershipEntity(
                    id = membershipId(ORG, "manager-bittu"), orgId = ORG, personId = "manager-bittu",
                    displayName = "Bittu", role = MemberRole.Operator, canEditMachines = true,
                    joinedAt = now, updatedAt = now,
                )
            )
            seedSession(ORG, "manager-bittu", MemberRole.Operator, business = true)
            ids
        }
        start()
        listOf(
            "home" to Routes.HOME, "jobs" to Routes.JOBS, "fleet" to Routes.FLEET,
            "machine" to "machines/${ids.machineId}", "machine-edit" to "machines/${ids.machineId}/edit",
            "more" to Routes.MORE, "price" to Routes.PRICE, "plan" to Routes.PLAN,
        ).forEach { (name, route) -> shoot("manager-machines", name, route) }
        jobsDone("manager-machines")
    }

    /** Before anybody is signed in, and before they belong to a business. */
    @Test fun signIn() {
        start()
        capture("pre", "sign-in")
    }

    @Test fun joinOrSetUp() {
        prefs().edit().putString("personId", OWNER).commit()
        start()
        capture("pre", "join")
        // No code: on to setting up a business of their own.
        runCatching { compose.onNode(hasText("I do not have a code")).performClick() }
        settle()
        capture("pre", "onboarding")
        // On through the steps, to the one that asks about money and land.
        fun tap(text: String) {
            runCatching { compose.onNode(hasText(text)).performClick() }
            settle()
        }
        tap("Get started"); capture("pre", "onboarding-role")
        tap("Continue")
        runCatching { compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Harpal Singh") }
        settle(); capture("pre", "onboarding-you")
        tap("Continue"); capture("pre", "onboarding-region")
        tap("US Dollar"); capture("pre", "onboarding-currency")
    }

    // --------------------------------------------------------------- mechanics

    @Before fun firebase() {
        // Sign-in builds Firebase objects as the screen opens. These options name no
        // real project and nothing here touches the network; they only let the
        // objects be constructed so the screen can be drawn.
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(
                app,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("AIzaScreenshotsOnly000000000000000000")
                    .setProjectId("fieldtrack-screens")
                    .build(),
            )
        }
    }

    private fun start() {
        if (biggerText) app.displaySettings.setBiggerText(true)
        // The application read the session as it started, before the seed existed.
        runBlocking { app.sessionStore.load() }
        compose.setContent {
            val controller = rememberNavController()
            LaunchedEffect(controller) { nav = controller }
            com.example.fieldtrack.feature.AppTheme { FieldTrackApp(navController = controller) }
        }
        settle()
    }

    private fun shoot(pass: String, name: String, route: String) {
        // "a > b" opens b on top of a, for screens that borrow their parent's state.
        route.split(" > ").forEach { step ->
            compose.runOnUiThread { nav.navigate(step) { launchSingleTop = true } }
            settle()
        }
        capture(pass, name)
        audit(name)
        // Scrolled to the end, so what is below the fold is looked at too. Through
        // each scrollable's own scroll action: a swipe gesture does not fling under
        // Robolectric, so it moved nothing.
        scrollEverythingToEnd()
        settle()
        capture(pass, "$name.end")
        compose.runOnUiThread { nav.popBackStack(Routes.HOME, inclusive = false) }
        settle()
    }

    /**
     * What a screen reader and a thumb need: every control says what it is, and is big
     * enough to hit. Written to `a11y.txt` beside the images; an unlabelled control fails
     * the test, since to a screen reader it is an unmarked button.
     */
    private val unlabelled = mutableListOf<String>()
    private val tooSmall = mutableListOf<String>()

    private fun audit(name: String) {
        val density = compose.density.density
        val min = 48 * density
        val nodes = runCatching {
            compose.onAllNodes(androidx.compose.ui.test.hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
        }.getOrDefault(emptyList())
        nodes.forEach { node ->
            val config = node.config
            val text = config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString(" ") { it.text }
            val description = config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ")
            val label = listOfNotNull(text, description).joinToString(" ").trim()
            val bounds = node.touchBoundsInRoot
            if (bounds.width <= 0f || bounds.height <= 0f) return@forEach
            if (label.isEmpty()) unlabelled += "$name: a control with no label at ${bounds.left.toInt()},${bounds.top.toInt()}"
            if (bounds.width < min - 1 || bounds.height < min - 1) {
                tooSmall += "$name: '${label.take(30)}' is ${(bounds.width / density).toInt()}x${(bounds.height / density).toInt()}dp"
            }
        }
    }

    @org.junit.After fun writeAudit() {
        if (unlabelled.isEmpty() && tooSmall.isEmpty()) return
        // Small ones are reported only: many are cut off by the edge of the window while scrolled.
        File("build/screens/$config/a11y.txt").apply { parentFile?.mkdirs() }
            .writeText("NO LABEL\n" + unlabelled.distinct().joinToString("\n") + "\n\nTOO SMALL\n" + tooSmall.distinct().joinToString("\n"))
        org.junit.Assert.assertTrue("Controls a screen reader would call 'button' with no name: ${unlabelled.distinct()}", unlabelled.isEmpty())
    }

    private fun scrollEverythingToEnd() {
        val count = runCatching {
            compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        }.getOrDefault(0)
        for (i in 0 until count) {
            runCatching {
                compose.onAllNodes(hasScrollAction())[i]
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            }
        }
    }

    /** Room answers on its own threads; give them a moment, then let Compose catch up. */
    private fun settle() {
        repeat(8) {
            compose.waitForIdle()
            Thread.sleep(60)
        }
        compose.waitForIdle()
    }

    private fun capture(pass: String, name: String) {
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        // Sheets and dialogs live in windows of their own, drawn over the screen in
        // the order they were opened. Without these the capture showed the screen
        // underneath and none of what the person was actually looking at.
        otherWindows(view).forEach { window ->
            canvas.save()
            val x = ((view.width - window.width) / 2f).coerceAtLeast(0f)
            val y = (view.height - window.height).toFloat().coerceAtLeast(0f)
            canvas.translate(x, y)
            window.draw(canvas)
            canvas.restore()
        }
        val out = File("build/screens/$config/$pass-$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun otherWindows(main: android.view.View): List<android.view.View> = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        (field.get(instance) as List<android.view.View>)
            .filter { it !== main && it.isShown && it.width > 0 && it.height > 0 }
    }.getOrDefault(emptyList())

    private fun prefs() = app.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE)

    // -------------------------------------------------------------------- data

    data class Ids(
        val customerId: String,
        val fieldId: String,
        val machineId: String,
        val doneJobId: String,
        val costId: String,
        val lossJobId: String,
        val crewJobId: String,
        val ownJobId: String,
        val driverMembershipId: String,
        val driverPersonId: String,
        /** Tomorrow afternoon on the broken harvester. */
        val bookingId: String,
        /** Promised for yesterday and never started. */
        val missedBookingId: String,
        /** Cancelled for tomorrow because of rain. */
        val cancelledBookingId: String,
    )

    private suspend fun seedSession(orgId: String, personId: String, role: MemberRole, business: Boolean) {
        val now = System.currentTimeMillis()
        val db = app.db
        if (db.orgDao().observeOrg(orgId).first() == null) {
            db.orgDao().upsertOrg(
                OrgEntity(
                    id = orgId, name = "Harpal Agro Works", phone = "9000000001",
                    addressLine = "Village Dosimana, Ludhiana", defaultLandUnit = MeasureUnit.Bigha,
                    createdAt = now, updatedAt = now,
                )
            )
        }
        if (db.orgDao().membership(orgId, personId) == null) {
            db.orgDao().upsertMembership(
                MembershipEntity(
                    id = membershipId(orgId, personId), orgId = orgId, personId = personId,
                    displayName = "Harpal Singh", role = role, joinedAt = now, updatedAt = now,
                )
            )
        }
        prefs().edit()
            .putString("personId", personId)
            .putString("orgId", orgId)
            .putBoolean("isBusiness", business)
            .commit()
    }

    private suspend fun seedBusiness(business: Boolean): Ids {
        seedSession(ORG, OWNER, MemberRole.Owner, business)
        // A business with a crew, so on the paid plan: the screens are checked as they are with a team.
        app.db.orgDao().upsertEntitlement(
            com.example.fieldtrack.data.db.entity.EntitlementEntity(
                id = ORG, orgId = ORG, tier = com.example.fieldtrack.data.db.PlanTier.Business,
                untilDay = java.time.LocalDate.now().toEpochDay() + 200,
            )
        )
        val dir = app.directoryRepository
        val work = app.workRepository
        val ledger = app.ledgerRepository
        val team = app.teamRepository
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()

        val ramesh = dir.addCustomer(ORG, "Ramesh Singh", "9876543210", "Dosimana")
        val harjit = dir.addCustomer(ORG, "Harjit Kaur", "9812345678", "Rampur", openingBalanceMinor = 2_500)
        dir.addCustomer(ORG, "Balbir Sandhu", null, "Kheri")
        val field = dir.addField(ORG, owner, ramesh, "Canal-side plot", 5.0, MeasureUnit.Bigha, crop = "Wheat")

        val swaraj = dir.addMachine(ORG, "Swaraj 855", MachineKind.Tractor, plate = "PB 10 AB 1234")
        val deere = dir.addMachine(ORG, "John Deere 5050", MachineKind.Tractor)
        val kartar = dir.addMachine(ORG, "Kartar 4000", MachineKind.Harvester)
        dir.setRate(ORG, "Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_000)

        val driverPerson = "driver-gurmeet"
        app.db.orgDao().upsertMembership(
            MembershipEntity(
                id = membershipId(ORG, driverPerson), orgId = ORG, personId = driverPerson,
                displayName = "Gurmeet Singh", phone = "9000000002", role = MemberRole.Operator,
                joinedAt = now, updatedAt = now,
            )
        )
        team.addMember(ORG, owner, "Jaswant Kumar", "9000000003", canSeeMoney = true, canAddCustomers = true, canCloseBills = true)
        team.createInvite(ORG, "Harpal Agro Works", owner, byPersonId = OWNER)

        // Finished today, by the owner, with a crew nobody has priced yet.
        val done = work.startJob(ORG, ramesh, "Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_000, swaraj, field.id, OWNER)
        work.setQuantity(ORG, done, 4.0)
        work.complete(ORG, done, OWNER)
        val cost = ledger.recordJobCost(ORG, owner, done, JobCostKind.Labour, headcount = 4, byPersonId = OWNER, atMillis = now)
        ledger.recordJobCost(ORG, owner, done, JobCostKind.Other, amountMinor = 120, note = "Tea", byPersonId = OWNER, atMillis = now)
        ledger.recordPayment(ORG, ramesh, 2_000, PaymentMethod.Cash, byPersonId = OWNER, jobId = done)

        // Finished earlier in the week, by the driver.
        val earlier = work.recordPastWork(
            ORG, harjit, "Harvesting", BillingMode.PerAcre, MeasureUnit.Acre, 2_200, 6.0,
            workedAt = now - 3 * day, startedAt = now - 3 * day, completedAt = now - 3 * day + 5 * 3_600_000,
            machineId = kartar, operatorPersonId = driverPerson,
        )
        work.complete(ORG, earlier, OWNER)

        // Out on a job right now, and the owner's own hourly job, paused.
        val crew = work.startJob(ORG, harjit, "Ploughing", BillingMode.PerAcre, MeasureUnit.Acre, 1_400, deere, null, driverPerson)
        work.setQuantity(ORG, crew, 2.5)
        val own = work.startJob(ORG, ramesh, "Rotavator", BillingMode.PerHour, MeasureUnit.Hour, 900, swaraj, null, OWNER)
        work.pause(ORG, own)

        // What a machine costs: a loan on the Swaraj, and enough finished hours on
        // it that an hour's cost is worth saying. Elapsed time is set directly —
        // a test that really waited out twenty-five hours would not finish.
        val calendarDay = java.time.LocalDate.now().toEpochDay()
        dir.setMachineFinance(
            ORG, owner, swaraj, 12_000, 700,
            purchasedDay = calendarDay - 3 * 365, purchaseMinor = 6_500_000_00, resaleMinor = 1_000_000_00, lifeYears = 10,
        )
        app.db.bookingDao().upsertRequest(
            com.example.fieldtrack.data.db.entity.BookingRequestEntity(
                id = "req-1", orgId = ORG, name = "Kamau Njoroge", phone = "+254700000001", village = "Nyeri", workType = "Ploughing",
                day = calendarDay + 3, note = "About two acres by the river", createdAt = now - 3_600_000, updatedAt = now - 3_600_000,
            )
        )
        app.db.orgDao().observeOrg(ORG).first()?.let { o -> app.db.orgDao().upsertOrg(o.copy(bookingLink = true, bookingSlug = "a1b2c3d4e5f6a7")) }
        dir.savePaper(ORG, owner, swaraj, com.example.fieldtrack.data.db.PaperKind.Insurance, "POL-2291", calendarDay + 12, null)
        dir.savePaper(ORG, owner, swaraj, com.example.fieldtrack.data.db.PaperKind.Fitness, null, calendarDay - 4, "Renew at the RTO")
        dir.savePaper(ORG, owner, swaraj, com.example.fieldtrack.data.db.PaperKind.Registration, "PB 10 AB 1234", calendarDay + 900, null)
        db.execSQL("UPDATE jobs SET elapsedSeconds = ${9 * 3600} WHERE id = '$done'")
        finishedJob(ramesh, swaraj, "Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_000, 10.0, 8, daysAgo = 9)
        finishedJob(ramesh, swaraj, "Rotavator", BillingMode.PerAcre, MeasureUnit.Bigha, 1_000, 12.0, 9, daysAgo = 12)
        work.addFuelForMachine(ORG, swaraj, 200.0, 18_000, OWNER)
        work.recordService(ORG, swaraj, "Oil and filters", 6_000, "Singh Motors", partsNote = "Engine oil, oil filter, fuel filter")

        // A job that cost more than it brought in, today: ₹1,200 billed, ₹1,500 of
        // diesel on it. It shows on the home, the jobs list, its own page and the season.
        val loss = finishedJob(harjit, swaraj, "Ploughing", BillingMode.PerHour, MeasureUnit.Hour, 400, 3.0, 3, daysAgo = 0)
        work.addFuel(ORG, loss, 20.0, 1_500, OWNER)

        // The John Deere drinking a third more than it did: 4 L an hour over the last
        // week against 3 before, with enough jobs and hours behind both.
        listOf(1, 2).forEach { d ->
            val j = finishedJob(harjit, deere, "Ploughing", BillingMode.PerAcre, MeasureUnit.Acre, 1_400, 3.0, 3, daysAgo = d)
            work.addFuel(ORG, j, 12.0, 1_100, driverPerson)
        }
        listOf(10, 15, 20).forEach { d ->
            val j = finishedJob(harjit, deere, "Ploughing", BillingMode.PerAcre, MeasureUnit.Acre, 1_400, 4.0, 4, daysAgo = d)
            work.addFuel(ORG, j, 12.0, 1_100, driverPerson)
        }

        work.reportBreakdownForMachine(ORG, kartar, BreakdownCategory.Hydraulics, "Leaking pipe", OWNER)
        work.addFuelForMachine(ORG, swaraj, 40.0, 3_800, OWNER)
        work.recordService(ORG, deere, "Oil and filters", 2_400, "Singh Motors")
        ledger.recordPayout(ORG, owner, 12_000, PayoutCovers.Period, now - day, OWNER, toPersonId = driverPerson, method = PaymentMethod.Cash)

        // The plan: a day with a double-booked machine and driver, work already under
        // way, a booking on a broken machine, one promised for yesterday, one rained off.
        val plan = app.planRepository
        val today = com.example.fieldtrack.data.plan.PlanDay.today(now, 4)
        val morning = com.example.fieldtrack.data.db.DayPart.Morning
        val whole = com.example.fieldtrack.data.db.DayPart.WholeDay
        val afternoon = com.example.fieldtrack.data.db.DayPart.Afternoon
        plan.save(owner, ORG, null, ramesh, field.id, "Rotavator", today, morning, 6 * 60 + 30, swaraj, driverPerson, "Gate by the canal")
        plan.save(owner, ORG, null, harjit, null, "Ploughing", today, morning, null, swaraj, driverPerson, null)
        val underWay = plan.save(owner, ORG, null, harjit, null, "Ploughing", today, whole, null, deere, driverPerson, null)
        db.execSQL("UPDATE jobs SET bookingId = '$underWay' WHERE id = '$crew'")
        val tomorrow = plan.save(owner, ORG, null, ramesh, field.id, "Harvesting", today + 1, afternoon, 14 * 60, kartar, null, "Bring the long header")
        plan.save(owner, ORG, null, harjit, null, "Ploughing", today + 1, whole, null, null, null, null)
        val rained = plan.save(owner, ORG, null, ramesh, null, "Sowing", today + 1, morning, null, deere, driverPerson, null)
        plan.cancel(owner, ORG, rained, com.example.fieldtrack.data.db.CancelReason.Rain)
        val missed = plan.save(owner, ORG, null, ramesh, null, "Rotavator", today - 1, morning, null, deere, driverPerson, null)

        return Ids(
            customerId = ramesh, fieldId = field.id, machineId = swaraj, doneJobId = done,
            costId = cost.id, lossJobId = loss, crewJobId = crew, ownJobId = own,
            driverMembershipId = membershipId(ORG, driverPerson), driverPersonId = driverPerson,
            bookingId = tomorrow, missedBookingId = missed, cancelledBookingId = rained,
        )
    }

    private val db get() = app.db.openHelper.writableDatabase

    /** A finished job of a known length, some days back. */
    private suspend fun finishedJob(
        customerId: String, machineId: String, type: String, mode: BillingMode,
        unit: MeasureUnit, rate: Long, quantity: Double, hours: Int, daysAgo: Int,
    ): String {
        val id = app.workRepository.startJob(ORG, customerId, type, mode, unit, rate, machineId, null, OWNER)
        if (mode != BillingMode.PerHour) app.workRepository.setQuantity(ORG, id, quantity)
        app.workRepository.complete(ORG, id, OWNER)
        val at = System.currentTimeMillis() - daysAgo * 86_400_000L
        db.execSQL(
            "UPDATE jobs SET elapsedSeconds = ${hours * 3600L}, workedAt = $at, startedAt = $at, completedAt = $at " +
                (if (mode == BillingMode.PerHour) ", quantity = $hours " else "") +
                "WHERE id = '$id'"
        )
        return id
    }

    private fun routes(ids: Ids) = listOf(
        "home" to Routes.HOME,
        "jobs" to Routes.JOBS,
        "customers" to Routes.CUSTOMERS,
        "fleet" to Routes.FLEET,
        "more" to Routes.MORE,
        // The screens of the field-feedback round.
        "history" to Routes.MONEY_HISTORY,
        "notices" to Routes.NOTICES,
        "roles" to Routes.ROLES,
        "role-new" to Routes.role(null),
        "person-pay" to Routes.personPay(com.example.fieldtrack.data.db.entity.membershipId(ORG, ids.driverPersonId)),
        "pay-out-person" to Routes.payOut(ids.driverPersonId),
        "start" to Routes.START,
        "start-for" to "start/${ids.customerId}",
        "start-on-field" to Routes.startOnField(ids.fieldId),
        "past-work" to Routes.PAST_WORK,
        "live" to Routes.live(ids.ownJobId),
        "finish" to Routes.finish(ids.ownJobId),
        "job" to "jobs/${ids.doneJobId}",
        "amend" to "jobs/${ids.doneJobId}/amend",
        "job-cost" to Routes.jobCost(ids.doneJobId, ids.costId),
        "new-customer" to Routes.NEW_CUSTOMER,
        "ledger" to "customer/${ids.customerId}",
        "pay" to "customer/${ids.customerId}/pay",
        "edit-customer" to "customer/${ids.customerId}/edit",
        "statement" to "customer/${ids.customerId}/statement",
        "statement-jobs" to "customer/${ids.customerId}/statement > customer/${ids.customerId}/statement/jobs",
        "fields" to Routes.fields(ids.customerId),
        "field-new" to Routes.newField(ids.customerId),
        "field" to Routes.field(ids.fieldId),
        "field-edit" to Routes.editField(ids.fieldId),
        "machines" to Routes.MACHINES,
        "new-machine" to Routes.NEW_MACHINE,
        "machine" to "machines/${ids.machineId}",
        "edit-machine" to "machines/${ids.machineId}/edit",
        "paper-new" to "machines/${ids.machineId}/papers/new",
        "worth" to "machines/${ids.machineId}/worth",
        "assets" to Routes.ASSETS,
        "income" to Routes.INCOME,
        "requests" to Routes.REQUESTS,
        "plan" to Routes.planPrice(),
        "plan-need" to Routes.planPrice(com.example.fieldtrack.data.plan.Need.MoreMachines),
        "booking-link" to Routes.BOOKING_LINK,
        "fuel" to "machines/${ids.machineId}/fuel",
        "machine-profit" to "machines/${ids.machineId}/made",
        "loss-job" to "jobs/${ids.lossJobId}",
        "upkeep" to Routes.UPKEEP,
        "money-out" to Routes.MONEY_OUT,
        "pay-out" to Routes.PAY_OUT,
        "season" to Routes.SEASON,
        "profile" to Routes.PROFILE,
        "settings" to Routes.SETTINGS,
        "money" to Routes.MONEY,
        "money-reports" to Routes.MONEY_REPORTS,
        "team" to Routes.TEAM,
        "member" to "more/team/${ids.driverMembershipId}",
        "member-money" to "more/team/${ids.driverMembershipId}?tab=Money",
        "member-details" to "more/team/${ids.driverMembershipId}?tab=Access",
        "invite" to Routes.INVITE,
        "sent-codes" to Routes.SENT_CODES,
        "hand-over" to Routes.HAND_OVER,
        "team-changes" to Routes.TEAM_CHANGES,
        "backup" to Routes.BACKUP,
        "display" to Routes.DISPLAY,
        "export" to Routes.EXPORT,
        "reminders" to Routes.REMINDERS,
        "rates" to Routes.RATES,
        "price" to Routes.PRICE,
        "seasons-edit" to Routes.SEASONS_EDIT,
        "plan" to Routes.PLAN,
        "plan-settings" to Routes.PLAN_SETTINGS, "money-settings" to Routes.MONEY_SETTINGS,
        "booking-new" to Routes.booking(),
        "booking" to Routes.booking(ids.bookingId),
        "booking-missed" to Routes.booking(ids.missedBookingId),
        "booking-cancelled" to Routes.booking(ids.cancelledBookingId),
        "start-booking" to Routes.startBooking(ids.bookingId),
        "join-another" to Routes.JOIN_ANOTHER,
    )

    private companion object {
        const val ORG = "org-harpal"
        const val OWNER = "person-harpal"
    }
}
