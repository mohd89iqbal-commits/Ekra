package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.remote.BiteLocalRepository
import com.example.ui.account.AccountScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.checkout.CheckoutScreen
import com.example.ui.explore.ExploreScreen
import com.example.ui.orders.OrdersScreen
import com.example.ui.restaurant.RestaurantDetailScreen
import com.example.ui.theme.BiteLocalTheme
import com.example.ui.tracking.OrderTrackingScreen
import com.example.ui.viewmodel.BiteLocalViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ROUTE_EXPLORE = "explore"
private const val ROUTE_ORDERS = "orders"
private const val ROUTE_ACCOUNT = "account"
private const val ROUTE_RESTAURANT = "restaurant/{restaurantId}"
private const val ROUTE_CHECKOUT = "checkout"
private const val ROUTE_TRACKING = "tracking/{orderId}"

private data class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab(ROUTE_EXPLORE, "Explore", Icons.Rounded.Explore, Icons.Outlined.Explore),
    TopLevelTab(ROUTE_ORDERS, "Orders", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    TopLevelTab(ROUTE_ACCOUNT, "Account", Icons.Rounded.Person, Icons.Outlined.Person)
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiteLocalTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedBiteLocalShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedBiteLocalShell(currentUserId: String) {
    val viewModel: BiteLocalViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BiteLocalViewModel(BiteLocalRepository(db), currentUserId)
            }
        }
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTopLevelBar = currentRoute in listOf(ROUTE_EXPLORE, ROUTE_ORDERS, ROUTE_ACCOUNT)

    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp && showTopLevelBar

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    TOP_LEVEL_TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showTopLevelBar && !useNavRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            TOP_LEVEL_TABS.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_EXPLORE
                    ) {
                        composable(ROUTE_EXPLORE) {
                            ExploreScreen(
                                viewModel = viewModel,
                                onRestaurantClick = { restaurantId ->
                                    navController.navigate("restaurant/$restaurantId")
                                },
                                onOpenCart = {
                                    navController.navigate(ROUTE_CHECKOUT)
                                },
                                onTrackOrder = { orderId ->
                                    navController.navigate("tracking/$orderId")
                                }
                            )
                        }

                        composable(ROUTE_ORDERS) {
                            OrdersScreen(
                                viewModel = viewModel,
                                onTrackOrder = { orderId ->
                                    navController.navigate("tracking/$orderId")
                                },
                                onReorderToCart = { order ->
                                    viewModel.reorderFromOrder(order)
                                    navController.navigate(ROUTE_CHECKOUT)
                                },
                                onExploreClick = {
                                    navController.navigate(ROUTE_EXPLORE) {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(ROUTE_ACCOUNT) {
                            AccountScreen(
                                viewModel = viewModel,
                                onRestaurantClick = { restaurantId ->
                                    navController.navigate("restaurant/$restaurantId")
                                }
                            )
                        }

                        composable(
                            route = ROUTE_RESTAURANT,
                            arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val restaurantId = backStackEntry.arguments?.getString("restaurantId") ?: ""
                            RestaurantDetailScreen(
                                restaurantId = restaurantId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenCart = { navController.navigate(ROUTE_CHECKOUT) }
                            )
                        }

                        composable(ROUTE_CHECKOUT) {
                            CheckoutScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOrderPlaced = { orderId ->
                                    navController.navigate("tracking/$orderId") {
                                        popUpTo(ROUTE_EXPLORE)
                                    }
                                }
                            )
                        }

                        composable(
                            route = ROUTE_TRACKING,
                            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                            OrderTrackingScreen(
                                orderId = orderId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
