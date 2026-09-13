package br.com.bonamassa.app.connected

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bonamassa.app.ui.*
import br.com.bonamassa.client.*
import br.com.bonamassa.core.money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun dateTime(value: String) = runCatching { DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }.getOrDefault("Horário indisponível")

@Composable
fun ConnectedOrders(ui: CustomerUi, login: () -> Unit, open: (String) -> Unit, more: () -> Unit, refresh: () -> Unit) {
    if (ui.saved.session == null) {
        EmptyState("Seus pedidos, sempre por perto", "Entre na sua conta para acompanhar o preparo e consultar seu histórico.", Icons.Default.ReceiptLong, "Entrar na conta", login)
        return
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("Meus pedidos", action = "Atualizar", onAction = refresh) }
        if (ui.orders.isEmpty()) item { EmptyState(if (ui.refreshing) "Consultando seus pedidos…" else "Nenhum pedido carregado", "Os pedidos desta conta aparecem aqui quando a conexão com a pizzaria está disponível.", Icons.Default.ReceiptLong) }
        items(ui.orders, key = { it.id }) { order ->
            Panel(Modifier.clickable { open(order.id) }) {
                Tag("PEDIDO #${order.number}", if (order.status.active) Brand.Gold else Brand.Muted)
                Text(order.statusLabel, style = MaterialTheme.typography.titleLarge)
                Text("${dateTime(order.createdAt)} · ${order.mode.label}", color = Brand.Muted)
                Text(order.items.joinToString(" • ") { "${it.quantity}× ${it.name}" }, style = MaterialTheme.typography.bodyMedium)
                PriceLine("Total", money(order.totals.total), true)
                Text("Ver detalhes", color = Brand.Red)
            }
        }
        if (ui.cursor != null) item { OutlinedButton(onClick = more, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("Carregar pedidos anteriores") } }
    }
}
@Composable
fun ConnectedOrder(ui: CustomerUi, order: Order?, refresh: () -> Unit, cancel: (Order, String) -> Unit) {
    var cancelling by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    if (order == null || ui.saved.session == null) {
        EmptyState("Consultando o pedido", "Se a sessão expirou, entre novamente pela aba Conta. Você também pode atualizar a consulta.", Icons.Default.ReceiptLong, "Atualizar", refresh)
        return
    }
    if (cancelling) AlertDialog(onDismissRequest = { cancelling = false }, title = { Text("Cancelar pedido #${order.number}?") },
        text = { Column { Text("O cancelamento só é permitido enquanto a pizzaria ainda não aceitou o pedido."); Input("Motivo do cancelamento", reason, { reason = it.take(240) }, singleLine = false) } },
        confirmButton = { TextButton(onClick = { cancelling = false; cancel(order, reason) }, enabled = reason.isNotBlank() && !ui.busy && ui.saved.pending == null) { Text("Confirmar cancelamento", color = Brand.Red) } },
        dismissButton = { TextButton(onClick = { cancelling = false }) { Text("Manter pedido") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Tag("PEDIDO #${order.number}")
        Text(order.statusLabel, style = MaterialTheme.typography.headlineLarge)
        Text(when (order.status) {
            Status.NEW -> "Seu pedido chegou à pizzaria e aguarda confirmação."
            Status.CONFIRMED -> "Tudo certo! A pizzaria aceitou seu pedido."
            Status.PREPARING -> "Sua pizza está sendo preparada com carinho."
            Status.READY -> if (order.mode == Mode.PICKUP) "Seu pedido está pronto. Você já pode retirar na pizzaria." else "Seu pedido está pronto e aguarda a saída para entrega."
            Status.OUT_FOR_DELIVERY -> "O entregador está a caminho do seu endereço."
            Status.RETURNING -> "Houve um problema na entrega. O pedido está retornando à pizzaria."
            Status.RETURNED -> "O retorno à pizzaria foi registrado. Entre em contato com a loja para combinar os próximos passos."
            Status.CANCELLED -> "Este pedido foi cancelado."
            Status.DELIVERED -> "Obrigado por escolher a Bonamassa!"
        }, color = Brand.Muted)
        Text("Última alteração: ${dateTime(order.updatedAt)}", style = MaterialTheme.typography.bodySmall)
        if (ui.syncError == null) Text("Atualização automática enquanto o app está aberto.", color = Brand.Green, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = refresh, enabled = !ui.busy && !ui.refreshing) { Text("Atualizar pedido") }
        Panel {
            Text("Andamento", style = MaterialTheme.typography.titleMedium)
            order.events.forEach { event ->
                val label = when (event.action) { "created" -> "Pedido enviado"; "accept" -> "Pedido aceito"; "prepare" -> "Preparo iniciado"; "ready" -> "Pedido pronto"; "assign" -> "Entregador atribuído"; "collect" -> "Retirado pelo entregador"; "start" -> "Saiu para entrega"; "complete" -> "Entrega concluída"; "pickup-complete" -> "Retirado pelo cliente"; "cancel" -> "Pedido cancelado"; "issue" -> "Ocorrência na entrega"; "return" -> "Devolução registrada"; "record-payment" -> "Pagamento registrado"; else -> "Pedido atualizado" }
                Text("${dateTime(event.createdAt)} · $label")
            }
        }
        Receipt(order.items)
        TotalsPanel(order.totals)
        Panel {
            Text(order.mode.label, style = MaterialTheme.typography.titleMedium)
            order.address?.let { Text(it.summary()) }
            Text(order.payment.label)
            Text(if (order.paymentRecorded) "Pagamento registrado pela pizzaria" else "Pagamento ainda não registrado")
            order.cashTendered?.let { Text("Dinheiro: ${money(it)} · Troco: ${money(order.change)}") }
            if (order.note.isNotBlank()) Text(order.note)
        }
        if (order.status == Status.NEW) OutlinedButton(onClick = { cancelling = true }, enabled = !ui.busy && ui.saved.pending == null, modifier = Modifier.fillMaxWidth()) { Text("Cancelar pedido", color = Brand.Red) }
        else if (order.status.active) Text("Para solicitar alterações ou cancelamento, fale com a pizzaria.", color = Brand.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ConnectedProfile(ui: CustomerUi, login: () -> Unit, logout: () -> Unit) {
    var leaving by rememberSaveable { mutableStateOf(false) }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Sair desta conta?") }, text = { Text("Os dados da conta e a sacola serão removidos deste aparelho. Seus pedidos continuam salvos na pizzaria.") },
        confirmButton = { TextButton(onClick = { leaving = false; logout() }) { Text("Sair") } }, dismissButton = { TextButton(onClick = { leaving = false }) { Text("Continuar") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("Sua conta")
        val user = ui.saved.session?.user
        if (user == null) {
            Text("Entre para acompanhar pedidos e usar seu endereço na próxima compra.", color = Brand.Muted)
            PrimaryAction("Entrar ou criar conta", Modifier.fillMaxWidth(), !ui.busy, onClick = login)
        } else {
            Panel { Text(user.name, style = MaterialTheme.typography.headlineMedium); Text(user.email); Text(user.phone) }
            if (ui.saved.checkout.address.street.isNotBlank()) Panel { Text("Último endereço usado", style = MaterialTheme.typography.titleMedium); Text(ui.saved.checkout.address.summary()); Text("Você pode alterá-lo ao finalizar o próximo pedido.", color = Brand.Muted) }
            OutlinedButton(onClick = { leaving = true }, enabled = !ui.busy && ui.saved.pending == null, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }
        }
    }
}
