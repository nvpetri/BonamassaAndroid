package br.com.bonamassa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.bonamassa.app.R
import br.com.bonamassa.core.*

@Composable
fun DemoNotice() {
    Box(Modifier.fillMaxWidth().background(Brand.Gold.copy(alpha = .10f)).padding(horizontal = 16.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text("DEMONSTRAÇÃO · NÃO ENVIA PEDIDOS", style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
    }
}
@Composable
fun BrandHeader(title: String, onBack: (() -> Unit)?, count: Int, onCart: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        else Image(painterResource(R.drawable.bonamassa_logo), "Bonamassa Pizzaria", Modifier.size(56.dp).clip(CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(if (onBack == null) "BONAMASSA" else title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (onBack == null) "PIZZARIA · FEITA PARA COMPARTILHAR" else "BONAMASSA PIZZARIA", style = MaterialTheme.typography.labelSmall, color = Brand.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BadgedBox(badge = { if (count > 0) Badge(containerColor = Brand.Red, contentColor = Brand.Background) { Text(count.toString()) } }) {
            IconButton(onClick = onCart) { Icon(Icons.Default.ShoppingBag, "Abrir sacola, $count itens") }
        }
    }
}
@Composable
fun PrimaryAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 54.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Button, contentColor = Brand.Cream), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
@Composable
fun SectionHeading(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Brand.Red) }
    }
}
@Composable
fun Tag(label: String, color: Color = Brand.Gold) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = .12f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
fun PriceLine(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (highlight) Brand.Cream else Brand.Muted, style = if (highlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(value, color = if (highlight) Brand.Gold else Brand.Cream, style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun QuotePanel(quote: Quote) {
    Panel {
        PriceLine("Produtos", money(quote.subtotal))
        if (quote.discount > 0) PriceLine("Desconto BONA10", "− ${money(quote.discount)}")
        PriceLine("Entrega (valor demonstrativo)", if (quote.delivery == 0L) "Grátis" else money(quote.delivery))
        HorizontalDivider(color = Brand.Border)
        PriceLine("Total", money(quote.total), highlight = true)
    }
}
@Composable
fun EmptyState(title: String, description: String, icon: ImageVector, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(84.dp).background(Brand.Raised, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(34.dp), tint = Brand.Gold) }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, color = Brand.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) PrimaryAction(action, onClick = onAction)
    }
}
@Composable
fun ProductTile(product: Product, favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit, compact: Boolean = false) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(22.dp), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border)) {
        if (compact) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodArt(product, Modifier.size(90.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text(product.description, style = MaterialTheme.typography.bodySmall, color = Brand.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${if (product.category == Category.PIZZA) "Grande · " else ""}${money(product.price)}", style = MaterialTheme.typography.labelLarge, color = Brand.Gold)
                }
                Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = Brand.Muted)
            }
        } else {
            Column {
                Box(Modifier.fillMaxWidth().background(Brand.Raised)) {
                    FoodArt(product, Modifier.fillMaxWidth().height(160.dp))
                    IconButton(onClick = onFavorite, Modifier.align(Alignment.TopEnd)) {
                        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "Remover ${product.name} dos favoritos" else "Favoritar ${product.name}", tint = if (favorite) Brand.Red else Brand.Cream)
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(product.badge)
                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                    Text("Grande · 8 fatias", style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(money(product.price), style = MaterialTheme.typography.titleMedium, color = Brand.Gold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.AddCircle, null, tint = Brand.Red)
                    }
                }
            }
        }
    }
}
@Composable
fun QuantityControl(quantity: Int, onChange: (Int) -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Brand.Raised, RoundedCornerShape(14.dp))) {
        IconButton(onClick = { onChange(quantity - 1) }, enabled = enabled && quantity > 1) { Icon(Icons.Default.Remove, "Diminuir quantidade") }
        Text(quantity.toString(), Modifier.widthIn(min = 24.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange(quantity + 1) }, enabled = enabled && quantity < OrderRules.MAX_QUANTITY) { Icon(Icons.Default.Add, "Aumentar quantidade") }
    }
}
@Composable
fun OptionRow(label: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (selected) Brand.Gold.copy(alpha = .08f) else Brand.Surface,
        border = BorderStroke(1.dp, if (selected) Brand.Gold else Brand.Border)) {
        Row(Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(4.dp)) { Text(label, style = MaterialTheme.typography.titleMedium); Text(detail, style = MaterialTheme.typography.bodySmall, color = Brand.Muted) }
            RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = Brand.Gold))
        }
    }
}
@Composable
fun Input(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null, type: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, isError = error != null, singleLine = singleLine,
        shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = type),
        supportingText = if (error != null) ({ Text(error) }) else null)
}
@Composable
fun BottomAction(label: String, value: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = Brand.Background, shadowElevation = 10.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value != null) PriceLine("Total", value, highlight = true)
            PrimaryAction(label, Modifier.fillMaxWidth(), enabled = enabled, onClick = onClick)
        }
    }
}
