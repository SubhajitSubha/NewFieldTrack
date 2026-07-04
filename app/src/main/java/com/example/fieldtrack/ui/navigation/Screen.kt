package com.example.fieldtrack.ui.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Login : Screen("login")
    object Otp : Screen("otp")
    object Welcome : Screen("welcome")
    object RoleSelection : Screen("role_selection")
    object OnboardingProfile : Screen("onboarding_profile")
    object Profile : Screen("profile")
    object JoinOptions : Screen("join_options")
    object CreateOrganization : Screen("create_organization")
    object InviteCode : Screen("invite_code")
    object JoinPreview : Screen("join_preview")
    object StartupSuccess : Screen("startup_success")
    object Settings : Screen("settings")
    object DeviceRegistration : Screen("device_registration")

    object NewJob : Screen("new_job")
    object Machines : Screen("machines")
    object NewMachine : Screen("new_machine")
    object Customers : Screen("customers")
    object NewCustomer : Screen("new_customer")
    object CustomerDetail : Screen("customer_detail/{customerId}") {
        fun createRoute(customerId: String) = "customer_detail/$customerId"
    }
    object Fields : Screen("fields")
    object NewField : Screen("new_field/{customerId}") {
        fun createRoute(customerId: String) = "new_field/$customerId"
    }
    object FieldDetail : Screen("field_detail/{fieldId}") {
        fun createRoute(fieldId: String) = "field_detail/$fieldId"
    }
    object NewPayment : Screen("new_payment/{customerId}") {
        fun createRoute(customerId: String) = "new_payment/$customerId"
    }
    object PaymentHistory : Screen("payment_history/{customerId}") {
        fun createRoute(customerId: String) = "payment_history/$customerId"
    }
    object BusinessDashboard : Screen("business_dashboard")
    object AnalyticsDashboard : Screen("analytics_dashboard")
    object CustomerAnalytics : Screen("customer_analytics")
    object MachineAnalytics : Screen("machine_analytics")
    object OperatorAnalytics : Screen("operator_analytics")
    object OrganizationDashboard : Screen("organization_dashboard")
    object TeamManagement : Screen("team_management")
    object MemberProfile : Screen("member_profile/{memberId}") {
        fun createRoute(memberId: String) = "member_profile/$memberId"
    }
    object JobDashboard : Screen("job_dashboard/{jobId}") {
        fun createRoute(jobId: String) = "job_dashboard/$jobId"
    }
    object ActiveWork : Screen("active_work")
    object CompletionSheet : Screen("completion_sheet")
}
