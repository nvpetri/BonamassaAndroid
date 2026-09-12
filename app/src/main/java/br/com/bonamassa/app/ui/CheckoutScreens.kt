package br.com.bonamassa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.*

val CheckoutSaver = listSaver<Checkout, String>(
    save = { listOf(it.customer.name, it.customer.phone, it.address.street, it.address.number, it.address.district,
        it.address.city, it.address.cep, it.address.complement, it.fulfillment.name, it.payment.name, it.changeFor) },
    restore = { Checkout(Customer(it[0], it[1]), Address(it[2], it[3], it[4], it[5], it[6], it[7]), Fulfillment.valueOf(it[8]), Payment.valueOf(it[9]), it[10]) }
)

@Composable
fun CartScreen(state: AppState, busy: Boolean, onMenu: () -> Unit, onEdit: (String) -> Unit, onRemove: (String) -> Unit,
               onQuantity: (String, Int) -> Unit, onCoupon: (String) -> Unit, onFulfillment: (Fulfillment) -> Unit, onCheckout: () -> Unit) {
    val quote = OrderRules.quote(state.cart, state.checkout.fulfillment, state.coupon)
    var code by rememberSaveable(state.coupon) { mutableStateOf(state.coupon) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    if (removeId != null) AlertDialog(onDismissRequest = { removeId = null }, title = { Text("Remover da sacola?") },
        text = { Text("O item e suas personalizações serão removidos.") },
        confirmButton = { TextButton(onClick = { removeId?.let(onRemove); removeId = null }) { Text("Remover") } },
        dismissButton = { TextButton(onClick = { removeId = null }) { Text("Manter") } })
    if (state.cart.isEmpty()) {
        LazyColumn { item { EmptyState("Sua sacola está esperando", "Escolha uma pizza e monte do seu jeito.", Icons.Default.ShoppingBag, "Explorar cardápio", onMenu) } }
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SectionHeading("Seu momento está quase aí.", "${state.cart.sumOf { it.quantity }} itens na sacola") }
            items(state.cart, key = { it.id }) { item ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FoodArt(Catalog.require(item.productId), Modifier.size(76.dp), Catalog.find(item.secondFlavorId))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(OrderRules.title(item), style = MaterialTheme.typography.titleMedium)
                            Text(OrderRules.details(item), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        }
                    }
                    if (item.note.isNotBlank()) Text("Obs.: ${item.note}", style = MaterialTheme.typography.bodySmall, color = Brand.Gold)
                    PriceLine("${item.quantity} × ${money(OrderRules.unitPrice(item))}", money(OrderRules.unitPrice(item) * item.quantity))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        QuantityControl(item.quantity, { onQuantity(item.id, it) }, enabled = !busy)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onEdit(item.id) }, enabled = !busy) { Icon(Icons.Default.Edit, "Editar ${OrderRules.title(item)}") }
                        IconButton(onClick = { removeId = item.id }, enabled = !busy) { Icon(Icons.Default.DeleteOutline, "Remover ${OrderRules.title(item)}", tint = Brand.Red) }
                    }
                }
            }
            item { TextButton(onClick = onMenu) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Adicionar mais itens") } }
            item {
                SectionHeading("Como você prefere?")
                Spacer(Modifier.height(12.dp))
                Fulfillment.entries.forEach { value ->
                    OptionRow(value.label, if (value == Fulfillment.DELIVERY) "Taxa de teste: ${money(OrderRules.DELIVERY_FEE)}" else "Sem taxa de entrega", state.checkout.fulfillment == value) { if (!busy) onFulfillment(value) }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                Panel {
                    Text("Tem um cupom?", style = MaterialTheme.typography.titleMedium)
                    Input("Código", code, { code = it.take(24).uppercase() })
                    Row {
                        TextButton(onClick = { onCoupon(code) }, enabled = !busy) { Text("Aplicar cupom") }
                        if (state.coupon.isNotBlank()) TextButton(onClick = { code = ""; onCoupon("") }, enabled = !busy) { Text("Remover") }
                    }
                    if (quote.couponError != null) Text(quote.couponError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else if (quote.discount > 0) Text("BONA10 aplicado: − ${money(quote.discount)}", color = Brand.Green, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { QuotePanel(quote) }
        }
        BottomAction("Continuar para os dados", money(quote.total), !busy, onCheckout)
    }
}

@Composable
fun CustomerFields(value: Customer, onChange: (Customer) -> Unit, errors: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Input("Seu nome", value.name, { onChange(value.copy(name = it.take(80))) }, error = errors["name"])
        Input("Telefone com DDD", value.phone, { onChange(value.copy(phone = digits(it).take(11))) }, error = errors["phone"], type = KeyboardType.Phone)
    }
}
@Composable
fun AddressFields(value: Address, onChange: (Address) -> Unit, errors: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Input("CEP", value.cep, { onChange(value.copy(cep = digits(it).take(8))) }, error = errors["cep"], type = KeyboardType.Number)
        Input("Rua ou avenida", value.street, { onChange(value.copy(street = it.take(120))) }, error = errors["street"])
        Input("Número ou S/N", value.number, { onChange(value.copy(number = it.take(20))) }, error = errors["number"])
        Input("Bairro", value.district, { onChange(value.copy(district = it.take(80))) }, error = errors["district"])
        Input("Cidade", value.city, { onChange(value.copy(city = it.take(80))) }, error = errors["city"])
        Input("Complemento (opcional)", value.complement, { onChange(value.copy(complement = it.take(120))) }, error = errors["complement"])
    }
}

@Composable
fun CheckoutScreen(state: AppState, busy: Boolean, onPlace: (Checkout) -> Unit, onSaveDraft: (Checkout) -> Unit) {
    var draft by rememberSaveable(stateSaver = CheckoutSaver) { mutableStateOf(state.checkout) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf(false) }
    val quote = OrderRules.quote(state.cart, draft.fulfillment, state.coupon)
    val errors = OrderRules.checkoutErrors(state.cart, draft, quote)
    val shownErrors = if (submitted) errors else emptyMap()
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("Criar pedido de teste?") },
        text = { Text("Total demonstrativo: ${money(quote.total)}. Nada será cobrado e nenhuma pizzaria receberá este pedido. Você poderá simular o andamento na próxima tela.") },
        confirmButton = { TextButton(onClick = { confirmation = false; onPlace(draft) }, enabled = !busy) { Text("Criar demonstração") } },
        dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Revisar") } })
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { SectionHeading("Só falta um detalhe.", "Use dados fictícios para apresentar a demonstração.") }
            if (submitted && errors.isNotEmpty()) item {
                Panel { Text("Revise antes de continuar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    errors.values.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                SectionHeading("Quem vai receber?")
                Spacer(Modifier.height(10.dp))
                CustomerFields(draft.customer, { draft = draft.copy(customer = it) }, shownErrors)
            }
            item {
                SectionHeading("Entrega ou retirada")
                Spacer(Modifier.height(10.dp))
                Fulfillment.entries.forEach { value ->
                    OptionRow(value.label, if (value == Fulfillment.DELIVERY) "Taxa de teste: ${money(OrderRules.DELIVERY_FEE)}" else "Busque na pizzaria · sem taxa", draft.fulfillment == value) { draft = draft.copy(fulfillment = value) }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (draft.fulfillment == Fulfillment.DELIVERY) item {
                SectionHeading("Seu endereço", "Validação de formato; ainda não consulta CEP ou área de entrega.")
                Spacer(Modifier.height(10.dp))
                AddressFields(draft.address, { draft = draft.copy(address = it) }, shownErrors)
            } else item { Panel { Text("Retirada na pizzaria", style = MaterialTheme.typography.titleMedium); Text("O endereço oficial e o horário de retirada serão configurados com a Bonamassa.", color = Brand.Muted) } }
            item {
                SectionHeading("Forma de pagamento", "Nenhuma opção cobra dinheiro nesta versão.")
                Spacer(Modifier.height(10.dp))
                Payment.entries.forEach { value ->
                    OptionRow(value.label, value.detail, draft.payment == value) { draft = draft.copy(payment = value) }
                    Spacer(Modifier.height(8.dp))
                }
                if (draft.payment == Payment.CASH) {
                    Input("Troco para quanto? (opcional)", draft.changeFor, { draft = draft.copy(changeFor = it.take(12)) }, error = shownErrors["changeFor"], type = KeyboardType.Decimal)
                    Text("Deixe vazio se não precisar de troco. Ex.: 100,00", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                }
            }
            item { QuotePanel(quote) }
            item { TextButton(onClick = { onSaveDraft(draft) }, enabled = !busy) { Text("Salvar dados e continuar depois") } }
            item { Text("As informações ficam apenas neste aplicativo, neste aparelho. Sem cadastro online, rastreamento ou compartilhamento.", style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
        }
        BottomAction("Revisar pedido de teste", money(quote.total), !busy && state.cart.isNotEmpty()) { submitted = true; if (errors.isEmpty()) confirmation = true }
    }
}
