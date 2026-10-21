package com.example.fieldtrack.feature

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fieldtrack.FieldFlowApplication
import com.example.fieldtrack.data.db.BillingMode
import com.example.fieldtrack.R
import com.example.fieldtrack.data.session.Capability
import com.example.fieldtrack.data.db.MemberRole
import com.example.fieldtrack.data.session.Startup
import com.example.fieldtrack.designsystem.component.FtBottomBar
import com.example.fieldtrack.document.Document
import com.example.fieldtrack.document.DocumentShare
import com.example.fieldtrack.document.Letterhead
import com.example.fieldtrack.document.Party
import com.example.fieldtrack.document.PaymentLine
import com.example.fieldtrack.document.WorkLine
import com.example.fieldtrack.document.rupeesInWords
import com.example.fieldtrack.designsystem.component.FtNavDestination
import com.example.fieldtrack.designsystem.format.quantity
import com.example.fieldtrack.designsystem.format.rupees
import com.example.fieldtrack.designsystem.icon.FieldTrackIcons
import com.example.fieldtrack.designsystem.theme.FieldTrackTheme
import com.example.fieldtrack.feature.customer.AddCustomerScreen
import com.example.fieldtrack.feature.customer.AddCustomerViewModel
import com.example.fieldtrack.feature.customer.CustomersScreen
import com.example.fieldtrack.feature.customer.CustomersViewModel
import com.example.fieldtrack.feature.customer.EditCustomerViewModel
import com.example.fieldtrack.feature.customer.LedgerScreen
import com.example.fieldtrack.feature.customer.StatementPickJobsScreen
import com.example.fieldtrack.feature.customer.StatementScreen
import com.example.fieldtrack.feature.customer.StatementViewModel
import com.example.fieldtrack.feature.customer.DateRangeSheet
import com.example.fieldtrack.feature.customer.statementDocumentFor
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.fieldtrack.feature.customer.CustomerTab
import com.example.fieldtrack.feature.customer.LedgerViewModel
import com.example.fieldtrack.feature.customer.RecordPaymentScreen
import com.example.fieldtrack.feature.customer.RecordPaymentViewModel
import com.example.fieldtrack.feature.home.HomeScreen
import com.example.fieldtrack.feature.fleet.FleetScreen
import com.example.fieldtrack.feature.fleet.FleetViewModel
import com.example.fieldtrack.feature.home.HomeViewModel
import com.example.fieldtrack.feature.jobs.AmendJobScreen
import com.example.fieldtrack.feature.jobs.AmendJobViewModel
import com.example.fieldtrack.feature.fields.FieldDetailScreen
import com.example.fieldtrack.feature.fields.FieldDetailViewModel
import com.example.fieldtrack.feature.fields.FieldEditScreen
import com.example.fieldtrack.feature.fields.FieldEditViewModel
import com.example.fieldtrack.feature.fields.FieldsScreen
import com.example.fieldtrack.feature.fields.FieldsViewModel
import com.example.fieldtrack.feature.jobs.JobCostScreen
import com.example.fieldtrack.feature.jobs.JobCostViewModel
import com.example.fieldtrack.feature.jobs.JobDetailScreen
import com.example.fieldtrack.feature.jobs.JobDetailViewModel
import com.example.fieldtrack.feature.jobs.JobsScreen
import com.example.fieldtrack.feature.jobs.JobsViewModel
import com.example.fieldtrack.feature.machine.AddMachineScreen
import com.example.fieldtrack.feature.machine.AddMachineViewModel
import com.example.fieldtrack.feature.machine.EditMachineViewModel
import com.example.fieldtrack.feature.machine.MachinesScreen
import com.example.fieldtrack.feature.machine.MachinesViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.fieldtrack.feature.join.JoinScreen
import com.example.fieldtrack.feature.join.JoinStep
import com.example.fieldtrack.feature.join.JoinViewModel
import com.example.fieldtrack.feature.invite.InviteScreen
import com.example.fieldtrack.feature.invite.InviteViewModel
import com.example.fieldtrack.feature.driver.DriverHomeScreen
import com.example.fieldtrack.feature.driver.DriverJobsScreen
import com.example.fieldtrack.feature.driver.DriverJobsViewModel
import com.example.fieldtrack.feature.driver.DriverHomeViewModel
import com.example.fieldtrack.feature.machine.FuelLogScreen
import com.example.fieldtrack.feature.machine.FuelLogViewModel
import com.example.fieldtrack.feature.machine.MachineDetailScreen
import com.example.fieldtrack.feature.machine.MachineDetailViewModel
import com.example.fieldtrack.feature.upkeep.UpkeepScreen
import com.example.fieldtrack.feature.upkeep.UpkeepViewModel
import com.example.fieldtrack.data.auth.FirebasePhoneAuth
import com.example.fieldtrack.feature.signin.SignInScreen
import com.example.fieldtrack.feature.signin.SignInStep
import com.example.fieldtrack.feature.signin.SignInViewModel
import com.example.fieldtrack.feature.rates.RateCardScreen
import com.example.fieldtrack.feature.rates.RateCardViewModel
import com.example.fieldtrack.feature.orgs.OrgSwitcherScreen
import com.example.fieldtrack.feature.orgs.OrgSwitcherViewModel
import com.example.fieldtrack.feature.more.BackupScreen
import com.example.fieldtrack.feature.more.BackupViewModel
import com.example.fieldtrack.feature.more.MoreScreen
import com.example.fieldtrack.feature.more.EditBusinessViewModel
import com.example.fieldtrack.feature.more.EditProfileViewModel
import com.example.fieldtrack.feature.more.EditSettingsScreen
import com.example.fieldtrack.feature.more.LetterheadFields
import com.example.fieldtrack.feature.more.ProfileScreen
import com.example.fieldtrack.feature.more.ProfileViewModel
import com.example.fieldtrack.feature.more.MoreViewModel
import com.example.fieldtrack.feature.onboarding.OnboardingScreen
import com.example.fieldtrack.feature.onboarding.OnboardingStep
import com.example.fieldtrack.feature.onboarding.OnboardingViewModel
import com.example.fieldtrack.feature.work.FinishScreen
import com.example.fieldtrack.feature.work.FinishViewModel
import com.example.fieldtrack.feature.work.LiveWorkScreen
import com.example.fieldtrack.feature.work.LiveWorkViewModel
import com.example.fieldtrack.feature.work.NewMachineSheet
import com.example.fieldtrack.feature.team.AddMemberSheet
import com.example.fieldtrack.feature.team.MemberScreen
import com.example.fieldtrack.feature.team.MemberViewModel
import com.example.fieldtrack.feature.team.TeamScreen
import com.example.fieldtrack.feature.team.TeamViewModel
import com.example.fieldtrack.feature.work.ClockSheet
import com.example.fieldtrack.feature.work.DayPickerSheet
import com.example.fieldtrack.feature.work.PastWorkScreen
import com.example.fieldtrack.feature.work.PastWorkViewModel
import com.example.fieldtrack.feature.work.PickCustomerSheet
import com.example.fieldtrack.feature.work.StartWorkScreen
import com.example.fieldtrack.feature.work.StartWorkViewModel
import kotlinx.coroutines.launch
import com.example.fieldtrack.data.auth.formatIndianPhone
import com.example.fieldtrack.feature.invite.SentCodesScreen
import com.example.fieldtrack.feature.invite.SentCodesViewModel
import com.example.fieldtrack.feature.team.HandOverScreen
import com.example.fieldtrack.feature.team.HandOverViewModel
import com.example.fieldtrack.feature.team.DuplicatePair
import com.example.fieldtrack.feature.team.TeamActivityScreen
import com.example.fieldtrack.feature.team.TeamActivityViewModel
import com.example.fieldtrack.feature.money.MoneyOutScreen
import com.example.fieldtrack.feature.money.MoneyOutViewModel
import com.example.fieldtrack.feature.money.PayOutScreen
import com.example.fieldtrack.feature.money.PayOutViewModel
import com.example.fieldtrack.feature.money.MachineProfitScreen
import com.example.fieldtrack.feature.money.MachineProfitViewModel

