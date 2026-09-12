package br.com.bonamassa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.*
import java.util.UUID

@Composable
fun HomeScreen(state: AppState, onMenu: () -> Unit, onProduct: (String) -> Unit, onFavorite: (String) -> Unit, onProfile: () -> Unit, onOrder: (String) -> Unit) {
    val active = state.orders.firstOrNull { it.status !in listOf(OrderStatus.CANCELLED, OrderStatus.DELIVERED) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.checkout.customer.name.isBlank()) "A sua próxima boa escolha." else "Oi, ${state.checkout.customer.name.trim().substringBefore(' ')}.", style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
                    Text("Hoje pede pizza.", style = MaterialTheme.typography.headlineLarge)
                }
                IconButton(onClick = onProfile) { Icon(Icons.Default.LocationOn, "Editar endereço", tint = Brand.Gold) }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF472421), Brand.Surface)), RoundedCornerShape(28.dp))) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Tag("A ESCOLHA DA CASA", Brand.Red)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Boa massa.\nBons momentos.", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("Monte do seu jeito.\nDivida com quem importa.", color = Brand.Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        FoodArt(Catalog.require("bonamassa"), Modifier.weight(.85f).height(175.dp))
                    }
                    PrimaryAction("Montar minha pizza", Modifier.fillMaxWidth(), icon = Icons.Default.LocalPizza) { onProduct("bonamassa") }
                }
            }
        }
        if (active != null) item {
            Panel {
                Tag("PEDIDO DEMONSTRATIVO #${active.number}", Brand.Green)
                Text(OrderRules.statusLabel(active.status, active.checkout.fulfillment), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { onOrder(active.id) }) { Text("Acompanhar demonstração") }
            }
        }
        item { SectionHeading("Escolhas da casa", "Ilustrações e preços demonstrativos.", "Ver tudo", onMenu) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(Catalog.pizzas.take(4), key = { it.id }) { product ->
                    Box(Modifier.width(206.dp)) { ProductTile(product, product.id in state.favorites, { onFavorite(product.id) }, { onProduct(product.id) }) }
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocalOffer, null, tint = Brand.Gold); Spacer(Modifier.width(10.dp)); Text("Uma boa desculpa para pedir.", style = MaterialTheme.typography.titleMedium) }
                Text("BONA10", style = MaterialTheme.typography.headlineMedium, color = Brand.Gold)
                Text("Cupom de teste: 10% em produtos a partir de R$ 60,00. Desconto máximo de R$ 20,00; não inclui a entrega.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                TextButton(onClick = onMenu) { Text("Explorar o cardápio") }
            }
        }
        item { Text("Bonamassa · protótipo local\nCardápio, valores e horários aguardam aprovação da pizzaria.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
    }
}

@Composable
fun MenuScreen(state: AppState, onProduct: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    val results = Catalog.products.filter {
        (category == "ALL" || it.category.name == category) && (!favorites || it.id in state.favorites) &&
            (it.name.contains(query.trim(), ignoreCase = true) || it.description.contains(query.trim(), ignoreCase = true))
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionHeading("O que vai ser hoje?", "Do primeiro pedaço à sobremesa.") }
        item {
            OutlinedTextField(query, { query = it.take(80) }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar sabor ou ingrediente") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Limpar busca") } }, shape = RoundedCornerShape(16.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == "ALL", onClick = { category = "ALL" }, label = { Text("Tudo") }) }
                items(Category.entries) { value -> FilterChip(selected = category == value.name, onClick = { category = value.name }, label = { Text(value.label) }) }
            }
        }
        item { FilterChip(favorites, { favorites = !favorites }, label = { Text("Só favoritos") }, leadingIcon = { Icon(Icons.Default.FavoriteBorder, null, Modifier.size(17.dp)) }) }
        if (results.isEmpty()) item { EmptyState("Nada por aqui ainda", "Tente outro termo ou desative os filtros.", Icons.Default.Search, "Limpar filtros") { query = ""; category = "ALL"; favorites = false } }
        items(results, key = { it.id }) { product -> ProductTile(product, false, {}, { onProduct(product.id) }, compact = true) }
    }
}

