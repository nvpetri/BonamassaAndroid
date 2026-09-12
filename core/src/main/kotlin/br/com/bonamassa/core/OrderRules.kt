package br.com.bonamassa.core

import java.math.BigDecimal
import java.math.RoundingMode

object OrderRules {
    const val MAX_QUANTITY = 20
    const val MAX_LINES = 50
    const val DELIVERY_FEE = 700L
    const val COUPON = "BONA10"

    fun unitPrice(item: CartItem): Long {
        require(item.quantity in 1..MAX_QUANTITY) { "Quantidade inválida." }
        require(item.note.length <= 240) { "Observação muito longa." }
        val product = Catalog.require(item.productId)
        require(product.available) { "Produto indisponível." }
        if (product.category != Category.PIZZA) {
            require(item.secondFlavorId == null && item.extras.isEmpty() && item.crust == Crust.NONE) { "Opções inválidas para este produto." }
            require(item.size == PizzaSize.LARGE) { "Tamanho inválido para este produto." }
            return product.price
        }
        val second = item.secondFlavorId?.let { Catalog.require(it) }
        require(second == null || second.category == Category.PIZZA && second.available && second.id != product.id) { "Segundo sabor inválido." }
        // Demonstration policy: charge the most expensive flavor, not the average.
        val base = maxOf(product.price, second?.price ?: product.price)
        return base + item.size.delta + item.crust.price + item.extras.sumOf { it.price }
    }
    fun quote(cart: List<CartItem>, fulfillment: Fulfillment, coupon: String = ""): Quote {
        require(cart.size <= MAX_LINES) { "Limite de itens atingido." }
        val subtotal = cart.sumOf { unitPrice(it) * it.quantity }
        val code = coupon.trim().uppercase()
        val error = when {
            code.isEmpty() -> null
            code != COUPON -> "Cupom não encontrado. Na demo, use BONA10."
            subtotal < 6000 -> "BONA10 exige pelo menos R$ 60,00 em produtos."
            else -> null
        }
        val discount = if (code == COUPON && error == null) minOf(subtotal / 10, 2000) else 0
        return Quote(subtotal, discount, if (cart.isEmpty() || fulfillment == Fulfillment.PICKUP) 0 else DELIVERY_FEE, error)
    }
    fun title(item: CartItem): String {
        val product = Catalog.require(item.productId)
        return item.secondFlavorId?.let { "½ ${product.name} + ½ ${Catalog.require(it).name}" } ?: product.name
    }
    fun details(item: CartItem): String = if (Catalog.require(item.productId).category != Category.PIZZA) {
        Catalog.require(item.productId).description
    } else listOf("${item.size.label} · ${item.size.detail}", "Borda: ${item.crust.label}")
        .plus(item.extras.map { it.label }).joinToString(" · ")

    fun customerErrors(customer: Customer): Map<String, String> = buildMap {
        if (customer.name.trim().length !in 2..80) put("name", "Informe um nome de 2 a 80 caracteres.")
        if (digits(customer.phone).length !in 10..11) put("phone", "Informe telefone com DDD (10 ou 11 dígitos).")
    }
    fun addressErrors(address: Address): Map<String, String> = buildMap {
        if (address.street.trim().length !in 3..120) put("street", "Informe rua ou avenida.")
        if (address.number.trim().length !in 1..20) put("number", "Informe número ou S/N.")
        if (address.district.trim().length !in 2..80) put("district", "Informe o bairro.")
        if (address.city.trim().length !in 2..80) put("city", "Informe a cidade.")
        if (digits(address.cep).length != 8) put("cep", "O CEP deve ter 8 dígitos.")
        if (address.complement.length > 120) put("complement", "Use até 120 caracteres.")
    }
    fun parseMoney(input: String): Long? = try {
        val normalized = input.trim().replace(',', '.')
        if (!Regex("[0-9]{1,7}(\\.[0-9]{1,2})?").matches(normalized)) null
        else BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    } catch (_: ArithmeticException) { null }

    fun checkoutErrors(cart: List<CartItem>, checkout: Checkout, quote: Quote): Map<String, String> = buildMap {
        if (cart.isEmpty()) put("cart", "Adicione pelo menos um item.")
        putAll(customerErrors(checkout.customer))
        if (checkout.fulfillment == Fulfillment.DELIVERY) putAll(addressErrors(checkout.address))
        if (checkout.payment == Payment.CASH && checkout.changeFor.isNotBlank()) {
            val value = parseMoney(checkout.changeFor)
            if (value == null || value < quote.total) put("changeFor", "O valor para troco deve ser igual ou maior que o total.")
        }
    }
    fun nextStatus(status: OrderStatus, fulfillment: Fulfillment): OrderStatus? = when (status) {
        OrderStatus.RECEIVED -> OrderStatus.PREPARING
        OrderStatus.PREPARING -> OrderStatus.READY
        OrderStatus.READY -> if (fulfillment == Fulfillment.PICKUP) OrderStatus.DELIVERED else OrderStatus.ON_THE_WAY
        OrderStatus.ON_THE_WAY -> OrderStatus.DELIVERED
        else -> null
    }
    fun steps(fulfillment: Fulfillment) = listOf(OrderStatus.RECEIVED, OrderStatus.PREPARING, OrderStatus.READY) +
        (if (fulfillment == Fulfillment.DELIVERY) listOf(OrderStatus.ON_THE_WAY) else emptyList()) + OrderStatus.DELIVERED
    fun statusLabel(status: OrderStatus, fulfillment: Fulfillment) = when {
        status == OrderStatus.DELIVERED && fulfillment == Fulfillment.PICKUP -> "Retirado"
        status == OrderStatus.READY && fulfillment == Fulfillment.PICKUP -> "Pronto para retirar"
        else -> status.label
    }
    fun place(state: AppState, id: String, now: Long): AppState {
        val quote = quote(state.cart, state.checkout.fulfillment, state.coupon)
        val errors = checkoutErrors(state.cart, state.checkout, quote)
        require(errors.isEmpty()) { errors.values.first() }
        val cleanCheckout = state.checkout.copy(customer = state.checkout.customer.copy(
            name = state.checkout.customer.name.trim(), phone = digits(state.checkout.customer.phone)
        ))
        val order = Order(id, state.nextOrderNumber, now,
            state.cart.map { OrderLine(title(it), details(it), it.quantity, unitPrice(it), it.note.trim()) },
            cleanCheckout, quote.copy(couponError = null), sourceItems = state.cart.toList())
        return state.copy(cart = emptyList(), coupon = "", orders = (listOf(order) + state.orders).take(100), nextOrderNumber = state.nextOrderNumber + 1)
    }
}
