package br.com.bonamassa.app.connected

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.bonamassa.app.ui.*
import br.com.bonamassa.client.Kind
import kotlinx.coroutines.delay

@Composable
fun CustomerApp(vm: CustomerViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, vm) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) { vm.refresh(); delay(if (vm.ui.value.syncError == null) 5_000 else 15_000) }
            } finally { vm.stopRefreshing() }
        }
    }
    var route by rememberSaveable { mutableStateOf("home") }
    var productId by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var authReturn by rememberSaveable { mutableStateOf("profile") }
    var addedToCart by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.error) { ui.error?.let { snackbar.showSnackbar(it); vm.clearError() } }
    fun order(id: String) { vm.selectOrder(id); route = "order" }
    fun auth(target: String) { authReturn = target; route = "auth" }
    fun back() {
        route = when (route) { "review" -> { vm.discardReview(); "checkout" }; "checkout" -> "cart"; "product" -> if (editId != null) "cart" else "menu"; "order" -> "orders"; "auth" -> authReturn; else -> "home" }
    }
    BackHandler(route !in listOf("home", "menu", "orders", "profile")) { if (!ui.busy) back() }
    if (ui.fatal) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.padding(padding)) {
                EmptyState("Não foi possível abrir seus dados", "Tente carregar novamente. Um envio pendente pode estar salvo neste aparelho.", Icons.Default.Lock, "Tentar novamente", vm::load)
            }
        }
        return
    }
    if (!ui.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val isTab = route in listOf("home", "menu", "orders", "profile")
    if (addedToCart && route == "cart") AlertDialog(
        onDismissRequest = { addedToCart = false }, title = { Text("Produto adicionado à sacola") },
        text = { Text("Sua sacola tem ${ui.saved.cart.sumOf { it.quantity }} produto(s). Deseja adicionar mais alguma coisa antes de revisar o pedido? Nada foi enviado à pizzaria ainda.") },
        confirmButton = { TextButton(onClick = { addedToCart = false; if (ui.saved.session == null) auth("checkout") else route = "checkout" }, enabled = !ui.busy) { Text("Ir para checkout") } },
        dismissButton = { TextButton(onClick = { addedToCart = false; route = "menu" }, enabled = !ui.busy) { Text("Continuar comprando") } })
    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                BrandHeader(when (route) { "cart" -> "Sua sacola"; "checkout" -> "Entrega e pagamento"; "review" -> "Confira seu pedido"; "order" -> "Acompanhar pedido"; "auth" -> "Sua conta"; else -> "Monte do seu jeito" },
                    if (isTab) null else ({ if (!ui.busy) back() }), ui.saved.cart.sumOf { it.quantity }, { if (!ui.busy) route = "cart" })
                if (ui.busy || ui.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand.Gold)
                ui.syncError?.let { error ->
                    Surface(color = Brand.Raised) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.refresh() }, enabled = !ui.refreshing && !ui.busy) { Text("Atualizar") }
                        }
                    }
                }
                ui.saved.pending?.let {
                    Surface(color = Brand.Gold.copy(alpha = .12f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Envio aguardando confirmação", color = Brand.Gold, style = MaterialTheme.typography.titleSmall)
                            Text("Verifique o resultado antes de fazer outro pedido.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { if (ui.saved.session == null) auth("cart") else vm.retryPending(::order) }, enabled = !ui.busy) { Text(if (ui.saved.session == null) "Entrar para verificar" else "Verificar envio") }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = Brand.Background) {
                listOf(Triple("home", "Início", Icons.Default.Home), Triple("menu", "Cardápio", Icons.Default.LocalPizza), Triple("orders", "Pedidos", Icons.Default.ReceiptLong), Triple("profile", "Conta", Icons.Default.PersonOutline)).forEach { (target, label, icon) ->
                    NavigationBarItem(route == target, { if (!ui.busy) route = target }, { Icon(icon, null) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (route) {
                "home", "menu" -> ConnectedMenu(ui, route == "home", { route = "menu" }, { id -> productId = id; editId = null; route = "product" }, vm::favorite, ::order)
                "product" -> {
                    val catalog = ui.catalog
                    val product = catalog?.products?.find { it.id == productId }
                    if (catalog != null && product != null) key(productId, editId) {
                        ConnectedProduct(catalog, product, ui.saved.cart.find { it.localId == editId }, ui, vm.endpoint()) { line -> vm.put(line) { addedToCart = editId == null; route = "cart" } }
                    } else EmptyState("Produto indisponível", "Atualize o cardápio para escolher outro sabor.", Icons.Default.LocalPizza, "Ver cardápio", { route = "menu" })
                }
                "cart" -> ConnectedCart(ui, vm::quantity, vm::remove, { line -> productId = if (line.kind == Kind.PIZZA) line.flavorIds.first() else line.productId; editId = line.localId; route = "product" }, { route = "menu" }) {
                    if (ui.saved.session == null) auth("cart") else route = "checkout"
                }
                "checkout" -> if (ui.saved.session == null) AuthScreen(ui.busy, ui.saved.account?.email.orEmpty()) { email, password, name, phone -> vm.signIn(email, password, name, phone) { route = "checkout" } }
                    else ConnectedCheckout(ui) { checkout -> vm.quote(checkout) { route = "review" } }
                "review" -> ConnectedReview(ui, { route = "checkout"; vm.discardReview() }, { vm.place(::order) }, { vm.discardReview(); route = "menu" })
                "auth" -> AuthScreen(ui.busy, ui.saved.account?.email.orEmpty()) { email, password, name, phone -> vm.signIn(email, password, name, phone) { route = authReturn } }
                "orders" -> ConnectedOrders(ui, { auth("orders") }, ::order, vm::moreOrders, { vm.refresh() })
                "order" -> ConnectedOrder(ui, ui.orders.find { it.id == ui.selectedOrder }, { vm.refresh() }) { order, reason -> vm.cancel(order, reason) {} }
                "profile" -> ConnectedProfile(ui, { auth("profile") }, vm::logout)
            }
        }
    }
}
