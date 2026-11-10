package com.example.fieldtrack.feature

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fieldtrack.R
import com.example.fieldtrack.data.db.FieldTrackDatabase
import com.example.fieldtrack.data.db.MeasureUnit
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.SessionStore
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.more.MoreScreen
import com.example.fieldtrack.feature.more.MoreState
import com.example.fieldtrack.feature.more.ProfileActions
import com.example.fieldtrack.feature.more.ProfileFields
import com.example.fieldtrack.feature.more.ProfileScreen
import com.example.fieldtrack.feature.more.ProfileState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where things are: the short menu, search in the bar, the profile that is its own form.
 *
 * The owner's rule from the field: one place for each thing, nothing that looks tappable
 * unless it is, and the same order for every role with only visibility differing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArrangementTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun menu(state: MoreState) {
        compose.setContent {
            FieldTrackTheme(dark = false) {
                MoreScreen(
                    state = state, onProfile = {}, onChangeBusiness = {}, onTeam = {}, onMoney = {},
                    onRequests = {}, onSettings = {}, onHelp = {}, inDrawer = true,
                )
            }
        }
    }

    @Test fun aDriverIsShownAShortMenuWithNoMoneyOrTeam() {
        menu(MoreState(displayName = "Gurmeet", businessName = "Harpal Agro Works", role = MemberRole.Operator))
        compose.onNodeWithText(text(R.string.more_settings)).assertExists()
        compose.onNodeWithText(text(R.string.help_title)).assertExists()
        compose.onNodeWithText(text(R.string.menu_your_profile)).assertExists()
        compose.onNodeWithText(text(R.string.menu_change_business)).assertExists()
        compose.onAllNodesWithText(text(R.string.menu_money)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.more_team)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.requests_title)).assertCountEquals(0)
    }

    @Test fun anOwnerSeesTeamAndMoneyButNotTheTabsAgain() {
        menu(
            MoreState(
                displayName = "Harpal", businessName = "Harpal Agro Works", role = MemberRole.Owner,
                canSeeMoney = true, canSeePeopleCost = true, canPlan = true, canSeeTeam = true, memberCount = 3,
            )
        )
        compose.onNodeWithText(text(R.string.more_team)).assertExists()
        compose.onNodeWithText(text(R.string.menu_money)).assertExists()
        // Machines and Customers are tabs; the menu does not repeat them.
        compose.onAllNodesWithText(text(R.string.machines_title)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.customers_title)).assertCountEquals(0)
        // No booking link and nothing waiting: requests have nowhere to come from.
        compose.onAllNodesWithText(text(R.string.requests_title)).assertCountEquals(0)
    }

    @Test fun farmersRequestsAppearOnceTheyCanArrive() {
        menu(MoreState(displayName = "Harpal", businessName = "H", canPlan = true, bookingLinkOn = true))
        compose.onNodeWithText(text(R.string.requests_title)).assertExists()
    }

    @Test fun searchHappensInTheBarAndKeepsEveryKey() {
        var searching by mutableStateOf(false)
        var typed by mutableStateOf("")
        compose.setContent {
            FieldTrackTheme(dark = false) {
                RootBar(
                    initials = "HS",
                    menuContentDescription = "menu",
                    searchContentDescription = text(R.string.menu_search_cd),
                    onMenu = {},
                    onSearch = { searching = true },
                    searching = searching,
                    query = typed,
                    onQuery = { typed = it },
                    onCloseSearch = { searching = false; typed = "" },
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.menu_search_cd)).performClick()
        compose.waitForIdle()
        assertTrue(searching)
        compose.onNode(hasSetTextAction()).performTextInput("walk")
        compose.waitForIdle()
        assertEquals("walk", typed)
        compose.onNodeWithContentDescription(text(R.string.search_close_cd)).performClick()
        compose.waitForIdle()
        assertEquals(false, searching)
        assertEquals("", typed)
    }

    @Test fun theProfileIsTheFormAndOffersSaveOnlyAfterAChange() {
        val loaded = ProfileFields(yourName = "Harpal", businessName = "Harpal Agro Works")
        var state by mutableStateOf(ProfileState(fields = loaded, saved = loaded, loaded = true, canEditBusiness = true))
        compose.setContent {
            FieldTrackTheme(dark = false) {
                ProfileScreen(
                    state = state,
                    actions = ProfileActions(
                        onYourName = { state = state.copy(fields = state.fields.copy(yourName = it)) },
                        onBusinessName = {}, onPhone = {}, onAddress = {}, onGstin = {}, onUpiId = {},
                        onSave = {}, onDiscard = {},
                    ),
                    onBack = {},
                )
            }
        }
        val save = text(R.string.edit_save)
        compose.onAllNodesWithText(save).assertCountEquals(0)
        compose.onNodeWithText("Harpal").performTextInput(" ji")
        compose.waitForIdle()
        compose.onNodeWithText(save).assertExists()
    }

    @Test fun landUnitChangesOnItsOwnFromSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("fieldtrack.session", Context.MODE_PRIVATE).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(context, FieldTrackDatabase::class.java).allowMainThreadQueries().build()
        try {
            val store = SessionStore(context, db)
            val orgId = store.completeOnboarding(
                displayName = "Harpal", businessName = "Harpal Agro Works", landUnit = MeasureUnit.Acre, isBusiness = true,
            ).orgId
            store.activate()
            store.setLandUnit(MeasureUnit.Bigha)
            assertEquals(MeasureUnit.Bigha, db.orgDao().observeOrg(orgId).first()?.defaultLandUnit)
            assertEquals(MeasureUnit.Bigha, store.current()?.landUnit)
            // The name and the rest of the letterhead are untouched.
            assertEquals("Harpal Agro Works", db.orgDao().observeOrg(orgId).first()?.name)
        } finally {
            db.close()
        }
    }

    @Test fun aMemberPageOffersSaveOnlyOnceSomethingChanged() {
        val base = com.example.fieldtrack.feature.team.MemberState(
            loaded = true, displayName = "Gurmeet", savedName = "Gurmeet", editable = true,
        )
        var state by mutableStateOf(base)
        compose.setContent {
            FieldTrackTheme(dark = false) {
                com.example.fieldtrack.feature.team.MemberScreen(
                    state = state, onBack = {}, onName = {}, onPhone = {}, onPreset = {}, onSeeMoney = {},
                    onAddCustomers = {}, onCloseBills = {}, onEditTeam = {}, onEditMachines = {}, onPlanWork = {},
                    onPayOut = {}, onSeePeopleCost = {}, onSave = {}, onSetActive = {}, onCall = null,
                    tab = com.example.fieldtrack.feature.team.MemberTab.Access,
                )
            }
        }
        val save = text(R.string.edit_save)
        compose.onAllNodesWithText(save).assertCountEquals(0)
        state = base.copy(displayName = "Gurmeet Singh")
        compose.waitForIdle()
        compose.onNodeWithText(save).assertExists()
    }

    @Test fun aPaymentIsTakenBackWithAButtonYouCanSee() {
        compose.setContent {
            FieldTrackTheme(dark = false) {
                com.example.fieldtrack.feature.money.PersonPayContent(
                    state = com.example.fieldtrack.feature.money.PersonPayState(
                        loaded = true, mayLook = true, mayPay = true, name = "Gurmeet", paidMinor = 200_000,
                        rows = listOf(
                            com.example.fieldtrack.feature.money.PersonPayRow(
                                "p1", 1L, 200_000, com.example.fieldtrack.data.db.PayoutCovers.Day, null, null, voided = false,
                            ),
                            com.example.fieldtrack.feature.money.PersonPayRow(
                                "p2", 2L, 100_000, com.example.fieldtrack.data.db.PayoutCovers.Advance, null, null, voided = true,
                            ),
                        ),
                    ),
                    onPay = {}, onTakeBack = {},
                )
            }
        }
        // One for the live payment; none for the one already taken back.
        compose.onAllNodesWithText(text(R.string.person_pay_back_button)).assertCountEquals(1)
    }
}
