package br.com.bonamassa.app.connected

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bonamassa.app.BuildConfig
import br.com.bonamassa.app.R
import br.com.bonamassa.app.ui.*
import br.com.bonamassa.client.*
import br.com.bonamassa.core.money
import coil.imageLoader
import coil.compose.SubcomposeAsyncImage

@Composable
private fun ProductPhoto(product: Product, endpoint: Endpoint, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (product.kind == Kind.DRINK) Icons.Default.LocalDrink else Icons.Default.LocalPizza, null, Modifier.size(64.dp), tint = Brand.Gold)
                Text("${product.kind.label} Bonamassa", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Surface(modifier.clip(RoundedCornerShape(18.dp)), color = Brand.Raised) {
        SubcomposeAsyncImage(model = endpoint.photo(product.photo), contentDescription = product.name, imageLoader = context.imageLoader,
            contentScale = ContentScale.Crop, loading = { placeholder() }, error = { placeholder() }, modifier = Modifier.fillMaxSize())
    }
}
@Composable
fun ConnectedMenu(ui: CustomerUi, home: Boolean, menu: () -> Unit, open: (String) -> Unit, favorite: (String) -> Unit, order: (String) -> Unit) {
    val catalog = ui.catalog
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    if (catalog == null) {
        EmptyState("O sabor da Bonamassa, na sua mão", if (ui.refreshing) "Carregando o cardápio da pizzaria…" else "Assim que a conexão estiver disponível, o cardápio aparecerá aqui.", Icons.Default.LocalPizza)
        return
    }
    val endpoint = Endpoint.parse(ui.saved.origin, ui.saved.slug, BuildConfig.DEBUG)
    val products = catalog.products.filter { p -> p.kind != Kind.CRUST && p.available &&
        (query.isBlank() || "${p.name} ${p.description}".contains(query, true)) && (!onlyFavorites || p.id in ui.saved.favorites) &&
        when (filter) { "Tradicionais" -> p.kind == Kind.PIZZA && p.group == "TRADITIONAL"; "Especiais" -> p.kind == Kind.PIZZA && p.group == "SPECIAL"; "Pizzas" -> p.kind == Kind.PIZZA; "Bebidas" -> p.kind == Kind.DRINK; "Combos" -> p.kind == Kind.COMBO; else -> true }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!catalog.open) item { ReservationNotice(catalog) }
        if (home) item {
            Panel {
                Image(painterResource(R.drawable.bonamassa_logo), "Bonamassa", Modifier.size(76.dp))
                Tag(if (catalog.open) "ABERTA PARA PEDIDOS" else if (catalog.reservationsAvailable) "FECHADA · RESERVE SUA PIZZA" else "FECHADA NO MOMENTO", if (catalog.open) Brand.Green else Brand.Gold)
                Text("Sua próxima pizza\ncomeça aqui.", style = MaterialTheme.typography.headlineLarge)
                Text("Escolha seus sabores, capriche na borda e deixe o resto com a gente.", color = Brand.Muted)
                if (catalog.reservationsAvailable) Text("Todos os dias · ${catalog.opensAt} às ${catalog.closesAt} · São Paulo", color = Brand.Muted)
                PrimaryAction("Explorar cardápio", Modifier.fillMaxWidth(), onClick = menu)
            }
        }
        if (home) ui.orders.firstOrNull { it.status.active }?.let { current -> item {
            Panel(Modifier.clickable { order(current.id) }) { Tag("PEDIDO #${current.number}"); Text(current.statusLabel, style = MaterialTheme.typography.titleLarge); Text("Toque para acompanhar", color = Brand.Muted) }
        } }
        if (catalog.promotions.isNotEmpty()) item {
            SectionHeading("Hoje tem vantagem")
            catalog.promotions.forEach { p ->
                Text("${p.name} · ${if (p.kind == "PERCENTAGE") "${p.value}%" else money(p.value)} de desconto", color = Brand.Gold)
                Text(listOfNotNull(p.remaining?.let { "Até $it pizzas disponíveis" }, p.endsAt?.let { "Até ${dateTime(it)}" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            Text("Selecione a promoção ao finalizar. A disponibilidade é confirmada no envio.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            SectionHeading("Cardápio", "${catalog.name} · ${if (catalog.deliveryFee == 0L) "Entrega grátis" else "Entrega ${money(catalog.deliveryFee)}"}")
            Spacer(Modifier.height(12.dp))
            Input("Buscar sabores e ingredientes", query, { query = it })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Pizzas", "Tradicionais", "Especiais", "Bebidas", "Combos").forEach { label -> FilterChip(filter == label, { filter = label }, { Text(label) }) }
            }
            FilterChip(onlyFavorites, { onlyFavorites = !onlyFavorites }, { Text("Só favoritos") }, leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, Modifier.size(18.dp)) })
        }
        if (products.isEmpty()) item { EmptyState("Nenhum produto por aqui", "Tente outro filtro ou aguarde a pizzaria atualizar o cardápio.", Icons.Default.Search) }
        items(products, key = { it.id }) { p ->
            Panel {
                ProductPhoto(p, endpoint, Modifier.fillMaxWidth().height(160.dp).clickable { open(p.id) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f).clickable { open(p.id) }, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { favorite(p.id) }, enabled = !ui.busy) { Icon(if (p.id in ui.saved.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favoritar ${p.name}", tint = Brand.Red) }
                }
                Text(p.description, color = Brand.Muted)
                p.individualTotal?.takeIf { it > p.price() }?.let { Text("Avulsos ${money(it)} · Economia ${money(it - p.price())}", color = Brand.Green) }
                PrimaryAction("${if (p.kind == Kind.PIZZA) "Montar · grande " else "Escolher · "}${money(p.price())}", Modifier.fillMaxWidth(), enabled = !ui.busy && ui.saved.pending == null) { open(p.id) }
            }
        }
    }
}
@Composable
fun ConnectedProduct(catalog: Catalog, product: Product, edit: DraftLine?, ui: CustomerUi, endpoint: Endpoint, put: (DraftLine) -> Unit) {
    var size by rememberSaveable { mutableStateOf(edit?.size ?: Size.LARGE) }
    var half by rememberSaveable { mutableStateOf(edit?.flavorIds?.size == 2) }
    var second by rememberSaveable { mutableStateOf(edit?.flavorIds?.getOrNull(1).orEmpty()) }
    var crust by rememberSaveable { mutableStateOf(edit?.crust ?: "NONE") }
    var quantity by rememberSaveable { mutableIntStateOf(edit?.quantity ?: 1) }
    var note by rememberSaveable { mutableStateOf(edit?.note.orEmpty()) }
    val localId = rememberSaveable { edit?.localId ?: java.util.UUID.randomUUID().toString() }
    val line = DraftLine(product.kind, product.id, if (product.kind == Kind.PIZZA) listOf(product.id) + if (half) listOf(second) else emptyList() else emptyList(), size, crust, quantity, note, localId)
    val price = runCatching { catalog.estimate(line) }.getOrNull()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProductPhoto(product, endpoint, Modifier.fillMaxWidth().height(210.dp))
            Text(product.name, style = MaterialTheme.typography.headlineLarge)
            Text(product.description, color = Brand.Muted)
            if (product.kind == Kind.PIZZA) {
                SectionHeading("Qual tamanho?")
                Size.entries.forEach { s -> OptionRow(s.label, money(product.price(s)), s == size, { size = s }) }
                SectionHeading("Seus sabores")
                OptionRow("Inteira", product.name, !half, { half = false })
                OptionRow("Meio a meio", "Vale o preço do sabor de maior valor", half, { half = true })
                if (half) catalog.products.filter { it.kind == Kind.PIZZA && it.available && it.id != product.id }.forEach { p -> OptionRow(p.name, money(p.price(size)), p.id == second, { second = p.id }) }
                if (half && second.isEmpty()) Text("Escolha o segundo sabor.", color = Brand.Gold)
                SectionHeading("Borda recheada")
                OptionRow("Sem borda recheada", "Incluso", crust == "NONE", { crust = "NONE" })
                catalog.products.filter { it.kind == Kind.CRUST && it.available }.forEach { p -> OptionRow(p.name, "+ ${money(p.price())}", crust == p.id, { crust = p.id }) }
            }
            if (product.kind == Kind.COMBO) {
                Panel {
                    Text("O que vem no combo", style = MaterialTheme.typography.titleMedium)
                    product.combo.forEach { component -> Text("${component.quantity}× ${catalog.title(component)}"); Text(catalog.detail(component), color = Brand.Muted, style = MaterialTheme.typography.bodySmall) }
                    Text("A combinação de sabores é definida pela pizzaria para esta oferta.", color = Brand.Muted)
                    product.individualTotal?.takeIf { it > product.price() }?.let { PriceLine("Economia do combo", money(it - product.price()), true) }
                }
            }
            if (product.kind != Kind.DRINK) Input("Alguma observação?", note, { note = it.take(240) }, singleLine = false)
            QuantityControl(quantity, { quantity = it }, !ui.busy && ui.saved.pending == null)
            Text("O total será conferido com a pizzaria antes do envio.", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
        }
        BottomAction(if (edit == null) "Adicionar à sacola" else "Salvar item", price?.let { money(it * quantity) }, !ui.busy && ui.saved.pending == null && price != null) { put(line) }
    }
}
