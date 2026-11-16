package com.agniv.hisaab.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.agniv.hisaab.AppSettings
import com.agniv.hisaab.NotifyType
import com.agniv.hisaab.R
import com.agniv.hisaab.alerts.AlertsScreen
import com.agniv.hisaab.alerts.NotificationSettingsScreen
import com.agniv.hisaab.core.designsystem.motion.Motion
import com.agniv.hisaab.core.designsystem.motion.rememberReducedMotion
import com.agniv.hisaab.feature.expenses.add.AddExpenseScreen
import com.agniv.hisaab.feature.expenses.budgets.BudgetCategoryScreen
import com.agniv.hisaab.feature.expenses.budgets.BudgetsScreen
import com.agniv.hisaab.feature.expenses.home.AllExpensesScreen
import com.agniv.hisaab.feature.expenses.home.ExpensesHomeScreen
import com.agniv.hisaab.feature.expenses.home.HomeActions
import com.agniv.hisaab.feature.expenses.statement.StatementImportScreen
import com.agniv.hisaab.feature.goals.GoalsScreen
import com.agniv.hisaab.feature.insights.InsightsHomeCard
import com.agniv.hisaab.feature.insights.InsightsScreen
import com.agniv.hisaab.feature.insights.report.MonthlyReportScreen
import com.agniv.hisaab.feature.loans.LoansScreen
import com.agniv.hisaab.feature.tools.ToolsScreen
import com.agniv.hisaab.feature.udhaar.UdhaarScreen
import kotlinx.serialization.Serializable

@Serializable
data object TabsRoute

@Serializable
data object AllExpensesRoute

/** [editId] null = adding a new expense. */
@Serializable
data class AddExpenseRoute(
    val editId: String? = null,
)

@Serializable
data object EmiRoute

@Serializable
data object ImportRoute

@Serializable
data object BudgetsRoute

@Serializable
data object AlertsRoute

@Serializable
data object ReportRoute

@Serializable
data object NotificationSettingsRoute

/** [month] is "2026-11". */
@Serializable
data class BudgetCategoryRoute(
    val categoryId: String,
    val month: String,
)

@Serializable
data object UdhaarRoute

@Serializable
data object ToolsRoute

