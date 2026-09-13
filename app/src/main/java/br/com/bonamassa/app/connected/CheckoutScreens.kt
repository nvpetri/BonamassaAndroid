package br.com.bonamassa.app.connected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.bonamassa.app.ui.*
import br.com.bonamassa.client.*
import br.com.bonamassa.core.money

@Composable
fun AuthScreen(busy: Boolean, initialEmail: String, submit: (String, String, String?, String?) -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    // Password never enters saved-instance state or persistent storage.
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(if (register) "Chegue mais." else "Bom ter você aqui.", "Entre para pedir e acompanhar sua pizza.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!register, { if (!busy) register = false }, { Text("Entrar") })
            FilterChip(register, { if (!busy) register = true }, { Text("Criar conta") })
        }
        if (register) {
            Input("Seu nome", name, { name = it.take(80) })
            Input("Telefone com DDD", phone, { phone = it.take(20) }, type = KeyboardType.Phone)
        }
        Input("E-mail", email, { email = it.take(254) }, type = KeyboardType.Email)
        OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth(), label = { Text("Senha") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy)
        if (register) Text("Use de 12 a 128 caracteres na senha.", color = Brand.Muted)
        PrimaryAction(if (register) "Criar minha conta" else "Entrar na minha conta", Modifier.fillMaxWidth(), !busy && email.isNotBlank() && password.isNotBlank()) {
            submit(email, password, if (register) name else null, if (register) phone else null)
        }
    }
}