private object Routes {
    const val HOME = "home"
    const val START = "start"
    const val START_FOR = "start/{customerId}"
    const val START_ON_FIELD = "start-on/{fieldId}"
    const val FIELDS = "customer/{customerId}/fields"
    const val FIELD_NEW = "customer/{customerId}/fields/new"
    const val FIELD = "field/{fieldId}"
    const val FIELD_EDIT = "field/{fieldId}/edit"
    const val PAST_WORK = "start/past"
    const val NEW_CUSTOMER = "customer/new"
    const val LIVE = "live/{jobId}"
    const val FINISH = "finish/{jobId}"
    const val CUSTOMERS = "customers"
    const val MACHINES = "machines"
    const val NEW_MACHINE = "machines/new"
    const val EDIT_MACHINE = "machines/{machineId}/edit"
    const val MORE = "more"
    const val PROFILE = "more/profile"
    const val TEAM = "more/team"
    const val MEMBER = "more/team/{membershipId}"
    const val EDIT_PROFILE = "more/profile/name"
    const val EDIT_BUSINESS = "more/business"
    const val BACKUP = "more/backup"
    const val RATES = "more/rates"
    const val ORGS = "more/businesses"
    const val JOIN_ANOTHER = "more/businesses/join"
    const val UPKEEP = "fleet/upkeep"
    const val INVITE = "more/team/invite"
    const val SENT_CODES = "more/team/invite/sent"
    const val HAND_OVER = "more/team/handover"
    const val TEAM_CHANGES = "more/team/changes"
    const val MONEY_OUT = "more/money-out"
    const val PAY_OUT = "more/money-out/pay"
    const val MACHINE_PROFIT = "machines/{machineId}/made"
    const val MACHINE = "machines/{machineId}"
    const val FUEL = "machines/{machineId}/fuel"
    const val JOBS = "jobs"
    const val JOB = "jobs/{jobId}"
    const val AMEND = "jobs/{jobId}/amend"
    const val JOB_COST = "jobs/{jobId}/cost?costId={costId}"
    const val FLEET = "fleet"

    /**
     * The tabs the bottom bar switches between.
     *
     * Three tracks, not two. A solo owner has no drivers to watch and no fleet to
     * speak of, so those tabs would be a dashboard of one. A business owner gets
     * them and reaches individual machines through Fleet.
     *
     * A driver gets neither, and the reason is worth stating: the business's
     * customers and its fleet are not theirs. Their tabs are their own work and the
     * machines they drive. Until this existed the bar branched on business mode — a
     * preference the *owner* sets about themselves — so a driver signing in was
     * handed the owner's screens and told to get on with it.
     */
    fun roots(business: Boolean, driver: Boolean) = when {
        driver -> listOf(HOME, JOBS, MACHINES, MORE)
        business -> listOf(HOME, JOBS, CUSTOMERS, FLEET, MORE)
        else -> listOf(HOME, CUSTOMERS, MACHINES, MORE)
    }
    const val LEDGER = "customer/{customerId}"
    const val PAY = "customer/{customerId}/pay"
    const val EDIT_CUSTOMER = "customer/{customerId}/edit"
    const val STATEMENT = "customer/{customerId}/statement"
    const val STATEMENT_JOBS = "customer/{customerId}/statement/jobs"

    fun live(jobId: String) = "live/$jobId"

    fun fields(customerId: String) = "customer/$customerId/fields"
    fun newField(customerId: String) = "customer/$customerId/fields/new"
    fun field(fieldId: String) = "field/$fieldId"
    fun editField(fieldId: String) = "field/$fieldId/edit"
    fun startOnField(fieldId: String) = "start-on/$fieldId"

    /** The cost sheet for one job, optionally opened on a cost already there. */
    fun jobCost(jobId: String, costId: String? = null) =
        "jobs/$jobId/cost?costId=${costId.orEmpty()}"
    fun finish(jobId: String) = "finish/$jobId"
    fun ledger(customerId: String) = "customer/$customerId"
    fun pay(customerId: String) = "customer/$customerId/pay"
    fun editCustomer(customerId: String) = "customer/$customerId/edit"
}

/**
 * The work loop, end to end.
 *
 * Only the routes the redesigned flow needs. The pre-redesign navigation graph still
 * exists under ui/navigation and is retired as each of its screens is replaced.
 */
@Composable
fun FieldTrackApp(navController: NavHostController = rememberNavController()) {
    val app = LocalContext.current.applicationContext as FieldFlowApplication
    val startup by app.sessionStore.startup.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { app.sessionStore.load() }

    FieldTrackTheme {
        when (startup) {
            // Resolving the session is a local database read, so this is a flicker
            // rather than a wait — but something has to be on screen during it.
            is Startup.Loading -> SplashScreen()

            is Startup.NeedsSignIn -> {
                // Firebase's phone flow needs the hosting Activity for its reCAPTCHA
                // and Play Integrity checks, so it has to be fished out of context.
                val activity = LocalContext.current as? android.app.Activity
                val scope = rememberCoroutineScope()
                val vm: SignInViewModel = viewModel {
                    SignInViewModel(
                        auth = FirebasePhoneAuth(activityProvider = { activity }),
                        onSignedIn = { user ->
                            app.sessionStore.recordSignIn(user.uid, user.phone)
                            // An account turned off must not be signed back into
                            // simply by proving the number again. Checked here as
                            // well as on every sync, because sign-in is the one
                            // moment somebody locked out is certain to try.
                            val disabled = runCatching {
                                app.remoteRestore.isAccountDisabled(user.uid)
                            }.getOrDefault(false)
                            if (disabled) {
                                app.sessionStore.lockOutDisabledAccount()
                                return@SignInViewModel
                            }
                            app.sessionStore.clearAccountDisabled()
                            // Same number, same work. Before offering to set up a
                            // business, ask the server whether this person already
                            // has one — a new handset should hand somebody their
                            // records, not a blank app and a setup wizard.
                            app.sessionStore.restoreFor(
                                personId = user.uid,
                                restore = app.remoteRestore,
                                applier = app.restoreApplier,
                            )
                        },
                    )
                }
                val state by vm.state.collectAsStateWithLifecycle()
                BackHandler(enabled = state.step == SignInStep.Code) { vm.changeNumber() }
                SignInScreen(
                    state = state,
                    onDigits = vm::setDigits,
                    onCode = vm::setCode,
                    onSendCode = vm::sendCode,
                    onVerify = vm::verify,
                    onResend = vm::resend,
                    onChangeNumber = vm::changeNumber,
                    accountTurnedOff = app.sessionStore.accountDisabled(),
                    // Nothing behind this: it is the first thing the app shows.
                    onBack = null,
                )
            }

            is Startup.NeedsOnboarding -> {
                // Signed in but in no business yet. A driver was sent a code and has
                // nothing to set up; an owner has no code and everything to. Asking
                // first costs one tap for the owner and saves the driver from
                // creating a business they did not want and cannot easily undo.
                var askedForCode by rememberSaveable { mutableStateOf(false) }

                if (!askedForCode) {
                    val scope = rememberCoroutineScope()
                    val vm: JoinViewModel = viewModel {
                        JoinViewModel(
                            lookup = app.inviteLookup,
                            suggestedName = "",
                            onJoin = { invite, name ->
                                val me = app.sessionStore.load()
                                val personId = app.sessionStore.personIdOrNull()
                                    ?: return@JoinViewModel
                                app.teamRepository.redeemInvite(
                                    invite = invite,
                                    personId = personId,
                                    displayName = name,
                                    phone = app.sessionStore.signedInPhone(),
                                )
                                app.sessionStore.adoptMembership(invite.orgId)
                            },
                        )
                    }
                    val joinState by vm.state.collectAsStateWithLifecycle()
                    BackHandler(enabled = joinState.step == JoinStep.Preview) { vm.back() }
                    // The name of the business they were taken off, if that is
                    // why they are here. Looked up locally: the membership row is
                    // kept, so the name survives the removal.
                    val removedOrgId = app.sessionStore.removedFrom()
                    var removedName by remember(removedOrgId) { mutableStateOf<String?>(null) }
                    val unknownBusiness = stringResource(R.string.join_removed_unknown)
                    LaunchedEffect(removedOrgId) {
                        removedName = removedOrgId?.let {
                            app.db.orgDao().findOrg(it)?.name?.ifBlank { null } ?: unknownBusiness
                        }
                    }
                    JoinScreen(
                        state = joinState,
                        removedFrom = removedName,
                        onCode = vm::setCode,
                        onName = vm::setName,
                        onCheck = vm::check,
                        onJoin = vm::join,
                        onBack = {
                            if (joinState.step == JoinStep.Preview) vm.back()
                            else askedForCode = true
                        },
                        onSkip = {
                            app.sessionStore.clearRemovedNotice()
                            askedForCode = true
                        },
                    )
                    return@FieldTrackTheme
                }

                val vm: OnboardingViewModel = appViewModel { OnboardingViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                BackHandler(enabled = state.step != OnboardingStep.Welcome) { vm.back() }
                OnboardingScreen(
                    state = state,
                    onBack = { vm.back() },
                    onOwnerKind = vm::setOwnerKind,
                    onName = vm::setName,
                    onBusinessName = vm::setBusinessName,
                    onLandUnit = vm::setLandUnit,
                    onMachineKind = vm::setMachineKind,
                    onMachineName = vm::setMachineName,
                    onNext = {
                        // The last step writes everything; the rest just advance.
                        if (state.step == OnboardingStep.Machine) vm.finishSetup() else vm.next()
                    },
                    onFinish = { vm.start() },
                )
            }

            is Startup.Ready -> MainGraph(navController)
        }
    }
}

/** Held for the instant it takes to read the session off disk. */
@Composable
private fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(FieldTrackTheme.colors.paper)
    )
}

