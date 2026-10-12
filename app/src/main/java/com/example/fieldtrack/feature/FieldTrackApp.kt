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

private object Routes {
    const val HOME = "home"
    const val START = "start"
    const val START_FOR = "start/{customerId}"
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
    const val UPKEEP = "fleet/upkeep"
    const val INVITE = "more/team/invite"
    const val MACHINE = "machines/{machineId}"
    const val FUEL = "machines/{machineId}/fuel"
    const val JOBS = "jobs"
    const val JOB = "jobs/{jobId}"
    const val AMEND = "jobs/{jobId}/amend"
    const val FLEET = "fleet"

    /**
     * The tabs the bottom bar switches between, by track.
     *
     * A solo owner has no drivers to watch and no fleet to speak of, so those two
     * tabs would be a dashboard of one. A business owner gets them and reaches
     * individual machines through Fleet instead of a tab of its own.
     */
    fun roots(business: Boolean) =
        if (business) listOf(HOME, JOBS, CUSTOMERS, FLEET, MORE)
        else listOf(HOME, CUSTOMERS, MACHINES, MORE)
    const val LEDGER = "customer/{customerId}"
    const val PAY = "customer/{customerId}/pay"
    const val EDIT_CUSTOMER = "customer/{customerId}/edit"
    const val STATEMENT = "customer/{customerId}/statement"
    const val STATEMENT_JOBS = "customer/{customerId}/statement/jobs"

    fun live(jobId: String) = "live/$jobId"
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
                    JoinScreen(
                        state = joinState,
                        onCode = vm::setCode,
                        onName = vm::setName,
                        onCheck = vm::check,
                        onJoin = vm::join,
                        onBack = {
                            if (joinState.step == JoinStep.Preview) vm.back()
                            else askedForCode = true
                        },
                        onSkip = { askedForCode = true },
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
    val roots = Routes.roots(business)

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
                destinations = bottomDestinations(business),
                selectedIndex = tab,
                onSelect = { index -> navController.switchTab(roots[index]) },
            )
        }
    }
}

@Composable
private fun bottomDestinations(business: Boolean) = buildList {
    add(FtNavDestination(FieldTrackIcons.Home, stringResource(R.string.nav_home)))
    if (business) add(FtNavDestination(FieldTrackIcons.Play, stringResource(R.string.nav_jobs)))
    add(FtNavDestination(FieldTrackIcons.Customer, stringResource(R.string.nav_customers)))
    add(
        if (business) FtNavDestination(FieldTrackIcons.Tractor, stringResource(R.string.nav_fleet))
        else FtNavDestination(FieldTrackIcons.Tractor, stringResource(R.string.nav_machines))
    )
    add(FtNavDestination(FieldTrackIcons.More, stringResource(R.string.nav_more)))
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
                    onOpenCustomer = {
                        if (state.customerId.isNotBlank()) {
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
                    onSeeMoney = vm::setSeeMoney,
                    onAddCustomers = vm::setAddCustomers,
                    onCloseBills = vm::setCloseBills,
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

            composable(Routes.PROFILE) {
                val vm: ProfileViewModel = appViewModel { ProfileViewModel(it) }
                val state by vm.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onEditName = { navController.navigate(Routes.EDIT_PROFILE) },
                    onEditBusiness = { navController.navigate(Routes.EDIT_BUSINESS) },
                    onBusinessMode = vm::setBusinessMode,
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
            phone = state.customerPhone?.ifBlank { null }?.let { "+91 $it" },
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
            state.businessPhone?.takeIf { it.isNotBlank() }?.let { "+91 $it" },
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

/**
 * The start-work wizard.
 *
 * Pulled out of the graph because it has two entry points — the home button and a
 * customer's own page — and the only difference between them is whether step one has
 * already been answered.
 */
@Composable
private fun StartWorkDestination(navController: NavHostController, forCustomerId: String?) {
            val vm: StartWorkViewModel = appViewModel(key = "start-${forCustomerId.orEmpty()}") {
        StartWorkViewModel(it, forCustomerId)
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
