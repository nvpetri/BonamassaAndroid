package br.com.bonamassa.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bonamassa.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun orderTime(time: Long) = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(time))

@Composable
fun OrdersScreen(state: AppState, onOrder: (String) -> Unit, onMenu: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("Boas lembranças.", "Seus pedidos de demonstração, neste aparelho.") }
        if (state.orders.isEmpty()) item { EmptyState("Seu primeiro pedido vem aí", "Os pedidos de teste aparecerão aqui depois da finalização.", Icons.Default.ReceiptLong, "Escolher uma pizza", onMenu) }
        items(state.orders, key = { it.id }) { order ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${order.number}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Tag(OrderRules.statusLabel(order.status, order.checkout.fulfillment).uppercase(), if (order.status == OrderStatus.CANCELLED) Brand.Red else Brand.Gold)
                }
                Text(orderTime(order.createdAt), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Text(order.items.joinToString("\n") { "${it.quantity}× ${it.title}" }, style = MaterialTheme.typography.bodyMedium)
                PriceLine("${order.checkout.fulfillment.label} · ${order.checkout.payment.label}", money(order.quote.total))
                OutlinedButton(onClick = { onOrder(order.id) }, Modifier.fillMaxWidth()) { Text("Ver pedido") }
            }
        }
    }
}

