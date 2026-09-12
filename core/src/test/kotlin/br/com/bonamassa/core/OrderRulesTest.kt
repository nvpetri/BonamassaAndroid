package br.com.bonamassa.core

import org.junit.Assert.*
import org.junit.Test

class OrderRulesTest {
    private fun pizza(id: String = "calabresa", quantity: Int = 1) = CartItem("item-1", id, quantity = quantity)
    private val recipient = Customer("Pessoa Teste", "11999990000")
    private val address = Address("Rua Exemplo", "123", "Centro", "Cidade Teste", "01001000")

    @Test fun halfPizzaUsesMostExpensiveFlavor() {
        val item = pizza().copy(secondFlavorId = "bonamassa")
        assertEquals(6290L, OrderRules.unitPrice(item))
        assertEquals(6290L, OrderRules.unitPrice(pizza("bonamassa").copy(secondFlavorId = "calabresa")))
    }
    @Test fun sizeCrustAndExtrasArePerWholePizza() {
        val item = pizza().copy(secondFlavorId = "bonamassa", size = PizzaSize.FAMILY, crust = Crust.CREAM_CHEESE, extras = setOf(Extra.BACON, Extra.CHEESE), quantity = 2)
        assertEquals(9590L, OrderRules.unitPrice(item))
        assertEquals(19180L, OrderRules.quote(listOf(item), Fulfillment.PICKUP).total)
    }
    @Test fun mediumPizzaHasExplicitDiscount() { assertEquals(3990L, OrderRules.unitPrice(pizza().copy(size = PizzaSize.MEDIUM))) }
    @Test fun drinkHasExactPrice() { assertEquals(1400L, OrderRules.unitPrice(pizza("cola"))) }
    @Test(expected = IllegalArgumentException::class) fun cannotAddCrustToDrink() { OrderRules.unitPrice(pizza("cola").copy(crust = Crust.CHEDDAR)) }
    @Test(expected = IllegalArgumentException::class) fun cannotMixDrinkAndPizza() { OrderRules.unitPrice(pizza().copy(secondFlavorId = "cola")) }
    @Test(expected = IllegalArgumentException::class) fun cannotDuplicateFlavor() { OrderRules.unitPrice(pizza().copy(secondFlavorId = "calabresa")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownProduct() { OrderRules.unitPrice(pizza("unknown")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroQuantity() { OrderRules.unitPrice(pizza(quantity = 0)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsExcessiveQuantity() { OrderRules.unitPrice(pizza(quantity = 21)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedNote() { OrderRules.unitPrice(pizza().copy(note = "a".repeat(241))) }
    @Test fun emptyCartHasNoDeliveryCharge() { assertEquals(0L, OrderRules.quote(emptyList(), Fulfillment.DELIVERY).total) }
    @Test fun pickupRemovesDeliveryCharge() {
        assertEquals(4990L, OrderRules.quote(listOf(pizza()), Fulfillment.PICKUP).total)
        assertEquals(5690L, OrderRules.quote(listOf(pizza()), Fulfillment.DELIVERY).total)
    }
    @Test fun validCouponOnlyDiscountsProducts() {
        val quote = OrderRules.quote(listOf(pizza("bonamassa")), Fulfillment.DELIVERY, " bona10 ")
        assertEquals(629L, quote.discount); assertEquals(700L, quote.delivery); assertEquals(6361L, quote.total)
    }
    @Test fun couponHasCap() { assertEquals(2000L, OrderRules.quote(listOf(pizza(quantity = 10)), Fulfillment.PICKUP, "BONA10").discount) }
    @Test fun couponRequiresMinimum() {
        val quote = OrderRules.quote(listOf(pizza()), Fulfillment.PICKUP, "BONA10")
        assertEquals(0L, quote.discount); assertNotNull(quote.couponError)
    }
    @Test fun unknownCouponDoesNotDiscount() {
        val quote = OrderRules.quote(listOf(pizza(quantity = 2)), Fulfillment.PICKUP, "FREE")
        assertEquals(0L, quote.discount); assertNotNull(quote.couponError)
    }
    @Test fun cashIsParsedWithoutFloatingPointRounding() {
        assertEquals(10010L, OrderRules.parseMoney("100,10")); assertEquals(10010L, OrderRules.parseMoney("100.10"))
        assertNull(OrderRules.parseMoney("1.000,00")); assertNull(OrderRules.parseMoney("-1")); assertNull(OrderRules.parseMoney("10,999"))
    }
    @Test fun deliveryNeedsFullAddress() {
        val errors = OrderRules.checkoutErrors(listOf(pizza()), Checkout(customer = recipient), OrderRules.quote(listOf(pizza()), Fulfillment.DELIVERY))
        assertTrue(errors.keys.containsAll(listOf("street", "number", "district", "city", "cep")))
    }
    @Test fun pickupNeedsNoAddress() {
        val checkout = Checkout(customer = recipient, fulfillment = Fulfillment.PICKUP)
        assertTrue(OrderRules.checkoutErrors(listOf(pizza()), checkout, OrderRules.quote(listOf(pizza()), Fulfillment.PICKUP)).isEmpty())
    }
    @Test fun phoneAcceptsTenOrElevenDigits() {
        assertTrue(OrderRules.customerErrors(recipient).isEmpty())
        assertTrue(OrderRules.customerErrors(recipient.copy(phone = "(11) 3333-0000")).isEmpty())
        assertTrue(OrderRules.customerErrors(recipient.copy(phone = "123")).containsKey("phone"))
    }
    @Test fun cashChangeCannotBeBelowTotal() {
        val checkout = Checkout(recipient, address, payment = Payment.CASH, changeFor = "20")
        assertTrue(OrderRules.checkoutErrors(listOf(pizza()), checkout, OrderRules.quote(listOf(pizza()), Fulfillment.DELIVERY)).containsKey("changeFor"))
    }
    @Test fun cashWithoutChangeIsAllowed() {
        val checkout = Checkout(recipient, address, payment = Payment.CASH)
        assertTrue(OrderRules.checkoutErrors(listOf(pizza()), checkout, OrderRules.quote(listOf(pizza()), Fulfillment.DELIVERY)).isEmpty())
    }
    @Test fun pickupSkipsDriverStep() { assertEquals(OrderStatus.DELIVERED, OrderRules.nextStatus(OrderStatus.READY, Fulfillment.PICKUP)) }
    @Test fun deliveryHasDriverStep() { assertEquals(OrderStatus.ON_THE_WAY, OrderRules.nextStatus(OrderStatus.READY, Fulfillment.DELIVERY)) }
    @Test fun terminalStatusesCannotAdvance() {
        assertNull(OrderRules.nextStatus(OrderStatus.DELIVERED, Fulfillment.DELIVERY)); assertNull(OrderRules.nextStatus(OrderStatus.CANCELLED, Fulfillment.PICKUP))
    }
    @Test fun placingOrderCreatesSnapshotAndClearsCart() {
        val state = AppState(cart = listOf(pizza().copy(note = "Sem cebola")), checkout = Checkout(recipient, address))
        val placed = OrderRules.place(state, "order-1", 123L)
        assertTrue(placed.cart.isEmpty()); assertEquals(1, placed.orders.size); assertEquals(1002, placed.nextOrderNumber)
        assertEquals("Sem cebola", placed.orders.first().items.first().note)
        assertEquals(4990L, placed.orders.first().items.first().unitPrice)
    }
    @Test(expected = IllegalArgumentException::class) fun secondSubmissionOfEmptyCartCannotCreateDuplicate() {
        val state = AppState(cart = listOf(pizza()), checkout = Checkout(recipient, address))
        OrderRules.place(OrderRules.place(state, "first", 1), "second", 2)
    }
    @Test fun completedOrderKeepsDeliveryChoice() {
        val state = AppState(cart = listOf(pizza()), checkout = Checkout(recipient, fulfillment = Fulfillment.PICKUP))
        assertEquals(Fulfillment.PICKUP, OrderRules.place(state, "first", 1).orders.first().checkout.fulfillment)
    }
    @Test fun countLimitRejectsOversizedCart() {
        assertThrows(IllegalArgumentException::class.java) { OrderRules.quote((1..51).map { pizza().copy(id = it.toString()) }, Fulfillment.PICKUP) }
    }
}