@Composable
fun BuilderScreen(product: Product, existing: CartItem?, favorite: Boolean, busy: Boolean, onFavorite: () -> Unit, onSave: (CartItem) -> Unit) {
    val draftKey = existing?.id ?: product.id
    var sizeName by rememberSaveable(draftKey) { mutableStateOf((existing?.size ?: PizzaSize.LARGE).name) }
    var crustName by rememberSaveable(draftKey) { mutableStateOf((existing?.crust ?: Crust.NONE).name) }
    var secondId by rememberSaveable(draftKey) { mutableStateOf(existing?.secondFlavorId ?: "") }
    var extraNames by rememberSaveable(draftKey) { mutableStateOf(existing?.extras?.map { it.name } ?: emptyList<String>()) }
    var quantity by rememberSaveable(draftKey) { mutableIntStateOf(existing?.quantity ?: 1) }
    var note by rememberSaveable(draftKey) { mutableStateOf(existing?.note ?: "") }
    val stableId = rememberSaveable(draftKey) { existing?.id ?: UUID.randomUUID().toString() }
    val pizza = product.category == Category.PIZZA
    val item = CartItem(stableId, product.id, secondId.ifBlank { null }, PizzaSize.valueOf(sizeName), Crust.valueOf(crustName), extraNames.map(Extra::valueOf).toSet(), quantity, note)
    val price = OrderRules.unitPrice(item)
    var chooseFlavor by rememberSaveable { mutableStateOf(false) }

    if (chooseFlavor) AlertDialog(onDismissRequest = { chooseFlavor = false }, title = { Text("Escolha o outro sabor") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Catalog.pizzas.filter { it.id != product.id }) { other ->
                OptionRow(other.name, "Grande: ${money(other.price)}", other.id == secondId) { secondId = other.id; chooseFlavor = false }
            }
        } }, confirmButton = { TextButton(onClick = { chooseFlavor = false }) { Text("Fechar") } })

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Box(Modifier.fillMaxWidth().background(Brand.Raised, RoundedCornerShape(26.dp))) {
                    FoodArt(product, Modifier.fillMaxWidth().height(230.dp), Catalog.find(secondId))
                    IconButton(onClick = onFavorite, Modifier.align(Alignment.TopEnd), enabled = !busy) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Alternar favorito", tint = Brand.Red) }
                }
            }
            item {
                Tag(product.badge)
                Spacer(Modifier.height(10.dp))
                Text(product.name, style = MaterialTheme.typography.headlineLarge)
                Text(product.description, style = MaterialTheme.typography.bodyMedium, color = Brand.Muted)
            }
            if (pizza) {
                item {
                    SectionHeading("01. O tamanho", "Escolha quanto compartilhar.")
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PizzaSize.entries.forEach { size ->
                        OptionRow(size.label, "${size.detail} · base ${money(product.price + size.delta)}", sizeName == size.name) { sizeName = size.name }
                    } }
                }
                item {
                    SectionHeading("02. Um ou dois sabores?", "Meio a meio: vale o preço do sabor mais caro.")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(secondId.isBlank(), { secondId = "" }, label = { Text("Um sabor") })
                        FilterChip(secondId.isNotBlank(), { chooseFlavor = true }, label = { Text("Meio a meio") })
                    }
                    if (secondId.isNotBlank()) {
                        Text("½ ${product.name} + ½ ${Catalog.require(secondId).name}", color = Brand.Gold)
                        TextButton(onClick = { chooseFlavor = true }) { Text("Trocar segundo sabor") }
                    }
                }
                item {
                    SectionHeading("03. A borda")
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Crust.entries.forEach { crust ->
                        OptionRow(crust.label, if (crust.price == 0L) "Inclusa" else "+ ${money(crust.price)} por pizza", crustName == crust.name) { crustName = crust.name }
                    } }
                }
                item {
                    SectionHeading("04. Um toque a mais", "Adicionais por pizza inteira, não por metade.")
                    Extra.entries.forEach { extra ->
                        Row(Modifier.fillMaxWidth().toggleable(value = extra.name in extraNames, role = Role.Checkbox,
                            onValueChange = { checked -> extraNames = if (checked) extraNames + extra.name else extraNames - extra.name }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(extra.name in extraNames, null)
                            Column(Modifier.weight(1f)) { Text(extra.label); Text("+ ${money(extra.price)}", color = Brand.Gold, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item {
                Input("Observação (opcional)", note, { note = it.take(240) }, singleLine = false)
                Text("${note.length}/240 · Ex.: retirar cebola", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PriceLine("Valor unitário", money(price))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Quantidade", style = MaterialTheme.typography.titleMedium)
                        QuantityControl(quantity, { quantity = it })
                    }
                }
            }
        }
        BottomAction(if (existing == null) "Adicionar à sacola" else "Salvar alterações", money(price * quantity), !busy) { onSave(item) }
    }
}