@Composable
private fun MainGraph(navController: NavHostController) {
    val app = LocalContext.current.applicationContext as FieldFlowApplication
    val business by app.sessionStore.businessMode.collectAsStateWithLifecycle()
    val startup by app.sessionStore.startup.collectAsStateWithLifecycle()
    val driver = (startup as? Startup.Ready)?.session?.role == MemberRole.Operator
    val roots = Routes.roots(business, driver)

    val entry by navController.currentBackStackEntryAsState()
    val tab = roots.indexOf(entry?.destination?.route)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            MainNavHost(navController)
        }
        // The bar belongs to the tabs only. On a job in progress or a bill being
        // written, anything that invites you to wander off is a way to lose work.
        if (tab >= 0) {
            FtBottomBar(
                destinations = bottomDestinations(roots, driver),
                selectedIndex = tab,
                onSelect = { index -> navController.switchTab(roots[index]) },
            )
        }
    }
}

/**
 * The bar, built from the very list the taps navigate to.
 *
 * These used to be two lists kept in step by hand — one deciding what the bar said,
 * the other deciding where a tap went. They drifted the moment a third track
 * appeared, and a driver tapping "Customers" would have been taken to Machines
 * instead: the label and the destination disagreeing is worse than either being
 * wrong on its own, because the app then lies about where it is sending you.
 * Deriving one from the other makes that impossible rather than merely fixed.
 */
@Composable
private fun bottomDestinations(roots: List<String>, driver: Boolean) = roots.map { route ->
    when (route) {
        Routes.HOME -> FtNavDestination(
            FieldTrackIcons.Home, stringResource(R.string.nav_home),
        )
        // A driver's jobs tab is his own work, not the yard's.
        Routes.JOBS -> FtNavDestination(
            FieldTrackIcons.Play,
            stringResource(if (driver) R.string.driver_my_work else R.string.nav_jobs),
        )
        Routes.CUSTOMERS -> FtNavDestination(
            FieldTrackIcons.Customer, stringResource(R.string.nav_customers),
        )
        Routes.FLEET -> FtNavDestination(
            FieldTrackIcons.Tractor, stringResource(R.string.nav_fleet),
        )
        Routes.MACHINES -> FtNavDestination(
            FieldTrackIcons.Tractor, stringResource(R.string.nav_machines),
        )
        else -> FtNavDestination(FieldTrackIcons.More, stringResource(R.string.nav_more))
    }
}