/** The five tabs on the boards' bottom bar. */
enum class Tab(
    val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    INSIGHTS(R.string.tab_insights, Icons.Outlined.Insights, Icons.Filled.Insights),
    GOALS(R.string.tab_goals, Icons.Outlined.TrackChanges, Icons.Filled.TrackChanges),
    ASSISTANT(R.string.tab_assistant, Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Screens the app shell supplies to the tabs (they need app-level state such as settings or the lock). */
class TabContent(
    val assistant: @Composable () -> Unit,
    val settings: @Composable () -> Unit,
)

/**
 * Whole signed-in app. Tabs sit at the root; every other screen slides in from the right and back out (300 ms,
 * none when the phone's animations are off). [openTab] switches tab from outside, e.g. a tapped notification.
 */
@Composable
fun KinlyNavHost(
    settings: AppSettings,
    content: TabContent,
    openTab: Tab?,
    onTabOpened: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val reduced = rememberReducedMotion()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    LaunchedEffect(openTab) {
        if (openTab != null) {
            navController.popBackStack(TabsRoute, inclusive = false)
            tab = openTab
            onTabOpened()
        }
    }
    val push: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) {
            EnterTransition.None
        } else {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(Motion.SCREEN)) +
                fadeIn(tween(Motion.SCREEN))
        }
    }
    val popOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced) {
            ExitTransition.None
        } else {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(Motion.SCREEN)) +
                fadeOut(tween(Motion.SCREEN))
        }
    }
    val stay: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced) ExitTransition.None else fadeOut(tween(Motion.SCREEN))
    }
    val back: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) EnterTransition.None else fadeIn(tween(Motion.SCREEN))
    }
    NavHost(
        navController = navController,
        startDestination = TabsRoute,
        enterTransition = push,
        exitTransition = stay,
        popEnterTransition = back,
        popExitTransition = popOut,
    ) {
        composable<TabsRoute> {
            Tabs(tab = tab, onTab = { tab = it }, content = content, navController = navController)
        }
        composable<AllExpensesRoute> {
            AllExpensesScreen(
                onBack = navController::popBackStack,
                onAdd = { navController.navigate(AddExpenseRoute()) },
                onEdit = { navController.navigate(AddExpenseRoute(it)) },
                onImport = { navController.navigate(ImportRoute) },
            )
        }
        composable<AddExpenseRoute> { entry ->
            AddExpenseScreen(onDone = navController::popBackStack, editId = entry.toRoute<AddExpenseRoute>().editId)
        }
        composable<EmiRoute> { LoansScreen(onBack = navController::popBackStack) }
        composable<BudgetsRoute> {
            BudgetsScreen(
                onBack = navController::popBackStack,
                onCategory = { id, month -> navController.navigate(BudgetCategoryRoute(id, month)) },
            )
        }
        composable<BudgetCategoryRoute> { entry ->
            val route = entry.toRoute<BudgetCategoryRoute>()
            BudgetCategoryScreen(
                categoryId = route.categoryId,
                month = route.month,
                onBack = navController::popBackStack,
                onEdit = { navController.navigate(AddExpenseRoute(it)) },
            )
        }
        composable<AlertsRoute> {
            AlertsScreen(
                onBack = navController::popBackStack,
                onSettings = { navController.navigate(NotificationSettingsRoute) },
                onOpen = { kind ->
                    when (kind.type) {
                        NotifyType.SMART, NotifyType.REPORT -> {
                            navController.popBackStack(TabsRoute, inclusive = false)
                            tab = Tab.INSIGHTS
                        }
                        NotifyType.GOALS -> {
                            navController.popBackStack(TabsRoute, inclusive = false)
                            tab = Tab.GOALS
                        }
                        NotifyType.BUDGET -> navController.navigate(BudgetsRoute)
                        NotifyType.EMI -> navController.navigate(EmiRoute)
                        NotifyType.UDHAAR -> navController.navigate(UdhaarRoute)
                    }
                },
            )
        }
        composable<NotificationSettingsRoute> { NotificationSettingsScreen(settings, onBack = navController::popBackStack) }
        composable<ReportRoute> {
            MonthlyReportScreen(onBack = navController::popBackStack, onAddExpense = { navController.navigate(AddExpenseRoute()) })
        }
        composable<ImportRoute> { StatementImportScreen(onBack = navController::popBackStack) }
        composable<UdhaarRoute> { UdhaarScreen(onBack = navController::popBackStack) }
        composable<ToolsRoute> { ToolsScreen() }
    }
}

@Composable
private fun Tabs(
    tab: Tab,
    onTab: (Tab) -> Unit,
    content: TabContent,
    navController: NavHostController,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { BottomBar(tab, onTab) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            when (tab) {
                Tab.HOME ->
                    ExpensesHomeScreen(
                        actions =
                            HomeActions(
                                onAddExpense = { navController.navigate(AddExpenseRoute()) },
                                onEditExpense = { navController.navigate(AddExpenseRoute(it)) },
                                // Alerts and Family get their own screens in later steps; until then they open the
                                // places that hold them today.
                                onAlerts = { navController.navigate(AlertsRoute) },
                                onFamily = { onTab(Tab.SETTINGS) },
                                onBudgets = { navController.navigate(BudgetsRoute) },
                                onAllExpenses = { navController.navigate(AllExpensesRoute) },
                                onEmi = { navController.navigate(EmiRoute) },
                                onUdhaar = { navController.navigate(UdhaarRoute) },
                                onTools = { navController.navigate(ToolsRoute) },
                                onImport = { navController.navigate(ImportRoute) },
                            ),
                        insightCard = { InsightsHomeCard(onOpen = { onTab(Tab.INSIGHTS) }) },
                    )
                Tab.INSIGHTS ->
                    InsightsScreen(
                        onReport = { navController.navigate(ReportRoute) },
                        onAddExpense = { navController.navigate(AddExpenseRoute()) },
                    )
                Tab.GOALS -> GoalsScreen()
                Tab.ASSISTANT -> content.assistant()
                Tab.SETTINGS -> content.settings()
            }
        }
    }
}

/** Board bottom bar: green pill behind the selected icon, labels always shown. */
@Composable
private fun BottomBar(
    tab: Tab,
    onTab: (Tab) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTab(t) },
                icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                label = { Text(stringResource(t.label), maxLines = 1) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
