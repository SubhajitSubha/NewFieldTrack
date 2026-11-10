package com.example.fieldtrack.feature

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Moving between tabs, and opening a tab from somewhere else.
 *
 * Machines is a tab for a solo owner and for a plain driver, and More has a row
 * that opens it. That row used to push Machines on top of More. The bar showed it
 * selected, but tapping the More tab then restored the saved pair [More, Machines]
 * and landed back on Machines — More could not be reached again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabNavigationTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    // A driver's tabs: Home, My work, Machines, More.
    private val tabs = listOf(Routes.HOME, Routes.JOBS, Routes.MACHINES, Routes.MORE)

    @org.junit.Before fun setUp() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.HOME) {
                tabs.forEach { route -> composable(route) { Text(route) } }
                composable("machines/{id}") { Text("a machine") }
                composable(Routes.FLEET) { Text("fleet") }
            }
        }
        compose.waitForIdle()
    }

    private fun ui(block: () -> Unit) {
        compose.runOnUiThread(block)
        compose.waitForIdle()
    }

    private fun here() = nav.currentDestination?.route

    private fun stack() = nav.currentBackStack.value.mapNotNull { it.destination.route }
        .filter { it != "" && !it.startsWith("androidx") }

    @Test
    fun `a tab opened from More keeps More reachable`() {
        ui { nav.switchTab(Routes.MORE) }
        ui { nav.openScreen(Routes.MACHINES, tabs) }       // the "Machines" row on More
        assertEquals(Routes.MACHINES, here())

        ui { nav.switchTab(Routes.MORE) }                  // the More tab
        assertEquals("More is reachable from Machines", Routes.MORE, here())
    }

    @Test
    fun `and back from there goes Home, not round and round`() {
        ui { nav.switchTab(Routes.MORE) }
        ui { nav.openScreen(Routes.MACHINES, tabs) }
        ui { nav.popBackStack() }
        assertEquals(Routes.HOME, here())
    }

    @Test
    fun `pushing a tab on top of another is the trap openScreen exists to avoid`() {
        // Not a behaviour to keep — a record of why the rule above is there. If a
        // newer Navigation stops doing this, this test says so and the rule can go.
        ui { nav.switchTab(Routes.MORE) }
        ui { nav.navigate(Routes.MACHINES) }               // what the row used to do
        ui { nav.switchTab(Routes.MORE) }
        assertEquals("lands back on Machines", Routes.MACHINES, here())
    }

    @Test
    fun `a screen that is not a tab for this person is pushed, with a way back`() {
        // A business owner's tabs have Fleet and no Machines.
        val owner = listOf(Routes.HOME, Routes.JOBS, Routes.CUSTOMERS, Routes.FLEET, Routes.MORE)
        ui { nav.switchTab(Routes.FLEET) }
        ui { nav.openScreen("machines/1", owner) }
        assertEquals("machines/{id}", here())
        ui { nav.popBackStack() }
        assertEquals(Routes.FLEET, here())
    }

    @Test
    fun `switching between tabs does not pile screens up`() {
        repeat(3) {
            ui { nav.switchTab(Routes.JOBS) }
            ui { nav.switchTab(Routes.HOME) }
        }
        assertEquals(Routes.HOME, here())
        assertEquals("one Home and nothing else", 1, stack().count { it == Routes.HOME })
        ui { nav.switchTab(Routes.MACHINES) }
        ui { nav.popBackStack() }
        assertEquals(Routes.HOME, here())
    }
}