/**
 * Moves between tabs without stacking them.
 *
 * Without this, tapping Home then Customers then Home three times leaves six screens
 * on the back stack and the device back button walks a trail nobody remembers making.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavHost(navController: NavHostController) {
    val app = LocalContext.current.applicationContext as FieldFlowApplication
    // Hiding an affordance someone may not use is a courtesy; the repository refuses
    // the call regardless, which is the boundary that actually holds.
    val canEditCustomers = remember {
        app.sessionStore.current()?.can?.let { Capability.EditCustomers in it } ?: false
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {

            composable(Routes.HOME) {
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val startup by app.sessionStore.startup.collectAsStateWithLifecycle()
                val session = (startup as? Startup.Ready)?.session

                // A driver's home is its own screen, not the owner's with the money
                // stripped out. Different question, different answer.
                if (session?.role == MemberRole.Operator) {
                    val driverVm: DriverHomeViewModel =
                        appViewModel { DriverHomeViewModel(it) }
                    val driverState by driverVm.state.collectAsStateWithLifecycle()
                    DriverHomeScreen(
                        state = driverState,
                        onStartWork = { navController.navigate(Routes.START) },
                        onResumeWork = { navController.navigate(Routes.live(it)) },
                        onAddPastWork = { navController.navigate(Routes.PAST_WORK) },
                        onOpenJob = { id -> navController.navigate("jobs/$id") },
                        onOpenMachine = { id -> navController.navigate("machines/$id") },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    )
                    return@composable
                }

                val vm: HomeViewModel = appViewModel { HomeViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onStartWork = { navController.navigate(Routes.START) },
                    onResumeWork = { navController.navigate(Routes.live(it)) },
                    onOpenCustomers = { navController.navigate(Routes.CUSTOMERS) },
                    onAddPastWork = { navController.navigate(Routes.PAST_WORK) },
                    // Opens the job's own record. Correcting it is one of the things
                    // offered there, rather than the only thing a tap can mean.
                    onOpenJob = { id -> navController.navigate("jobs/$id") },
                )
            }

            // Two ways in: the big green button, and the plus on a customer's own
            // page. Same screen; the second just arrives with the customer known.
            composable(Routes.START) { StartWorkDestination(navController, null) }

            // A third way in: standing on a plot and tapping "start work here".
            // The field names its own customer, so step one is answered too.
            composable(Routes.START_ON_FIELD) { entry ->
                StartWorkDestination(
                    navController,
                    forCustomerId = null,
                    forFieldId = entry.arguments?.getString("fieldId"),
                )
            }

            composable(Routes.FIELDS) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                val vm: FieldsViewModel = appViewModel(key = "fields-${'$'}customerId") {
                    FieldsViewModel(it, customerId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                FieldsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.newField(customerId)) },
                    onOpen = { id -> navController.navigate(Routes.field(id)) },
                )
            }

            composable(Routes.FIELD_NEW) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                FieldEditDestination(navController, customerId = customerId, fieldId = null)
            }

            composable(Routes.FIELD_EDIT) { entry ->
                val fieldId = entry.arguments?.getString("fieldId").orEmpty()
                FieldEditDestination(navController, customerId = "", fieldId = fieldId)
            }

            composable(Routes.FIELD) { entry ->
                val fieldId = entry.arguments?.getString("fieldId").orEmpty()
                val vm: FieldDetailViewModel = appViewModel(key = "field-${'$'}fieldId") {
                    FieldDetailViewModel(it, fieldId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val noMaps = stringResource(R.string.field_maps_missing)
                FieldDetailScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editField(fieldId)) },
                    onOpenMaps = {
                        val lat = state.latitude
                        val lng = state.longitude
                        if (lat != null && lng != null) {
                            // geo: with a label, so the pin arrives named rather
                            // than as a bare dot the driver has to guess at.
                            val label = Uri.encode(state.name)
                            val geo = Uri.parse("geo:${'$'}lat,${'$'}lng?q=${'$'}lat,${'$'}lng(${'$'}label)")
                            val intent = Intent(Intent.ACTION_VIEW, geo)
                            // Tried rather than asked about. From Android 11,
                            // resolveActivity answers null for anything outside
                            // this app's <queries> declaration even when two map
                            // apps are installed — so asking first reported "no
                            // map app" on a phone that had one.
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(context, noMaps, Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    onStartWork = { navController.navigate(Routes.startOnField(fieldId)) },
                )
            }

            composable(Routes.START_FOR) { entry ->
                StartWorkDestination(
                    navController,
                    entry.arguments?.getString("customerId"),
                )
            }

            composable(Routes.PAST_WORK) {
                val vm: PastWorkViewModel = appViewModel { PastWorkViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                var picking by remember { mutableStateOf(false) }
                var pickingDate by remember { mutableStateOf(false) }
                var pickingFrom by remember { mutableStateOf(false) }
                var pickingTo by remember { mutableStateOf(false) }

                PastWorkScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onDay = vm::setDay,
                    onPickDate = { pickingDate = true },
                    onPickCustomer = { picking = true },
                    onMachine = vm::selectMachine,
                    onWorkType = vm::selectWorkType,
                    onBillingMode = vm::setBillingMode,
                    onUnit = vm::setUnit,
                    onQuantity = vm::setQuantity,
                    onRate = vm::setRate,
                    onPickFrom = { pickingFrom = true },
                    onPickTo = { pickingTo = true },
                    onForgetTimes = vm::forgetTimes,
                    onSave = {
                        // Straight into the bill: the reason for typing in old work
                        // is that it has not been charged for yet.
                        vm.save { jobId ->
                            navController.navigate(Routes.finish(jobId)) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    },
                )

                if (picking) {
                    PickCustomerSheet(
                        customers = state.customers,
                        query = state.query,
                        onQuery = vm::setQuery,
                        onPick = { vm.selectCustomer(it); picking = false },
                        onDismiss = { picking = false },
                    )
                }
                if (pickingDate) {
                    DayPickerSheet(
                        selectedMillis = state.dayMillis,
                        onDismiss = { pickingDate = false },
                        onPicked = { vm.setDay(it); pickingDate = false },
                    )
                }
                if (pickingFrom) {
                    ClockSheet(
                        minutes = state.fromMinutes,
                        onDismiss = { pickingFrom = false },
                        onPicked = { vm.setFrom(it); pickingFrom = false },
                    )
                }
                if (pickingTo) {
                    ClockSheet(
                        minutes = state.toMinutes,
                        onDismiss = { pickingTo = false },
                        onPicked = { vm.setTo(it); pickingTo = false },
                    )
                }
            }

            composable(Routes.CUSTOMERS) {
                val vm: CustomersViewModel = appViewModel { CustomersViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                CustomersScreen(
                    state = state,
                    onBack = null,
                    onQuery = vm::setQuery,
                    onToggleOwing = vm::toggleOnlyOwing,
                    onAdd = { navController.navigate(Routes.NEW_CUSTOMER) },
                    onOpen = { navController.navigate(Routes.ledger(it)) },
                )
            }

            composable(Routes.LEDGER) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                val vm: LedgerViewModel = appViewModel(key = "ledger-$customerId") {
                    LedgerViewModel(it, customerId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableStateOf(CustomerTab.Khata) }
                val context = LocalContext.current
                val reminder = stringResource(
                    R.string.ledger_reminder_text,
                    state.businessName.ifBlank { stringResource(R.string.app_name) },
                    rupees(state.balanceRupees),
                )

                LedgerScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onCall = {
                        state.customerPhone?.let { phone ->
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            )
                        }
                    },
                    onRecordPayment = { navController.navigate(Routes.pay(customerId)) },
                    // Opens the job's own record. Correcting it is one of the things
                    // offered there, rather than the only thing a tap can mean.
                    onOpenJob = { id -> navController.navigate("jobs/$id") },
                    onStatement = {
                        navController.navigate("customer/$customerId/statement")
                    },
                    onFields = { navController.navigate(Routes.fields(customerId)) },
                    onNewJob = {
                        navController.navigate("start/$customerId") { launchSingleTop = true }
                    },
                    tab = tab,
                    onTab = { tab = it },
                    // Keyed by the JOB's id, which is what a payment points at — a
                    // ledger entry id here would silently match nothing.
                    jobTitles = state.jobs.mapNotNull { job ->
                        job.jobId?.to(buildString {
                            append(job.workType)
                            if (job.billingMode != BillingMode.Fixed && job.quantity > 0) {
                                append(" ")
                                append(quantity(job.quantity))
                                append(" ")
                                append(stringResource(job.unit.labelRes))
                            }
                        })
                    }.toMap(),
                    onEdit = if (canEditCustomers) {
                        { navController.navigate(Routes.editCustomer(customerId)) }
                    } else null,
                    onRemind = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reminder)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                )
            }

            composable(Routes.EDIT_CUSTOMER) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                val vm: EditCustomerViewModel = appViewModel(key = "edit-$customerId") {
                    EditCustomerViewModel(it, customerId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val allowed by vm.allowed.collectAsStateWithLifecycle()
                AddCustomerScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onName = vm::setName,
                    onPhone = vm::setPhone,
                    onVillage = vm::setVillage,
                    onOpening = null,
                    onSave = { vm.save { navController.popBackStack() } },
                    titleRes = R.string.edit_customer_title,
                    saveLabelRes = R.string.edit_save,
                    readOnlyNotice = if (allowed) null
                    else stringResource(R.string.edit_not_allowed),
                )
            }

            composable(Routes.STATEMENT) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                val vm: StatementViewModel = appViewModel(key = "statement-$customerId") {
                    StatementViewModel(it, customerId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var pickingDates by remember { mutableStateOf(false) }
                val chooserTitle = stringResource(R.string.statement_chooser)
                val failed = stringResource(R.string.statement_failed)
                val document = statementDocumentFor(vm.sources())

                StatementScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onPeriod = vm::setPeriod,
                    onPickDates = { pickingDates = true },
                    onContent = vm::setContent,
                    onPickJobs = { navController.navigate("customer/$customerId/statement/jobs") },
                    onPreview = {
                        scope.launch {
                            runCatching { DocumentShare.print(context, document) }
                                .onFailure { Toast.makeText(context, failed, Toast.LENGTH_LONG).show() }
                        }
                    },
                    onSend = {
                        if (!state.working) {
                            vm.setWorking(true)
                            scope.launch {
                                runCatching { DocumentShare.share(context, document, chooserTitle) }
                                    .onFailure { Toast.makeText(context, failed, Toast.LENGTH_LONG).show() }
                                vm.setWorking(false)
                            }
                        }
                    },
                )

                if (pickingDates) {
                    DateRangeSheet(
                        onDismiss = { pickingDates = false },
                        onPicked = { start, end ->
                            vm.setCustomRange(start, end)
                            pickingDates = false
                        },
                    )
                }
            }

            composable(Routes.STATEMENT_JOBS) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                // Scoped to the statement screen's own nav entry rather than this
                // one, so a tick here is the same selection its summary is counting.
                // A plain appViewModel would hand this route a second instance.
                val parent = remember(entry) { navController.getBackStackEntry(Routes.STATEMENT) }
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val vm: StatementViewModel = viewModel(
                    viewModelStoreOwner = parent,
                    key = "statement-$customerId",
                    factory = rememberSimpleFactory { StatementViewModel(app, customerId) },
                )
                val state by vm.state.collectAsStateWithLifecycle()
                StatementPickJobsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onToggle = vm::togglePicked,
                    onClear = vm::clearPicked,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.PAY) { entry ->
                val customerId = entry.arguments?.getString("customerId").orEmpty()
                val vm: RecordPaymentViewModel = appViewModel(key = "pay-$customerId") {
                    RecordPaymentViewModel(it, customerId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val business = state.customerName

                RecordPaymentScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAmount = vm::setAmount,
                    onTakeAll = vm::takeAll,
                    onMethod = vm::setMethod,
                    onSaveAndSend = {
                        vm.save { paid ->
                            val text = context.getString(
                                R.string.ledger_reminder_text,
                                business,
                                rupees((state.owedRupees - paid).coerceAtLeast(0)),
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                            navController.popBackStack()
                        }
                    },
                    onJustSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.JOBS) {
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val startup by app.sessionStore.startup.collectAsStateWithLifecycle()

                // The same tab, a different list. An owner's jobs are the yard's
                // work; a driver's are their own, and showing them everybody's
                // would be handing them a list they cannot act on and did not do.
                if ((startup as? Startup.Ready)?.session?.role == MemberRole.Operator) {
                    val driverVm: DriverJobsViewModel =
                        appViewModel { DriverJobsViewModel(it) }
                    val driverState by driverVm.state.collectAsStateWithLifecycle()
                    DriverJobsScreen(
                        state = driverState,
                        onPeriod = driverVm::setPeriod,
                        onAddPastWork = { navController.navigate(Routes.PAST_WORK) },
                        onOpenJob = { id -> navController.navigate("jobs/$id") },
                    )
                    return@composable
                }

                val vm: JobsViewModel = appViewModel { JobsViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                JobsScreen(
                    state = state,
                    onFilter = vm::setFilter,
                    onOpen = { job -> navController.navigate("jobs/${job.id}") },
                )
            }

            composable(Routes.JOB) { entry ->
                val jobId = entry.arguments?.getString("jobId").orEmpty()
                val vm: JobDetailViewModel = appViewModel(key = "job-$jobId") {
                    JobDetailViewModel(it, jobId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current

                JobDetailScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onOpenLive = { navController.navigate(Routes.live(jobId)) },
                    onAmend = { navController.navigate("jobs/$jobId/amend") }
                        .takeIf { state.canAmend && state.finished },
                    // A khata is the money record. There is no useful version of
                    // it with the money taken out, so somebody who may not see
                    // amounts does not open it — rather than opening an empty one.
                    onOpenCustomer = {
                        if (state.customerId.isNotBlank() && state.canSeeMoney) {
                            navController.navigate("customer/${'$'}{state.customerId}")
                        }
                    },
                    onCall = state.operatorPhone?.takeIf { it.isNotBlank() }?.let { number ->
                        {
                            val dial = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${'$'}number")
                            }
                            runCatching { context.startActivity(dial) }
                            Unit
                        }
                    },
                    // Offered to anyone who may record work, because the person
                    // who knows how many hands were on it is the one who was there.
                    onAddCost = { navController.navigate(Routes.jobCost(jobId)) }
                        .takeIf { state.canRecordCost },
                    onPayCost = { costId: String ->
                        navController.navigate(Routes.jobCost(jobId, costId))
                    }.takeIf { state.canPriceCost },
                )
            }

            composable(
                Routes.JOB_COST,
                arguments = listOf(
                    navArgument("costId") { defaultValue = ""; nullable = false },
                ),
            ) { entry ->
                val jobId = entry.arguments?.getString("jobId").orEmpty()
                val costId = entry.arguments?.getString("costId").orEmpty().ifBlank { null }
                val vm: JobCostViewModel = appViewModel(key = "jobcost-${'$'}jobId-${'$'}costId") {
                    JobCostViewModel(it, jobId, costId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                JobCostScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onKind = vm::setKind,
                    onHeadcount = vm::setHeadcount,
                    onUncounted = vm::setUncounted,
                    onCounted = vm::setCounted,
                    onAmount = vm::setAmount,
                    onNote = vm::setNote,
                    onPaid = vm::setPaid,
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.AMEND) { entry ->
                val jobId = entry.arguments?.getString("jobId").orEmpty()
                val vm: AmendJobViewModel = appViewModel(key = "amend-$jobId") {
                    AmendJobViewModel(it, jobId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                AmendJobScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onQuantity = vm::setQuantity,
                    onRate = vm::setRate,
                    onReason = vm::setReason,
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.FLEET) {
                val vm: FleetViewModel = appViewModel { FleetViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                FleetScreen(
                    state = state,
                    onSeeAllMachines = { navController.navigate(Routes.MACHINES) },
                    onUpkeep = { navController.navigate(Routes.UPKEEP) },
                )
            }

            composable(Routes.UPKEEP) {
                val vm: UpkeepViewModel = appViewModel { UpkeepViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                val draft by vm.draft.collectAsStateWithLifecycle()
                UpkeepScreen(
                    state = state,
                    draft = draft,
                    onBack = { navController.popBackStack() },
                    onMarkRepaired = vm::markRepaired,
                    onLogRepair = vm::logRepair,
                    onLogService = { vm.logService() },
                    onDraftMachine = vm::setDraftMachine,
                    onDraftDescription = vm::setDraftDescription,
                    onDraftCost = vm::setDraftCost,
                    onDraftVendor = vm::setDraftVendor,
                    onDraftSave = vm::saveDraft,
                    onDraftDismiss = vm::dismissDraft,
                )
            }

            composable(Routes.MACHINES) {
                val vm: MachinesViewModel = appViewModel { MachinesViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                // Whether this is a tab depends on the track, so whether it needs a
                // way back does too.
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val business by app.sessionStore.businessMode.collectAsStateWithLifecycle()
                MachinesScreen(
                    state = state,
                    onAdd = { navController.navigate(Routes.NEW_MACHINE) },
                    // Opens the machine rather than the form. Everyone can look at
                    // what a machine has been doing; only an owner can rename it,
                    // and that now lives behind the pencil on the detail screen.
                    onEdit = { id -> navController.navigate("machines/$id") },
                    // A tab for a solo owner, a pushed screen for a business one.
                    onBack = if (business) ({ navController.popBackStack(); Unit }) else null,
                )
            }

            composable(Routes.MACHINE) { entry ->
                val machineId = entry.arguments?.getString("machineId").orEmpty()
                val vm: MachineDetailViewModel = appViewModel {
                    MachineDetailViewModel(it, machineId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val reporting by vm.reporting.collectAsStateWithLifecycle()
                val service by vm.service.collectAsStateWithLifecycle()
                MachineDetailScreen(
                    state = state,
                    reporting = reporting,
                    service = service,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("machines/$machineId/edit") }
                        .takeIf { state.canEdit },
                    onFuel = { navController.navigate("machines/$machineId/fuel") },
                    // Service and Breakdown are different things, and used to go to
                    // the same fleet-wide screen. Service logs work done on THIS
                    // machine; Breakdown says this machine is broken now.
                    // Both open a sheet here rather than sending the user to the
                    // fleet-wide screen. Tapping Service used to land them on a
                    // different page with a sheet over it, and dismissing it left
                    // them somewhere they never asked to be.
                    onService = vm::logService,
                    onBreakdown = vm::reportBreakdown,
                    onWhatItMade = {
                        navController.navigate("machines/$machineId/made")
                    },
                    onSaveBreakdown = vm::saveBreakdown,
                    onDismissReport = vm::dismissReport,
                    onServiceDescription = vm::setServiceDescription,
                    onServiceCost = vm::setServiceCost,
                    onServiceVendor = vm::setServiceVendor,
                    onSaveService = vm::saveService,
                    onDismissService = vm::dismissService,
                    onOpenJob = { id -> navController.navigate("jobs/$id") },
                )
            }

            composable(Routes.FUEL) { entry ->
                val machineId = entry.arguments?.getString("machineId").orEmpty()
                val vm: FuelLogViewModel = appViewModel { FuelLogViewModel(it, machineId) }
                val state by vm.state.collectAsStateWithLifecycle()
                val draft by vm.draft.collectAsStateWithLifecycle()
                FuelLogScreen(
                    state = state,
                    draft = draft,
                    onBack = { navController.popBackStack() },
                    onAdd = vm::add,
                    onLitres = vm::setLitres,
                    onCost = vm::setCost,
                    onSave = vm::save,
                    onDismiss = vm::dismiss,
                )
            }

            composable(Routes.NEW_MACHINE) {
                val vm: AddMachineViewModel = appViewModel { AddMachineViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                AddMachineScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onKind = vm::setKind,
                    onName = vm::setName,
                    onPlate = vm::setPlate,
                    onHours = vm::setHours,
                    onServiceEvery = vm::setServiceEvery,
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.EDIT_MACHINE) { entry ->
                val machineId = entry.arguments?.getString("machineId").orEmpty()
                val vm: EditMachineViewModel = appViewModel(key = "edit-machine-$machineId") {
                    EditMachineViewModel(it, machineId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val allowed by vm.allowed.collectAsStateWithLifecycle()
                AddMachineScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onKind = vm::setKind,
                    onName = vm::setName,
                    onPlate = vm::setPlate,
                    onHours = vm::setHours,
                    onServiceEvery = vm::setServiceEvery,
                    onSave = { vm.save { navController.popBackStack() } },
                    titleRes = R.string.edit_machine_title,
                    saveLabelRes = R.string.edit_save,
                    readOnlyNotice = if (allowed) null
                    else stringResource(R.string.edit_not_allowed),
                )
            }

            composable(Routes.MORE) {
                val vm: MoreViewModel = appViewModel { MoreViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                MoreScreen(
                    state = state,
                    onEditProfile = { navController.navigate(Routes.PROFILE) },
                    onTeam = { navController.navigate(Routes.TEAM) },
                    onEditBusiness = if (state.canEditBusiness) {
                        { navController.navigate(Routes.EDIT_BUSINESS) }
                    } else null,
                    onBackup = { navController.navigate(Routes.BACKUP) },
                    onRates = { navController.navigate(Routes.RATES) },
                    onMoneyOut = { navController.navigate(Routes.MONEY_OUT) },
                    onMachines = { navController.navigate(Routes.MACHINES) },
                )
            }

            composable(Routes.RATES) {
                val vm: RateCardViewModel = appViewModel { RateCardViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                val draft by vm.draft.collectAsStateWithLifecycle()
                val confirmDelete by vm.confirmDelete.collectAsStateWithLifecycle()
                RateCardScreen(
                    state = state,
                    draft = draft,
                    confirmDelete = confirmDelete,
                    onBack = { navController.popBackStack() },
                    onAdd = vm::add,
                    onEdit = vm::edit,
                    onPerCustomer = vm::setPerCustomer,
                    onDraftWorkType = vm::setWorkType,
                    onDraftBillingMode = vm::setBillingMode,
                    onDraftUnit = vm::setUnit,
                    onDraftAmount = vm::setAmount,
                    onDraftLabel = vm::setLabel,
                    onDraftSave = vm::saveDraft,
                    onDraftDismiss = vm::dismissDraft,
                    onAskDelete = vm::askDelete,
                    onConfirmDelete = vm::confirmDeleteNow,
                    onDismissDelete = vm::dismissDelete,
                )
            }

            composable(Routes.BACKUP) {
                val vm: BackupViewModel = appViewModel { BackupViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                BackupScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onBackUpNow = vm::backUpNow,
                    onSetAside = vm::setAsideBlocked,
                )
            }

            composable(Routes.TEAM) {
                val vm: TeamViewModel = appViewModel { TeamViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val scope = rememberCoroutineScope()
                var adding by remember { mutableStateOf(false) }

                TeamScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onOpenMember = { id -> navController.navigate("more/team/$id") },
                    onAddMember = { adding = true }.takeIf { state.canEditTeam },
                    onInvite = { navController.navigate(Routes.INVITE) }
                        .takeIf { state.canEditTeam },
                    onSentCodes = { navController.navigate(Routes.SENT_CODES) }
                        .takeIf { state.canEditTeam },
                    onHandOver = { navController.navigate(Routes.HAND_OVER) }
                        .takeIf { state.canEditTeam },
                    onChanges = { navController.navigate(Routes.TEAM_CHANGES) }
                        .takeIf { state.canEditTeam },
                    onMergeDuplicate = if (state.canEditTeam) {
                        { pair: DuplicatePair -> vm.merge(pair) {} }
                    } else null,
                    onNotDuplicate = if (state.canEditTeam) {
                        { pair: DuplicatePair -> vm.notTheSamePerson(pair) }
                    } else null,
                )

                if (adding) {
                    AddMemberSheet(
                        onDismiss = { adding = false },
                        onAdd = { name, phone ->
                            adding = false
                            scope.launch {
                                val session = app.sessionStore.load() ?: return@launch
                                runCatching {
                                    app.teamRepository.addMember(
                                        orgId = session.orgId,
                                        can = session.can,
                                        displayName = name,
                                        phone = phone,
                                    )
                                }
                            }
                        },
                    )
                }
            }

            composable(Routes.MACHINE_PROFIT) { entry ->
                val machineId = entry.arguments?.getString("machineId").orEmpty()
                val vm: MachineProfitViewModel =
                    appViewModel { MachineProfitViewModel(it, machineId) }
                val state by vm.state.collectAsStateWithLifecycle()
                MachineProfitScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSeeMoneyOut = { navController.navigate(Routes.MONEY_OUT) },
                )
            }

            composable(Routes.MONEY_OUT) {
                val vm: MoneyOutViewModel = appViewModel { MoneyOutViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                MoneyOutScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onPay = { navController.navigate(Routes.PAY_OUT) },
                )
            }

            composable(Routes.PAY_OUT) {
                val vm: PayOutViewModel = appViewModel { PayOutViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                PayOutScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onPerson = vm::setPerson,
                    onHeadcount = vm::setHeadcount,
                    onAmount = vm::setAmount,
                    onCovers = vm::setCovers,
                    onJob = vm::setJob,
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.TEAM_CHANGES) {
                val vm: TeamActivityViewModel = appViewModel { TeamActivityViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                TeamActivityScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.HAND_OVER) {
                val vm: HandOverViewModel = appViewModel { HandOverViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                HandOverScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onChoose = vm::choose,
                    onStepBack = vm::setStepBack,
                    onConfirm = { vm.confirm { navController.popBackStack() } },
                )
            }

            composable(Routes.SENT_CODES) {
                val vm: SentCodesViewModel = appViewModel { SentCodesViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val businessName = state.businessName
                SentCodesScreen(
                    state = state,
                    nowMillis = vm.now(),
                    onBack = { navController.popBackStack() },
                    onShare = { code ->
                        val text = context.getString(
                            R.string.invite_share_text, businessName, code,
                        )
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                null,
                            )
                        )
                    },
                    onRevoke = vm::revoke,
                    onInviteMore = { navController.navigate(Routes.INVITE) },
                )
            }

            composable(Routes.INVITE) {
                val vm: InviteViewModel = appViewModel { InviteViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val shareText = stringResource(
                    R.string.invite_share_text, state.businessName, state.code.orEmpty(),
                )
                val copiedMessage = stringResource(R.string.invite_copied)
                InviteScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onMakeCode = vm::makeCode,
                    onNewCode = vm::newCode,
                    onRevoke = vm::revoke,
                    onCopy = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("code", it))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        // Plain ACTION_SEND rather than a WhatsApp-only intent: the
                        // chooser reaches whatever the owner actually uses, and does
                        // not fail on a phone without WhatsApp installed.
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                },
                                null,
                            )
                        )
                    },
                    onSeeMoney = vm::setSeeMoney,
                    onAddCustomers = vm::setAddCustomers,
                    onCloseBills = vm::setCloseBills,
                    daysLeft = { expiresAt ->
                        // Rounded up, not truncated. A code made a moment ago lasts
                        // seven days; dividing leaves 6.99 and floor turns that into
                        // "works for 6 days", which is the app understating itself
                        // and an owner wondering what happened to the seventh.
                        val remaining = (expiresAt - System.currentTimeMillis())
                            .coerceAtLeast(0L)
                        ((remaining + 86_399_999L) / 86_400_000L).toInt()
                    },
                )
            }

            composable(Routes.MEMBER) { entry ->
                val membershipId = entry.arguments?.getString("membershipId").orEmpty()
                val vm: MemberViewModel = appViewModel(key = "member-$membershipId") {
                    MemberViewModel(it, membershipId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current

                MemberScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onName = vm::setName,
                    onPhone = vm::setPhone,
                    onPreset = vm::setPreset,
                    onSeeMoney = vm::setSeeMoney,
                    onAddCustomers = vm::setAddCustomers,
                    onCloseBills = vm::setCloseBills,
                    onEditTeam = vm::setEditTeam,
                    onEditMachines = vm::setEditMachines,
                    onPayOut = vm::setPayOut,
                    onSeePeopleCost = vm::setSeePeopleCost,
                    onSave = { vm.save { navController.popBackStack() } },
                    onSetActive = { active ->
                        vm.setActive(active) { navController.popBackStack() }
                    },
                    onCall = state.phone?.takeIf { it.isNotBlank() }?.let { number ->
                        {
                            val dial = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:$number")
                            }
                            runCatching { context.startActivity(dial) }
                            Unit
                        }
                    },
                )
            }

            composable(Routes.JOIN_ANOTHER) {
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val vm: JoinViewModel = viewModel {
                    JoinViewModel(
                        lookup = app.inviteLookup,
                        suggestedName = app.sessionStore.current()?.displayName.orEmpty(),
                        onJoin = { invite, name ->
                            val personId = app.sessionStore.personIdOrNull()
                                ?: return@JoinViewModel
                            app.teamRepository.redeemInvite(
                                invite = invite,
                                personId = personId,
                                displayName = name,
                                phone = app.sessionStore.signedInPhone(),
                            )
                            // Joining is also switching: somebody who types a code
                            // means to start working for those people now.
                            app.sessionStore.adoptMembership(invite.orgId)
                        },
                    )
                }
                val joinState by vm.state.collectAsStateWithLifecycle()
                BackHandler(enabled = joinState.step == JoinStep.Preview) { vm.back() }
                JoinScreen(
                    state = joinState,
                    removedFrom = null,
                    onCode = vm::setCode,
                    onName = vm::setName,
                    onCheck = vm::check,
                    onJoin = vm::join,
                    onBack = {
                        if (joinState.step == JoinStep.Preview) vm.back()
                        else navController.popBackStack()
                    },
                    // Already in a business; there is nothing to skip to.
                    onSkip = null,
                )
            }

            composable(Routes.ORGS) {
                val vm: OrgSwitcherViewModel = appViewModel { OrgSwitcherViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                OrgSwitcherScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSwitch = { orgId ->
                        // Switching changes what the whole app is looking at, so
                        // it goes home rather than leaving the user on a screen
                        // about a business they have just stopped looking at.
                        vm.switchTo(orgId) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    },
                    onJoinAnother = { navController.navigate(Routes.JOIN_ANOTHER) },
                    onDismissRefusal = vm::clearRefusal,
                )
            }

            composable(Routes.PROFILE) {
                val app = LocalContext.current.applicationContext as FieldFlowApplication
                val scope = rememberCoroutineScope()
                val activity = LocalContext.current as? android.app.Activity
                var unsent by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) { unsent = app.sessionStore.unsentCount() }
                val vm: ProfileViewModel = appViewModel { ProfileViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onEditName = { navController.navigate(Routes.EDIT_PROFILE) },
                    onEditBusiness = { navController.navigate(Routes.EDIT_BUSINESS) },
                    onBusinessMode = vm::setBusinessMode,
                    onBusinesses = { navController.navigate(Routes.ORGS) },
                    onSignOut = {
                        scope.launch {
                            app.sessionStore.signOut(
                                FirebasePhoneAuth(activityProvider = { activity })
                            )
                        }
                    },
                    // The only owner cannot leave: a business nobody owns can
                    // never be changed again by anyone.
                    onLeave = if (state.canLeave) {
                        {
                            scope.launch {
                                runCatching {
                                    app.teamRepository.leaveOrg(state.orgId, state.personId)
                                    app.sessionStore.signOut(null)
                                }
                            }
                        }
                    } else null,
                    unsentCount = unsent,
                )
            }

            composable(Routes.EDIT_PROFILE) {
                val vm: EditProfileViewModel = appViewModel { EditProfileViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                EditSettingsScreen(
                    state = state,
                    titleRes = R.string.edit_profile_title,
                    nameLabelRes = R.string.onboard_your_name,
                    nameHintRes = R.string.onboard_your_name_hint,
                    onBack = { navController.popBackStack() },
                    onName = vm::setName,
                    onLandUnit = null,
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.EDIT_BUSINESS) {
                val vm: EditBusinessViewModel = appViewModel { EditBusinessViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                EditSettingsScreen(
                    state = state,
                    titleRes = R.string.edit_business_title,
                    nameLabelRes = R.string.onboard_business_name,
                    nameHintRes = R.string.onboard_business_name_hint,
                    helperRes = R.string.edit_business_name_note,
                    onBack = { navController.popBackStack() },
                    onName = vm::setName,
                    onLandUnit = vm::setLandUnit,
                    onLetterhead = LetterheadFields(
                        onPhone = vm::setPhone,
                        onAddress = vm::setAddress,
                        onGstin = vm::setGstin,
                        onUpiId = vm::setUpiId,
                    ),
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.NEW_CUSTOMER) {
                val vm: AddCustomerViewModel = appViewModel { AddCustomerViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                AddCustomerScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onName = vm::setName,
                    onPhone = vm::setPhone,
                    onVillage = vm::setVillage,
                    onOpening = vm::setOpening,
                    onSave = { vm.save { navController.popBackStack() } },
                )
            }

            composable(Routes.LIVE) { entry ->
                val jobId = entry.arguments?.getString("jobId").orEmpty()
                val vm: LiveWorkViewModel = appViewModel(key = jobId) {
                    LiveWorkViewModel(it, jobId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                LiveWorkScreen(
                    state = state,
                    onIncrease = { vm.increase() },
                    onDecrease = { vm.decrease() },
                    onAddTrip = { vm.addTrip() },
                    onUndoTrip = { vm.undoTrip() },
                    onPause = { vm.pause() },
                    onResume = { vm.resume() },
                    onFinish = { navController.navigate(Routes.finish(jobId)) },
                    onNote = vm::addNote,
                    onRate = vm::setRate,
                    onFuel = vm::addFuel,
                    onBreakdown = vm::reportBreakdown,
                    onHandOver = vm::handOver,
                    // The job row is gone, so the screen showing it has to go too.
                    onDiscard = { vm.discard { navController.popBackStack() } },
                    onAmount = vm::setQuantity,
                )
            }

            composable(Routes.FINISH) { entry ->
                val jobId = entry.arguments?.getString("jobId").orEmpty()
                val vm: FinishViewModel = appViewModel(key = "finish-$jobId") {
                    FinishViewModel(it, jobId)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val payment by vm.payment.collectAsStateWithLifecycle()
                val step by vm.step.collectAsStateWithLifecycle()
                val received by vm.receivedRupees.collectAsStateWithLifecycle()
                val finalBalance by vm.finalBalance.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val goHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }

                val receiptText = receiptFor(state, received, finalBalance ?: 0L)
                val billDocument = billDocumentFor(
                    state = state,
                    receivedRupees = received,
                    finalBalanceRupees = finalBalance ?: 0L,
                    method = payment.method,
                )
                val scope = rememberCoroutineScope()
                var makingPdf by remember { mutableStateOf(false) }
                val chooserTitle = stringResource(R.string.finish_pdf_chooser)
                val pdfFailed = stringResource(R.string.finish_pdf_failed)
                val labour by vm.labour.collectAsStateWithLifecycle()

                FinishScreen(
                    step = step,
                    state = state,
                    payment = payment,
                    receivedRupees = received,
                    finalBalanceRupees = finalBalance ?: 0L,
                    onBack = { if (vm.back()) navController.popBackStack() },
                    onToBill = vm::toBill,
                    onChooseAll = vm::chooseAll,
                    onChooseSome = vm::chooseSome,
                    onChooseNothing = vm::chooseNothing,
                    onAmount = vm::setAmount,
                    onMethod = vm::setMethod,
                    labour = labour,
                    onAnyLabour = vm::setAnyLabour,
                    onLabourHeadcount = vm::setLabourHeadcount,
                    onLabourUncounted = vm::setLabourUncounted,
                    onLabourCounted = vm::setLabourCounted,
                    onSave = { vm.save {} },
                    makingPdf = makingPdf,
                    onPdf = {
                        if (!makingPdf) {
                            makingPdf = true
                            scope.launch {
                                runCatching {
                                    DocumentShare.share(context, billDocument, chooserTitle)
                                }.onFailure {
                                    Toast.makeText(context, pdfFailed, Toast.LENGTH_LONG).show()
                                }
                                makingPdf = false
                            }
                        }
                    },
                    onPrint = {
                        scope.launch {
                            runCatching { DocumentShare.print(context, billDocument) }
                                .onFailure {
                                    Toast.makeText(context, pdfFailed, Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    onSms = {
                        // An SMS goes to the number on the customer record when there
                        // is one; otherwise the chooser lets them pick a recipient.
                        val sms = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse(
                                "smsto:" + state.customerPhone.orEmpty().filter(Char::isDigit)
                            )
                            putExtra("sms_body", receiptText)
                        }
                        runCatching { context.startActivity(sms) }
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(state.customerName, receiptText)
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.finish_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onShare = {
                        // A plain text share for now: it reaches WhatsApp, SMS and
                        // anything else installed. The rendered image receipt from the
                        // designs needs a bitmap pipeline and comes with the khata work.
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, receiptText)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    onDone = { goHome() },
                )
            }
        }
}

/**
 * The finished job as a printable bill.
 *
 * Built here rather than in the view model because every label on it — the unit, the
 * payment method, the date format — is a resource, and a bill with the wrong word for
 * "bigha" on it is worse than no bill.
 */
