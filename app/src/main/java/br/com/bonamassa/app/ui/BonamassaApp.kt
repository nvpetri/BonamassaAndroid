package br.com.bonamassa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import br.com.bonamassa.app.BonamassaViewModel
import br.com.bonamassa.core.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(Tab("home", "Início", Icons.Default.Home), Tab("menu", "Cardápio", Icons.Default.LocalPizza),
    Tab("orders", "Pedidos", Icons.Default.ReceiptLong), Tab("profile", "Perfil", Icons.Default.PersonOutline))

@Composable
fun BonamassaApp(vm: BonamassaViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    var resetErrorData by rememberSaveable { mutableStateOf(false) }
    if (resetErrorData) AlertDialog(onDismissRequest = { resetErrorData = false }, title = { Text("Reiniciar dados locais?") },
        text = { Text("O histórico, a sacola e o perfil deste app serão apagados definitivamente. Tente carregar novamente antes de reiniciar.") },
        confirmButton = { TextButton(onClick = { resetErrorData = false; vm.reset {} }) { Text("Apagar e reiniciar") } },
        dismissButton = { TextButton(onClick = { resetErrorData = false }) { Text("Voltar") } })
    if (ui.fatalError) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding)) {
                EmptyState("Não foi possível ler seus dados", "Seus dados não foram sobrescritos. Tente novamente. Se o problema persistir, você pode reiniciar a demonstração.", Icons.Default.ErrorOutline, "Tentar novamente", vm::observe)
                TextButton(onClick = { resetErrorData = true }, enabled = !ui.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Reiniciar demonstração") }
            }
        }
        return
    }
    if (!ui.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand.Gold) }
        return
    }
    key(ui.generation) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "home"
        val isTab = tabs.any { it.route == route }
        val state = ui.data
        val count = state.cart.sumOf { it.quantity }
        fun goTab(target: String) {
            nav.navigate(target) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
        fun openCart() {
            if (!nav.popBackStack("cart", false)) nav.navigate("cart") { launchSingleTop = true }
        }
        fun openProduct(id: String) { nav.navigate("product/$id") }
        fun openOrder(id: String) { nav.navigate("order/$id") }
        val title = when {
            route == "cart" -> "Sua sacola"
            route == "checkout" -> "Finalizar pedido"
            route.startsWith("product") -> "Monte do seu jeito"
            route.startsWith("order/") -> "Seu pedido"
            else -> "Bonamassa"
        }
        Scaffold(
            containerColor = Brand.Background,
            topBar = {
                Column(Modifier.statusBarsPadding()) {
                    BrandHeader(title, if (isTab) null else ({ if (!ui.busy) nav.popBackStack() }), count, { if (!ui.busy) openCart() })
                    DemoNotice()
                    if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand.Gold)
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (isTab) Column {
                    if (count > 0 && route in listOf("home", "menu")) {
                        PrimaryAction("Ver sacola · $count itens · ${money(OrderRules.quote(state.cart, state.checkout.fulfillment, state.coupon).total)}",
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), !ui.busy, Icons.Default.ShoppingBag) { openCart() }
                    }
                    NavigationBar(containerColor = Brand.Background, tonalElevation = 0.dp) {
                        tabs.forEach { tab -> NavigationBarItem(selected = route == tab.route, onClick = { if (!ui.busy) goTab(tab.route) },
                            icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Brand.Gold, selectedTextColor = Brand.Gold, indicatorColor = Brand.Raised)) }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                NavHost(navController = nav, startDestination = "home", modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    composable("home") { HomeScreen(state, { goTab("menu") }, ::openProduct, vm::favorite, { goTab("profile") }, ::openOrder) }
                    composable("menu") { MenuScreen(state, ::openProduct) }
                    composable("orders") { OrdersScreen(state, ::openOrder, { goTab("menu") }) }
                    composable("profile") { ProfileScreen(state, ui.busy, { customer, address -> vm.profile(customer, address) { vm.notify("Perfil salvo neste aparelho.") } }, { vm.reset {} }) }
                    composable("product/{productId}?itemId={itemId}", arguments = listOf(navArgument("itemId") { type = NavType.StringType; defaultValue = "" })) { backStack ->
                        val product = Catalog.find(backStack.arguments?.getString("productId"))
                        val itemId = backStack.arguments?.getString("itemId").orEmpty()
                        val existing = state.cart.find { it.id == itemId }
                        if (product == null || itemId.isNotEmpty() && existing == null) {
                            EmptyState("Item não encontrado", "Ele pode ter sido removido da sacola.", Icons.Default.Search, "Voltar ao cardápio", { goTab("menu") })
                        } else BuilderScreen(product, existing, product.id in state.favorites, ui.busy, { vm.favorite(product.id) }) { item ->
                            vm.putItem(item) { nav.popBackStack(); openCart() }
                        }
                    }
                    composable("cart") {
                        CartScreen(state, ui.busy, { goTab("menu") }, { id -> state.cart.find { it.id == id }?.let { nav.navigate("product/${it.productId}?itemId=$id") } },
                            vm::remove, vm::quantity, vm::coupon, vm::fulfillment, { nav.navigate("checkout") { launchSingleTop = true } })
                    }
                    composable("checkout") {
                        CheckoutScreen(state, ui.busy, { draft -> vm.place(draft) { id ->
                            nav.navigate("order/$id") { popUpTo("cart") { inclusive = true }; launchSingleTop = true }
                        } }, { draft -> vm.checkout(draft) { vm.notify("Dados salvos. Você pode continuar depois.") } })
                    }
                    composable("order/{id}") { backStack ->
                        val order = state.orders.find { it.id == backStack.arguments?.getString("id") }
                        if (order == null) EmptyState("Pedido não encontrado", "Verifique o histórico local.", Icons.Default.ReceiptLong, "Ver pedidos", { goTab("orders") })
                        else TrackingScreen(order, ui.busy, { vm.advance(order.id) }, { vm.cancel(order.id) }, { vm.reorder(order.id) { openCart() } })
                    }
                }
            }
        }
    }
}
