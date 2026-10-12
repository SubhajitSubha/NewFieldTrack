package com.example.fieldtrack

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.fieldtrack.core.onboarding.OnboardingPersistence
import com.example.fieldtrack.core.onboarding.production.ProductionOnboardingStep
import com.example.fieldtrack.core.onboarding.production.ProductionOnboardingPersistence
import com.example.fieldtrack.core.onboarding.production.ProductionOnboardingEngine
import com.example.fieldtrack.core.session.CurrentSessionProvider
import com.example.fieldtrack.core.session.identityfoundation.IdentitySessionState
import com.example.fieldtrack.core.startup.AppStartupState
import com.example.fieldtrack.core.startup.StartupStateResolver
import com.example.fieldtrack.ui.navigation.FieldFlowNavGraph
import com.example.fieldtrack.ui.navigation.Screen
import com.example.fieldtrack.ui.startup.StartupSplashScreen
import com.example.fieldtrack.ui.theme.FieldFlowTheme
import com.example.fieldtrack.data.local.entity.AppSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        /** Logcat tag for R2 identity-flow tracing (debug builds only). */
        const val TAG_IDENTITY_FLOW = "IdentityFlow"

        /** R4.2 — Bug-hunt tag for fresh-install onboarding diagnostics. */
        const val TAG_IDENTITY_FLOW_DEBUG = "IdentityFlowDebug"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get repositories from Application
        val app = application as FieldFlowApplication
        OnboardingPersistence.init(app)
        ProductionOnboardingPersistence.init(app)
        val jobRepository = app.jobRepository
        val customerRepository = app.customerRepository
        val ratePreferences = app.ratePreferences
        val customerFieldDao = app.customerFieldDao
        val activeJobRepository = app.activeJobRepository
        val syncManager = app.syncManager
        val machineRepository = app.machineRepository
        val paymentRepository = app.paymentRepository
        val activeJobsRepository = app.activeJobsRepository
        val settingsRepository = app.settingsRepository
        val workPatternPreferences = app.workPatternPreferences

        setContent {
            val onboardingEngine = remember {
                ProductionOnboardingEngine(
                    sessionManager = app.sessionManager,
                    organizationRepository = app.organizationRepository
                )
            }
            val scope = rememberCoroutineScope()
            val identityFoundationState by app.sessionManager.state.collectAsState()
            val identitySession by app.sessionManager.currentSession.collectAsState()
            val sessionRestoreCompleted by app.sessionManager.restoreCompleted.collectAsState()
            val productionOnboardingState by ProductionOnboardingPersistence.state.collectAsState()
            val currentSessionForIdentity by CurrentSessionProvider.session.collectAsState()
            val onboardingStateForIdentity by OnboardingPersistence.state.collectAsState()
            var productionOnboardingBusy by remember { mutableStateOf(false) }
            var productionOnboardingError by remember { mutableStateOf<String?>(null) }
            var startupState by remember { mutableStateOf<AppStartupState>(AppStartupState.Loading) }
            var lastMembershipResolvedUserId by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(
                sessionRestoreCompleted,
                identityFoundationState,
                identitySession?.userId,
                currentSessionForIdentity?.userId,
                productionOnboardingState.step,
                onboardingStateForIdentity.profilePhone
            ) {
                if (!sessionRestoreCompleted) {
                    startupState = AppStartupState.Loading
                    return@LaunchedEffect
                }

                if (identityFoundationState != IdentitySessionState.AUTHENTICATED) {
                    if (productionOnboardingState.step != ProductionOnboardingStep.OTP) {
                        onboardingEngine.resetForLogin()
                    }
                    lastMembershipResolvedUserId = null
                    startupState = StartupStateResolver.resolve(
                        isAuthenticated = false,
                        hasMembership = false
                    )
                    return@LaunchedEffect
                }

                if (productionOnboardingState.step == ProductionOnboardingStep.COMPLETED) {
                    startupState = StartupStateResolver.resolve(
                        isAuthenticated = true,
                        hasMembership = true
                    )
                    return@LaunchedEffect
                }

                val resolvedUserId = identitySession?.userId ?: currentSessionForIdentity?.userId
                if (resolvedUserId.isNullOrBlank()) {
                    startupState = AppStartupState.StartupError(Throwable("Authenticated session missing user id"))
                    return@LaunchedEffect
                }

                if (lastMembershipResolvedUserId == resolvedUserId) {
                    startupState = StartupStateResolver.resolve(
                        isAuthenticated = true,
                        hasMembership = false
                    )
                    return@LaunchedEffect
                }

                startupState = AppStartupState.Loading
                val fallbackName = currentSessionForIdentity?.displayName.orEmpty()
                val fallbackMobile = onboardingStateForIdentity.profilePhone.orEmpty()
                val restored = onboardingEngine.restoreMembershipContext(
                    userId = resolvedUserId,
                    fallbackDisplayName = fallbackName,
                    fallbackMobile = fallbackMobile
                )
                if (restored) {
                    startupState = StartupStateResolver.resolve(
                        isAuthenticated = true,
                        hasMembership = true
                    )
                } else {
                    if (productionOnboardingState.step == ProductionOnboardingStep.OTP) {
                        onboardingEngine.routeAuthenticatedUserWithoutMembership(
                            userId = resolvedUserId,
                            displayName = fallbackName.ifBlank { "Operator" },
                            mobile = fallbackMobile
                        )
                    }
                    lastMembershipResolvedUserId = resolvedUserId
                    startupState = StartupStateResolver.resolve(
                        isAuthenticated = true,
                        hasMembership = false
                    )
                }
            }

            LaunchedEffect(identityFoundationState, productionOnboardingState.step) {
                if (
                    identityFoundationState == IdentitySessionState.AUTHENTICATED &&
                    productionOnboardingState.step == ProductionOnboardingStep.OTP &&
                    currentSessionForIdentity != null &&
                    onboardingStateForIdentity.onboardingCompleted
                ) {
                    ProductionOnboardingPersistence.persist(
                        productionOnboardingState.copy(
                            step = ProductionOnboardingStep.COMPLETED,
                            userId = currentSessionForIdentity?.userId,
                            displayName = currentSessionForIdentity?.displayName.orEmpty(),
                            mobileNumber = onboardingStateForIdentity.profilePhone.orEmpty(),
                            organizationName = onboardingStateForIdentity.organizationName.orEmpty()
                        )
                    )
                }
                if (identityFoundationState == IdentitySessionState.UNAUTHENTICATED &&
                    productionOnboardingState.step == ProductionOnboardingStep.COMPLETED
                ) {
                    onboardingEngine.resetForLogin()
                }
            }

            // Observe theme preference from settings
            val settings by settingsRepository.settings.collectAsState(
                initial = AppSettings()
            )
            val darkTheme = when (settings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            LaunchedEffect(identityFoundationState, productionOnboardingState.step) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG_IDENTITY_FLOW, "IdentitySessionState = $identityFoundationState, step=${productionOnboardingState.step}")
                }
            }

            // R4.2 — Bug-hunt diagnostics. LOGGING ONLY. No state mutation.
            // Re-emits whenever any input to the resolver changes so a fresh
            // install / app-data-clear trace can be read line-by-line from
            // logcat. PROD is gated by BuildConfig.DEBUG.
            LaunchedEffect(currentSessionForIdentity, onboardingStateForIdentity, identityFoundationState, productionOnboardingState.step) {
                if (BuildConfig.DEBUG) {
                    val s = currentSessionForIdentity
                    val o = onboardingStateForIdentity
                    Log.d(
                        TAG_IDENTITY_FLOW_DEBUG,
                        "session=${if (s == null) "null" else "present(role=${s.role},testUser=${s.role.isTestUser})"} " +
                            "completed=${o.onboardingCompleted} " +
                            "dismissed=${o.welcomeDismissed} " +
                            "firstLaunch=${o.isFirstLaunch} " +
                            "hasStartedFirstWork=${o.hasStartedFirstWork} " +
                            "identityState=$identityFoundationState " +
                            "onboardingStep=${productionOnboardingState.step}"
                    )
                }
            }

            FieldFlowTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (startupState == AppStartupState.Loading) {
                        StartupSplashScreen()
                    } else {
                        val rootStartDestination = when (startupState) {
                            AppStartupState.AuthenticatedWithMembership -> Screen.Main.route
                            AppStartupState.AuthenticatedWithoutMembership -> routeForOnboardingStep(productionOnboardingState.step)
                            AppStartupState.Unauthenticated -> Screen.Login.route
                            is AppStartupState.StartupError -> Screen.Login.route
                            AppStartupState.Loading -> Screen.Login.route
                            AppStartupState.Initializing -> Screen.Login.route
                        }

                        key(rootStartDestination) {
                            val navController = rememberNavController()
                            FieldFlowNavGraph(
                                navController = navController,
                                rootStartDestination = rootStartDestination,
                                onboardingState = productionOnboardingState,
                                onboardingBusy = productionOnboardingBusy,
                                onboardingError = productionOnboardingError,
                                onOtpSubmit = { mobile, otp ->
                                    scope.launch {
                                        productionOnboardingBusy = true
                                        productionOnboardingError = onboardingEngine.verifyOtp(mobile, otp)
                                        productionOnboardingBusy = false
                                    }
                                },
                                onOtpResend = { mobile ->
                                    scope.launch {
                                        productionOnboardingBusy = true
                                        productionOnboardingError = onboardingEngine.resendOtp(mobile)
                                        productionOnboardingBusy = false
                                    }
                                },
                                onAdvanceFromWelcome = {
                                    onboardingEngine.advanceFromWelcome()
                                },
                                onRoleSelected = { role ->
                                    onboardingEngine.selectRole(role)
                                },
                                onProfileSubmit = { name, path ->
                                    scope.launch {
                                        productionOnboardingBusy = true
                                        productionOnboardingError = onboardingEngine.submitProfile(name, path)
                                        productionOnboardingBusy = false
                                    }
                                },
                                onCreateOrganization = { orgName ->
                                    scope.launch {
                                        productionOnboardingBusy = true
                                        productionOnboardingError = onboardingEngine.createOrganization(orgName)
                                        productionOnboardingBusy = false
                                    }
                                },
                                onGoToInvite = {
                                    onboardingEngine.goToInvite()
                                },
                                onSkipJoin = {
                                    onboardingEngine.skipJoin()
                                },
                                onPrepareJoinPreview = { inviteCode ->
                                    scope.launch {
                                        productionOnboardingBusy = true
                                        productionOnboardingError = onboardingEngine.prepareJoinPreview(inviteCode)
                                        productionOnboardingBusy = false
                                    }
                                },
                                onConfirmJoin = {
                                    scope.launch {
                                        productionOnboardingBusy = true
                                        productionOnboardingError = onboardingEngine.confirmJoinOrganization()
                                        productionOnboardingBusy = false
                                    }
                                },
                                onCancelJoinPreview = {
                                    onboardingEngine.cancelJoinPreview()
                                    productionOnboardingError = null
                                },
                                onContinueAfterSuccess = {
                                    onboardingEngine.continueAfterSuccess()
                                    productionOnboardingError = null
                                },
                                jobRepository = jobRepository,
                                customerRepository = customerRepository,
                                ratePreferences = ratePreferences,
                                customerFieldDao = customerFieldDao,
                                activeJobRepository = activeJobRepository,
                                syncManager = syncManager,
                                machineRepository = machineRepository,
                                paymentRepository = paymentRepository,
                                activeJobsRepository = activeJobsRepository,
                                workPatternPreferences = workPatternPreferences
                            )
                        }
                    }

                }
            }
        }
    }
}

private fun routeForOnboardingStep(step: ProductionOnboardingStep): String = when (step) {
    ProductionOnboardingStep.OTP -> Screen.Otp.route
    ProductionOnboardingStep.WELCOME -> Screen.Welcome.route
    ProductionOnboardingStep.ROLE_SELECTION -> Screen.RoleSelection.route
    ProductionOnboardingStep.PROFILE -> Screen.OnboardingProfile.route
    ProductionOnboardingStep.OWNER_ORGANIZATION -> Screen.CreateOrganization.route
    ProductionOnboardingStep.JOIN_OPTIONS -> Screen.JoinOptions.route
    ProductionOnboardingStep.OPERATOR_INVITE -> Screen.InviteCode.route
    ProductionOnboardingStep.JOIN_PREVIEW -> Screen.JoinPreview.route
    ProductionOnboardingStep.SUCCESS -> Screen.StartupSuccess.route
    ProductionOnboardingStep.COMPLETED -> Screen.Main.route
}