@Composable
private fun billDocumentFor(
    state: com.example.fieldtrack.feature.work.FinishState,
    receivedRupees: Long,
    finalBalanceRupees: Long,
    method: com.example.fieldtrack.data.db.PaymentMethod?,
): Document.Bill {
    val unit = stringResource(state.unit.labelRes)
    val dateLabel = dayMonthYear(state.billedAtMillis ?: System.currentTimeMillis())
    val methodLabel = stringResource(
        when (method) {
            com.example.fieldtrack.data.db.PaymentMethod.Upi -> R.string.method_upi
            com.example.fieldtrack.data.db.PaymentMethod.Bank -> R.string.method_bank
            com.example.fieldtrack.data.db.PaymentMethod.Cheque -> R.string.method_cheque
            else -> R.string.method_cash
        }
    )
    return Document.Bill(
        letterhead = Letterhead(
            businessName = state.businessName,
            addressLine = state.businessAddress?.ifBlank { null },
            phone = state.businessPhone?.ifBlank { null },
            gstin = state.businessGstin?.ifBlank { null },
            upiId = state.businessUpiId?.ifBlank { null },
        ),
        customer = Party(
            name = state.customerName,
            addressLine = state.customerVillage?.ifBlank { null },
            phone = state.customerPhone?.ifBlank { null }?.let { formatIndianPhone(it) },
        ),
        billNumber = state.billNumber,
        dateLabel = dateLabel,
        workedOnLabel = dateLabel,
        machineLabel = state.machineName,
        lines = listOf(
            WorkLine(
                title = state.workType,
                detail = null,
                measure = "${quantity(state.quantity)} $unit",
                rate = if (state.ratesVaried) {
                    stringResource(R.string.finish_rate_varies)
                } else {
                    stringResource(
                        R.string.start_rate_per_amount,
                        rupees(state.rateRupees),
                        unit,
                    )
                },
                amountRupees = state.jobAmountRupees,
            )
        ),
        previousBalanceRupees = state.previousBalanceRupees,
        payments = if (receivedRupees > 0) {
            listOf(PaymentLine(dateLabel, methodLabel, receivedRupees))
        } else emptyList(),
        balanceRupees = finalBalanceRupees.coerceAtLeast(0),
        balanceInWords = rupeesInWords(finalBalanceRupees.coerceAtLeast(0)),
    )
}