@Composable
fun ConnectedCart(ui: CustomerUi, quantity: (String, Int) -> Unit, remove: (String) -> Unit, edit: (DraftLine) -> Unit, menu: () -> Unit, checkout: () -> Unit) {
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = !ui.busy && ui.saved.pending == null
    if (removing != null) AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remover da sacola?") },
        confirmButton = { TextButton(onClick = { removing?.let(remove); removing = null }) { Text("Remover") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Manter") } })
    if (ui.saved.cart.isEmpty()) {
        EmptyState("Sua sacola está esperando", "Escolha uma pizza e deixe seu dia mais gostoso.", Icons.Default.ShoppingBag, "Ver cardápio", menu)
        return
    }
    val estimated = ui.catalog?.let { c -> runCatching { ui.saved.cart.sumOf { c.estimate(it) * it.quantity } }.getOrNull() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(ui.saved.cart, key = { it.localId }) { line ->
                Panel {
                    Text(ui.catalog?.title(line) ?: "Item da sua sacola", style = MaterialTheme.typography.titleLarge)
                    ui.catalog?.detail(line)?.takeIf { it.isNotBlank() }?.let { Text(it, color = Brand.Muted) }
                    if (line.note.isNotBlank()) Text(line.note)
                    val price = ui.catalog?.let { runCatching { it.estimate(line) }.getOrNull() }
                    if (price == null) Text("Confira este item no cardápio atualizado.", color = Brand.Gold)
                    else PriceLine("${line.quantity}× ${money(price)}", money(line.quantity * price))
                    QuantityControl(line.quantity, { quantity(line.localId, it) }, editable)
                    Row {
                        TextButton(onClick = { edit(line) }, enabled = editable && price != null) { Text("Editar") }
                        TextButton(onClick = { removing = line.localId }, enabled = editable) { Text("Remover", color = Brand.Red) }
                    }
                }
            }
            item {
                Panel {
                    PriceLine("Prévia dos produtos", estimated?.let(::money) ?: "Atualize o cardápio", true)
                    Text("Entrega e promoção serão calculadas na próxima etapa.", color = Brand.Muted)
                    if (ui.catalog?.open == false) Text("A pizzaria está fechada para novos pedidos.", color = Brand.Gold)
                }
            }
        }
        BottomAction(if (ui.saved.session == null) "Entrar para continuar" else "Continuar pedido", enabled = editable && estimated != null && ui.catalog?.open == true, onClick = checkout)
    }
}

@Composable
fun ConnectedCheckout(ui: CustomerUi, submit: (Checkout) -> Unit) {
    val initial = ui.saved.checkout
    var mode by rememberSaveable { mutableStateOf(initial.mode) }
    var payment by rememberSaveable { mutableStateOf(initial.payment) }
    var street by rememberSaveable { mutableStateOf(initial.address.street) }
    var number by rememberSaveable { mutableStateOf(initial.address.number) }
    var neighborhood by rememberSaveable { mutableStateOf(initial.address.neighborhood) }
    var city by rememberSaveable { mutableStateOf(initial.address.city) }
    var state by rememberSaveable { mutableStateOf(initial.address.state) }
    var cep by rememberSaveable { mutableStateOf(initial.address.postalCode) }
    var reference by rememberSaveable { mutableStateOf(initial.address.reference) }
    var cash by rememberSaveable { mutableStateOf(initial.cash) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var promotion by rememberSaveable { mutableStateOf(initial.promotionId) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeading("Como você prefere?")
            Mode.entries.forEach { m -> OptionRow(m.label, if (m == Mode.DELIVERY) "Receba no seu endereço" else "Busque na pizzaria", mode == m, { mode = m }) }
            ui.saved.session?.user?.let { Text("Pedido de ${it.name} · ${it.phone}", color = Brand.Muted) }
            if (mode == Mode.DELIVERY) {
                SectionHeading("Onde vamos entregar?")
                Input("Rua", street, { street = it.take(120) })
                Input("Número", number, { number = it.take(20) })
                Input("Bairro", neighborhood, { neighborhood = it.take(80) })
                Input("Cidade", city, { city = it.take(80) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Input("UF", state, { state = it.uppercase().take(2) }, Modifier.weight(1f))
                    Input("CEP", cep, { cep = it.filter(Char::isDigit).take(8) }, Modifier.weight(2f), type = KeyboardType.Number)
                }
                Input("Complemento ou referência", reference, { reference = it.take(240) }, singleLine = false)
            }
            SectionHeading("Pagamento no recebimento")
            OptionRow(Method.CARD.label, "Leve o cartão para usar na maquininha", payment == Method.CARD, { payment = Method.CARD })
            OptionRow(Method.CASH.label, "Pode deixar em branco se não precisar de troco", payment == Method.CASH, { payment = Method.CASH })
            if (payment == Method.CASH) Input("Troco para quanto? Ex.: 100,00", cash, { cash = it.take(12) }, type = KeyboardType.Decimal)
            SectionHeading("Promoção")
            OptionRow("Sem promoção", "Continuar com os preços do cardápio", promotion == null, { promotion = null })
            ui.catalog?.promotions?.forEach { p ->
                OptionRow(p.name, "${if (p.kind == "PERCENTAGE") "${p.value}%" else money(p.value)} sobre pizzas elegíveis${p.remaining?.let { " · até $it pizzas" }.orEmpty()}", promotion == p.id, { promotion = p.id })
            }
            if (promotion != null && ui.catalog?.promotions?.none { it.id == promotion } == true) Text("A promoção selecionada não está mais disponível. Escolha outra opção.", color = Brand.Gold)
            Text("Descontos não incluem bordas, bebidas, combos ou entrega. A cotação mostra quantas pizzas receberam o benefício.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            Input("Observações do pedido", note, { note = it.take(240) }, singleLine = false)
        }
        BottomAction("Conferir valores", enabled = !ui.busy && ui.saved.pending == null && ui.catalog?.open == true) {
            submit(Checkout(mode, Address(street, number, neighborhood, city, state, cep, reference), payment, cash, note, promotion))
        }
    }
}

@Composable
fun TotalsPanel(totals: Totals) {
    Panel {
        PriceLine("Produtos", money(totals.subtotal))
        if (totals.discount > 0) PriceLine(totals.promotion ?: "Promoção", "− ${money(totals.discount)}")
        if (totals.discountedPizzas > 0) Text("${totals.discountedPizzas} pizza(s) com desconto", style = MaterialTheme.typography.bodySmall, color = Brand.Green)
        PriceLine("Taxa de entrega", money(totals.fee))
        HorizontalDivider(color = Brand.Border)
        PriceLine("Total", money(totals.total), true)
    }
}
@Composable
fun Receipt(lines: List<ReceiptLine>) {
    lines.forEach { line ->
        Panel {
            Text("${line.quantity}× ${line.name}", style = MaterialTheme.typography.titleMedium)
            Text(line.detail, color = Brand.Muted)
            line.components.forEach { c -> Text("Inclui ${c.quantity}× ${c.name}", style = MaterialTheme.typography.bodyMedium); Text(c.detail, style = MaterialTheme.typography.bodySmall, color = Brand.Muted); if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall) }
            if (line.note.isNotBlank()) Text(line.note)
            PriceLine("${money(line.unitPrice)} cada", money(line.unitPrice * line.quantity))
        }
    }
}
@Composable
fun ConnectedReview(ui: CustomerUi, back: () -> Unit, confirm: () -> Unit) {
    val review = ui.review
    if (review == null) {
        EmptyState("Confira os valores novamente", "A sacola foi mantida. Atualize a cotação para continuar.", Icons.Default.ReceiptLong, "Voltar ao pedido", back)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Tag("VALORES CONFIRMADOS PELA PIZZARIA", Brand.Green)
            SectionHeading("Tudo certo com o pedido?")
            Receipt(review.quote.items)
            TotalsPanel(review.quote.totals)
            Panel {
                Text(review.checkout.mode.label, style = MaterialTheme.typography.titleMedium)
                if (review.checkout.mode == Mode.DELIVERY) Text(review.checkout.address.summary())
                Text(review.checkout.payment.label)
                if (review.checkout.payment == Method.CASH) {
                    val cash = parseCash(review.checkout.cash)
                    Text(if (cash == null) "Sem troco" else "Dinheiro: ${money(cash)} · Troco: ${money((cash - review.quote.totals.total).coerceAtLeast(0))}")
                }
                if (review.checkout.note.isNotBlank()) Text(review.checkout.note)
            }
            Text("Ao confirmar, o pedido será enviado à pizzaria. O pagamento será no recebimento.", color = Brand.Muted)
            TextButton(onClick = back, enabled = !ui.busy && ui.saved.pending == null) { Text("Alterar pedido") }
        }
        BottomAction("Confirmar e enviar pedido", money(review.quote.totals.total), !ui.busy && ui.saved.pending == null && ui.saved.session != null, confirm)
    }
}