@Composable
fun TrackingScreen(order: Order, busy: Boolean, onAdvance: () -> Unit, onCancel: () -> Unit, onReorder: () -> Unit) {
    var cancelConfirmation by rememberSaveable { mutableStateOf(false) }
    var reorderConfirmation by rememberSaveable { mutableStateOf(false) }
    val next = OrderRules.nextStatus(order.status, order.checkout.fulfillment)
    val cancelled = order.status == OrderStatus.CANCELLED
    val complete = order.status == OrderStatus.DELIVERED
    if (cancelConfirmation) AlertDialog(onDismissRequest = { cancelConfirmation = false }, title = { Text("Cancelar pedido de teste?") },
        text = { Text("O pedido continuará no histórico como cancelado. Nenhum pagamento foi feito.") },
        confirmButton = { TextButton(onClick = { cancelConfirmation = false; onCancel() }) { Text("Cancelar pedido") } },
        dismissButton = { TextButton(onClick = { cancelConfirmation = false }) { Text("Manter") } })
    if (reorderConfirmation) AlertDialog(onDismissRequest = { reorderConfirmation = false }, title = { Text("Pedir de novo?") },
        text = { Text("Os itens serão adicionados à sacola sem apagar o que já está lá. Preços e cupom serão recalculados pelo cardápio atual.") },
        confirmButton = { TextButton(onClick = { reorderConfirmation = false; onReorder() }) { Text("Adicionar à sacola") } },
        dismissButton = { TextButton(onClick = { reorderConfirmation = false }) { Text("Voltar") } })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Tag("PEDIDO DEMONSTRATIVO #${order.number}")
            Spacer(Modifier.height(12.dp))
            Text(when { cancelled -> "Tudo bem. Fica para a próxima."; complete -> "Um bom momento, completo."; else -> "O seu pedido,\npasso a passo." }, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(orderTime(order.createdAt), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        }
        item {
            Panel {
                Text(OrderRules.statusLabel(order.status, order.checkout.fulfillment), style = MaterialTheme.typography.titleLarge, color = if (cancelled) Brand.Red else Brand.Gold)
                if (!cancelled) {
                    val steps = OrderRules.steps(order.checkout.fulfillment)
                    val current = steps.indexOf(order.status)
                    steps.forEachIndexed { index, status ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                            Box(Modifier.size(34.dp).background(if (index <= current) Brand.Gold else Brand.Raised, CircleShape), contentAlignment = Alignment.Center) {
                                if (index < current || complete) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Brand.Background)
                                else Text("${index + 1}", color = if (index == current) Brand.Background else Brand.Muted)
                            }
                            Text(OrderRules.statusLabel(status, order.checkout.fulfillment), Modifier.padding(start = 14.dp),
                                style = MaterialTheme.typography.titleMedium, color = if (index <= current) Brand.Cream else Brand.Muted)
                        }
                    }
                } else Text("Demonstração cancelada; sem cobrança.", color = Brand.Muted)
            }
        }
        if (next != null) item {
            Panel {
                Tag("CONTROLE DA APRESENTAÇÃO", Brand.Red)
                Text("Você avança as etapas manualmente. Não há cozinha, motoboy ou GPS conectados.", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                PrimaryAction("Simular: ${OrderRules.statusLabel(next, order.checkout.fulfillment)}", Modifier.fillMaxWidth(), !busy, onClick = onAdvance)
            }
        }
        item {
            Panel {
                Text("O que você escolheu", style = MaterialTheme.typography.titleLarge)
                order.items.forEach { line ->
                    Text("${line.quantity}× ${line.title}", style = MaterialTheme.typography.titleMedium)
                    Text(line.details, color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                    if (line.note.isNotBlank()) Text("Obs.: ${line.note}", color = Brand.Gold, style = MaterialTheme.typography.bodySmall)
                    PriceLine("${money(line.unitPrice)} por unidade", money(line.unitPrice * line.quantity))
                }
            }
        }
        item {
            Panel {
                Text("${order.checkout.fulfillment.label} · ${order.checkout.customer.name}", style = MaterialTheme.typography.titleMedium)
                if (order.checkout.fulfillment == Fulfillment.DELIVERY) Text(order.checkout.address.summary(), color = Brand.Muted)
                Text("${order.checkout.payment.label} · sem cobrança nesta demo", color = Brand.Gold)
                if (order.checkout.payment == Payment.CASH && order.checkout.changeFor.isNotBlank()) {
                    val change = OrderRules.parseMoney(order.checkout.changeFor)
                    if (change != null) Text("Troco para ${money(change)} · devolver ${money(change - order.quote.total)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { QuotePanel(order.quote) }
        item { PrimaryAction("Pedir de novo", Modifier.fillMaxWidth(), !busy, icon = Icons.Default.Replay) { reorderConfirmation = true } }
        if (order.status == OrderStatus.RECEIVED) item { TextButton(onClick = { cancelConfirmation = true }, enabled = !busy) { Text("Cancelar pedido de teste", color = Brand.Red) } }
    }
}

@Composable
fun ProfileScreen(state: AppState, busy: Boolean, onSave: (Customer, Address) -> Unit, onReset: () -> Unit) {
    var draft by rememberSaveable(stateSaver = CheckoutSaver) { mutableStateOf(state.checkout) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var resetConfirmation by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val addressFilled = listOf(draft.address.street, draft.address.number, draft.address.cep, draft.address.city, draft.address.district, draft.address.complement).any { it.isNotBlank() }
    val customerFilled = draft.customer.name.isNotBlank() || draft.customer.phone.isNotBlank()
    val errors = (if (customerFilled) OrderRules.customerErrors(draft.customer) else emptyMap()) + (if (addressFilled) OrderRules.addressErrors(draft.address) else emptyMap())
    if (resetConfirmation) AlertDialog(onDismissRequest = { resetConfirmation = false }, title = { Text("Apagar os dados locais?") },
        text = { Text("Isso remove sacola, favoritos, perfil, endereço e histórico deste app. Não é possível desfazer e não existe cópia na nuvem.") },
        confirmButton = { TextButton(onClick = { resetConfirmation = false; onReset() }) { Text("Apagar dados") } },
        dismissButton = { TextButton(onClick = { resetConfirmation = false }) { Text("Manter meus dados") } })
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Bonamassa · 0.2.0 demo") },
        text = { Text("App Android nativo em Kotlin e Jetpack Compose. Funciona offline com dados armazenados no aparelho. Não há login real, cobrança, envio de pedidos, contato por WhatsApp ou rastreamento. Cardápio, regra de meio a meio e entrega são exemplos. Evite dados pessoais reais durante testes.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Entendi") } })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { SectionHeading("Do seu jeito.", "Perfil local, sem conta online.") }
        item { Panel { Text("Seu próximo pedido fica mais fácil", style = MaterialTheme.typography.titleMedium); Text("Salve um perfil e um endereço para preencher os próximos pedidos de teste. Não pedimos senha.", color = Brand.Muted, style = MaterialTheme.typography.bodyMedium) } }
        item {
            SectionHeading("Seus dados")
            Spacer(Modifier.height(12.dp))
            CustomerFields(draft.customer, { draft = draft.copy(customer = it) }, if (submitted) errors else emptyMap())
        }
        item {
            SectionHeading("Seu endereço", "Opcional para retirada. Para remover, deixe todos os campos vazios.")
            Spacer(Modifier.height(12.dp))
            AddressFields(draft.address, { draft = draft.copy(address = it) }, if (submitted) errors else emptyMap())
        }
        item {
            if (submitted && errors.isNotEmpty()) Text("Revise os campos destacados acima.", color = MaterialTheme.colorScheme.error)
            PrimaryAction("Salvar perfil neste aparelho", Modifier.fillMaxWidth(), !busy) { submitted = true; if (errors.isEmpty()) onSave(draft.customer, draft.address) }
        }
        item {
            Panel {
                Text("Você controla seus dados", style = MaterialTheme.typography.titleMedium)
                Text("Sem analytics, localização ou transmissão de dados. O histórico mantém os últimos 100 pedidos de teste. Ao apagar os dados do app ou desinstalá-lo, tudo é removido.", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { about = true }) { Text("Sobre esta versão") }
                TextButton(onClick = { resetConfirmation = true }, enabled = !busy) { Text("Apagar dados da demonstração", color = Brand.Red) }
            }
        }
    }
}