/** The receipt as plain text, in the order the paper bill reads. */
@Composable
private fun receiptFor(
    state: com.example.fieldtrack.feature.work.FinishState,
    receivedRupees: Long,
    finalBalanceRupees: Long,
): String {
    val unit = stringResource(state.unit.labelRes)
    val remaining = finalBalanceRupees.coerceAtLeast(0)
    return buildString {
        appendLine(state.businessName.ifBlank { stringResource(R.string.app_name) })
        // The letterhead, each line only when the business has filled it in.
        listOfNotNull(
            state.businessAddress?.takeIf { it.isNotBlank() },
            state.businessPhone?.takeIf { it.isNotBlank() }?.let { formatIndianPhone(it) },
        ).joinToString(" · ").takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine()
        appendLine("${state.customerName}")
        appendLine("${state.workType} ${quantity(state.quantity)} $unit — ${rupees(state.jobAmountRupees)}")
        if (state.previousBalanceRupees != 0L) {
            appendLine("${stringResource(R.string.money_old_balance)} — ${rupees(state.previousBalanceRupees)}")
        }
        if (receivedRupees > 0) {
            appendLine("${stringResource(R.string.money_paid_today)} — ${rupees(receivedRupees)}")
        }
        appendLine("${stringResource(R.string.money_balance)} — ${rupees(remaining)}")
        state.businessUpiId?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(stringResource(R.string.finish_pay_upi, it))
        }
        appendLine()
        // The same footer the printed bill carries, so a WhatsApp message and a
        // paper copy of the same job can be matched up when one is disputed.
        appendLine(
            listOfNotNull(
                state.billedAtMillis?.let { dayMonthYear(it) },
                state.billNumber?.let { stringResource(R.string.finish_bill_no, it) },
                stringResource(R.string.finish_made_with),
            ).joinToString(" · ")
        )
    }
}

