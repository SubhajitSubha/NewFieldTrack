package com.example.fieldtrack.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun FieldFlowNavGraph(
    navController: NavHostController,
    rootStartDestination: String? = null,
    onboardingState: Any? = null,
    onboardingBusy: Boolean = false,
    onboardingError: String? = null,
    onOtpSubmit: (String, String) -> Unit = { _, _ -> },
    onOtpResend: (String) -> Unit = {},
    onAdvanceFromWelcome: () -> Unit = {},
    onRoleSelected: (String) -> Unit = {},
    onProfileSubmit: (String, String?) -> Unit = { _, _ -> },
    onCreateOrganization: (String) -> Unit = {},
    onGoToInvite: () -> Unit = {},
    onSkipJoin: () -> Unit = {},
    onPrepareJoinPreview: (String) -> Unit = {},
    onConfirmJoin: () -> Unit = {},
    onCancelJoinPreview: () -> Unit = {},
    onContinueAfterSuccess: () -> Unit = {},
    jobRepository: Any? = null,
    customerRepository: Any? = null,
    ratePreferences: Any? = null,
    customerFieldDao: Any? = null,
    activeJobRepository: Any? = null,
    syncManager: Any? = null,
    machineRepository: Any? = null,
    paymentRepository: Any? = null,
    activeJobsRepository: Any? = null,
    workPatternPreferences: Any? = null
) {
    if (rootStartDestination == null) return
    NavHost(
        navController = navController,
        startDestination = rootStartDestination
    ) {
        composable(Screen.Login.route) {
            com.example.fieldtrack.ui.login.LoginScreen(
                onLoginClick = { phone -> 
                    navController.navigate(Screen.Otp.route)
                }
            )
        }
        composable(Screen.Main.route) {
            com.example.fieldtrack.ui.main.MainScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToNewJob = { navController.navigate(Screen.NewJob.route) },
                onNavigateToMachines = { navController.navigate(Screen.Machines.route) },
                onNavigateToCustomers = { navController.navigate(Screen.Customers.route) },
                onNavigateToActiveWork = { navController.navigate(Screen.ActiveWork.route) },
                onNavigateToAnalyticsDashboard = { navController.navigate(Screen.AnalyticsDashboard.route) },
                onNavigateToBusinessDashboard = { navController.navigate(Screen.BusinessDashboard.route) }
            )
        }
        composable(Screen.Otp.route) {
            com.example.fieldtrack.ui.login.OtpScreen(
                onBackClick = { navController.popBackStack() },
                onVerifyClick = { code -> 
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.OnboardingProfile.route) {
            com.example.fieldtrack.ui.onboarding.OnboardingProfileScreen(
                onProfileSubmit = { name, path -> 
                    onProfileSubmit(name, path)
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Welcome.route) {
            com.example.fieldtrack.ui.onboarding.WelcomeScreen(
                onContinueClick = {
                    onAdvanceFromWelcome()
                    navController.navigate(Screen.RoleSelection.route)
                }
            )
        }
        composable(Screen.RoleSelection.route) {
            com.example.fieldtrack.ui.onboarding.RoleSelectionScreen(
                onRoleSelected = { role -> 
                    onRoleSelected(role)
                    navController.navigate(Screen.OnboardingProfile.route)
                }
            )
        }
        composable(Screen.Profile.route) {
            com.example.fieldtrack.ui.profile.ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToOrganization = { navController.navigate(Screen.OrganizationDashboard.route) },
                onLogoutClick = { 
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.CreateOrganization.route) {
            com.example.fieldtrack.ui.organization.CreateOrganizationScreen(
                onBackClick = { navController.popBackStack() },
                onSaveClick = { orgName ->
                    onCreateOrganization(orgName)
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.JoinOptions.route) {
            com.example.fieldtrack.ui.onboarding.JoinOptionsScreen(
                onJoinExisting = onGoToInvite,
                onSkip = onSkipJoin
            )
        }
        composable(Screen.InviteCode.route) {
            com.example.fieldtrack.ui.onboarding.InviteCodeScreen(
                onContinueClick = onPrepareJoinPreview
            )
        }
        composable(Screen.JoinPreview.route) {
            com.example.fieldtrack.ui.onboarding.JoinPreviewScreen(
                orgName = "Mock Organization",
                roleTitle = "Team Member",
                onConfirmClick = onConfirmJoin,
                onCancelClick = onCancelJoinPreview
            )
        }
        composable(Screen.Settings.route) {
            com.example.fieldtrack.ui.settings.SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToOrganizations = { navController.navigate(Screen.OrganizationDashboard.route) },
                onNavigateToTeam = { navController.navigate(Screen.TeamManagement.route) },
                onNavigateToDevices = { navController.navigate(Screen.DeviceRegistration.route) },
                onLogoutClick = { 
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.DeviceRegistration.route) {
            com.example.fieldtrack.ui.settings.DeviceRegistrationScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.StartupSuccess.route) {
            com.example.fieldtrack.ui.onboarding.StartupSuccessScreen(
                onContinueClick = onContinueAfterSuccess
            )
        }
        composable(Screen.NewJob.route) {
            com.example.fieldtrack.ui.job.NewJobScreen(
                onBackClick = { navController.popBackStack() },
                onSaveJob = { 
                    navController.popBackStack()
                    navController.navigate(Screen.JobDashboard.createRoute("mockJobId"))
                }
            )
        }
        composable(Screen.JobDashboard.route) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            com.example.fieldtrack.ui.job.JobDashboardScreen(
                jobId = jobId,
                onBackClick = { navController.popBackStack() },
                onStartWorkClick = { navController.navigate(Screen.ActiveWork.route) }
            )
        }
        composable(Screen.Machines.route) {
            com.example.fieldtrack.ui.machine.MachinesScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToNewMachine = { navController.navigate(Screen.NewMachine.route) }
            )
        }
        composable(Screen.NewMachine.route) {
            com.example.fieldtrack.ui.machine.NewMachineScreen(
                onBackClick = { navController.popBackStack() },
                onSaveMachine = { navController.popBackStack() }
            )
        }
        composable(Screen.Customers.route) {
            com.example.fieldtrack.ui.customer.CustomersScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToNewCustomer = { navController.navigate(Screen.NewCustomer.route) },
                onNavigateToCustomerDetail = { customerId -> navController.navigate(Screen.CustomerDetail.createRoute(customerId)) }
            )
        }
        composable(Screen.NewCustomer.route) {
            com.example.fieldtrack.ui.customer.NewCustomerScreen(
                onBackClick = { navController.popBackStack() },
                onSaveCustomer = { navController.popBackStack() }
            )
        }
        composable(Screen.CustomerDetail.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
            com.example.fieldtrack.ui.customer.CustomerDetailScreen(
                customerId = customerId,
                onBackClick = { navController.popBackStack() },
                onNavigateToNewField = { cid -> navController.navigate(Screen.NewField.createRoute(cid)) },
                onNavigateToFieldDetail = { fieldId -> navController.navigate(Screen.FieldDetail.createRoute(fieldId)) },
                onNavigateToNewPayment = { cid -> navController.navigate(Screen.NewPayment.createRoute(cid)) },
                onNavigateToPaymentHistory = { cid -> navController.navigate(Screen.PaymentHistory.createRoute(cid)) }
            )
        }
        composable(Screen.NewPayment.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
            com.example.fieldtrack.ui.payment.NewPaymentScreen(
                customerId = customerId,
                onBackClick = { navController.popBackStack() },
                onSavePayment = { navController.popBackStack() }
            )
        }
        composable(Screen.PaymentHistory.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
            com.example.fieldtrack.ui.payment.PaymentHistoryScreen(
                customerId = customerId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.FieldDetail.route) { backStackEntry ->
            val fieldId = backStackEntry.arguments?.getString("fieldId") ?: ""
            com.example.fieldtrack.ui.field.FieldDetailScreen(
                fieldId = fieldId,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.NewField.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
            com.example.fieldtrack.ui.field.NewFieldScreen(
                customerId = customerId,
                onBackClick = { navController.popBackStack() },
                onSaveField = { navController.popBackStack() }
            )
        }
        composable(Screen.ActiveWork.route) {
            com.example.fieldtrack.ui.job.ActiveWorkScreen(
                onBackClick = { navController.popBackStack() },
                onCompleteJob = { navController.navigate(Screen.CompletionSheet.route) }
            )
        }
        composable(Screen.CompletionSheet.route) {
            com.example.fieldtrack.ui.job.CompletionSheetScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToHome = { 
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.BusinessDashboard.route) {
            com.example.fieldtrack.ui.payment.BusinessDashboardScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.AnalyticsDashboard.route) {
            com.example.fieldtrack.ui.analytics.AnalyticsDashboardScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToFinancial = { navController.navigate(Screen.BusinessDashboard.route) },
                onNavigateToCustomerAnalytics = { navController.navigate(Screen.CustomerAnalytics.route) },
                onNavigateToMachineAnalytics = { navController.navigate(Screen.MachineAnalytics.route) },
                onNavigateToOperatorAnalytics = { navController.navigate(Screen.OperatorAnalytics.route) }
            )
        }
        composable(Screen.CustomerAnalytics.route) {
            com.example.fieldtrack.ui.analytics.CustomerAnalyticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.MachineAnalytics.route) {
            com.example.fieldtrack.ui.analytics.MachineAnalyticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.OperatorAnalytics.route) {
            com.example.fieldtrack.ui.analytics.OperatorAnalyticsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.OrganizationDashboard.route) {
            com.example.fieldtrack.ui.organization.OrganizationDashboardScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToTeam = { navController.navigate(Screen.TeamManagement.route) }
            )
        }
        composable(Screen.TeamManagement.route) {
            com.example.fieldtrack.ui.organization.TeamManagementScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToMember = { memberId -> navController.navigate(Screen.MemberProfile.createRoute(memberId)) }
            )
        }
        composable(Screen.MemberProfile.route) { backStackEntry ->
            val memberId = backStackEntry.arguments?.getString("memberId") ?: ""
            com.example.fieldtrack.ui.organization.MemberProfileScreen(
                memberId = memberId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
