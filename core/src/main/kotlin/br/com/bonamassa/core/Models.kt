package br.com.bonamassa.core

import java.text.NumberFormat
import java.util.Locale

enum class Category(val label: String) { PIZZA("Pizzas"), DRINK("Bebidas"), DESSERT("Doces") }
enum class PizzaSize(val label: String, val detail: String, val delta: Long) {
    MEDIUM("Média", "6 fatias", -1000), LARGE("Grande", "8 fatias", 0), FAMILY("Família", "12 fatias", 1400)
}
enum class Crust(val label: String, val price: Long) {
    NONE("Tradicional", 0), CREAM_CHEESE("Requeijão cremoso", 800), CHEDDAR("Cheddar", 800)
}
enum class Extra(val label: String, val price: Long) {
    BACON("Bacon crocante", 600), CHEESE("Mussarela extra", 500), OLIVES("Azeitonas", 300)
}
enum class Fulfillment(val label: String) { DELIVERY("Entrega"), PICKUP("Retirada") }
enum class Payment(val label: String, val detail: String) {
    PIX("Pix", "Somente simulado; não gera cobrança"),
    CARD("Cartão", "Maquininha no recebimento"), CASH("Dinheiro", "Informe se precisa de troco")
}
enum class OrderStatus(val label: String) {
    RECEIVED("Pedido recebido"), PREPARING("Em preparo"), READY("Pronto"),
    ON_THE_WAY("Saiu para entrega"), DELIVERED("Entregue"), CANCELLED("Cancelado")
}
data class Product(
    val id: String, val name: String, val description: String,
    val price: Long, val category: Category, val badge: String = "", val art: Int = 0,
    val available: Boolean = true
)
data class CartItem(
    val id: String, val productId: String, val secondFlavorId: String? = null,
    val size: PizzaSize = PizzaSize.LARGE, val crust: Crust = Crust.NONE,
    val extras: Set<Extra> = emptySet(), val quantity: Int = 1, val note: String = ""
)
data class Customer(val name: String = "", val phone: String = "")
data class Address(
    val street: String = "", val number: String = "", val district: String = "",
    val city: String = "", val cep: String = "", val complement: String = ""
) {
    fun summary() = listOf("$street, $number", district, city, complement).filter(String::isNotBlank).joinToString(" · ")
}
data class Checkout(
    val customer: Customer = Customer(), val address: Address = Address(),
    val fulfillment: Fulfillment = Fulfillment.DELIVERY, val payment: Payment = Payment.PIX,
    val changeFor: String = ""
)
data class Quote(val subtotal: Long, val discount: Long, val delivery: Long, val couponError: String? = null) {
    val total: Long get() = subtotal - discount + delivery
}
// Snapshot names/prices so historical receipts never change when the menu changes.
data class OrderLine(val title: String, val details: String, val quantity: Int, val unitPrice: Long, val note: String)
data class Order(
    val id: String, val number: Int, val createdAt: Long, val items: List<OrderLine>,
    val checkout: Checkout, val quote: Quote, val status: OrderStatus = OrderStatus.RECEIVED,
    val sourceItems: List<CartItem> = emptyList()
)
data class AppState(
    val cart: List<CartItem> = emptyList(), val favorites: Set<String> = emptySet(),
    val checkout: Checkout = Checkout(), val coupon: String = "", val orders: List<Order> = emptyList(),
    val nextOrderNumber: Int = 1001
)
fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(cents / 100.0)
fun digits(value: String) = value.filter(Char::isDigit)