/** Writing a plot down, or correcting one. One screen, two ways in. */
@Composable
private fun FieldEditDestination(
    navController: NavHostController,
    customerId: String,
    fieldId: String?,
) {
    val vm: FieldEditViewModel = appViewModel(
        key = "field-edit-${'$'}{fieldId ?: customerId}"
    ) {
        FieldEditViewModel(it, customerId, fieldId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    FieldEditScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onName = vm::setName,
        onArea = vm::setArea,
        onUnit = vm::setUnit,
        onCrop = vm::setCrop,
        onDropPin = { vm.dropPin(context) },
        onSave = { vm.save { navController.popBackStack() } },
        // Back twice: the field's own screen is about to stop existing.
        onRemove = {
            vm.remove {
                navController.popBackStack()
                navController.popBackStack()
            }
        },
    )
}

/**
 * The start-work wizard.
 *
 * Pulled out of the graph because it has two entry points — the home button and a
 * customer's own page — and the only difference between them is whether step one has
 * already been answered.
 */
@Composable
private fun StartWorkDestination(
    navController: NavHostController,
    forCustomerId: String?,
    forFieldId: String? = null,
) {
            val vm: StartWorkViewModel = appViewModel(
                key = "start-${forCustomerId.orEmpty()}-${forFieldId.orEmpty()}"
            ) {
        StartWorkViewModel(it, forCustomerId, forFieldId)
    }
            val state by vm.state.collectAsStateWithLifecycle()
            var addingMachine by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            // Back steps through the wizard before it leaves it, so a mis-tap on
            // step three does not throw away the customer picked on step one.
            val leave = { if (vm.back()) navController.popBackStack() else true }
            BackHandler { leave() }

            StartWorkScreen(
                state = state,
                onBack = { leave() },
                onQuery = vm::setQuery,
                onNewCustomer = { navController.navigate(Routes.NEW_CUSTOMER) },
                onNewMachine = { addingMachine = true },
                onSelectCustomer = { vm.selectCustomer(it); vm.next() },
                onSelectMachine = vm::selectMachine,
                onSelectField = vm::selectField,
                onSelectWorkType = vm::selectWorkType,
                onBillingMode = vm::setBillingMode,
                onUnit = vm::setUnit,
                onRate = vm::setRate,
                onNext = vm::next,
                onStart = {
                    scope.launch {
                        vm.start()?.let { jobId ->
                            navController.navigate(Routes.live(jobId)) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    }
                },
            )

            if (addingMachine) {
                NewMachineSheet(
                    onDismiss = { addingMachine = false },
                    onAdd = { name, kind ->
                        vm.createMachine(name, kind)
                        addingMachine = false
                    },
                )
            }
}
