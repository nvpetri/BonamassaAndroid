package br.com.bonamassa.app

import br.com.bonamassa.app.data.StateCodec
import br.com.bonamassa.core.*
import org.junit.Assert.*
import org.junit.Test

class StateCodecTest {
    @Test fun emptyStateRoundTrip() { assertEquals(AppState(), StateCodec.decode(StateCodec.encode(AppState()))) }
    @Test fun customizedCartRoundTrip() {
        val state = AppState(cart = listOf(CartItem("one", "bonamassa", "frango", PizzaSize.FAMILY, Crust.CHEDDAR, setOf(Extra.CHEESE), 3, "Sem cebola e sem pimenta")),
            favorites = setOf("bonamassa"), checkout = Checkout(Customer("Teste ç", "11999990000"), Address("Rua teste", "1", "Bairro", "São Paulo", "01001000", "Apto 1")), coupon = "BONA10")
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }
    @Test fun ordersRetainFullSnapshot() {
        val state = OrderRules.place(AppState(cart = listOf(CartItem("1", "cola")), checkout = Checkout(Customer("Teste", "11999990000"), fulfillment = Fulfillment.PICKUP)), "demo", 1L)
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }
    @Test fun malformedDataFailsWithoutSilentReset() {
        assertThrows(Exception::class.java) { StateCodec.decode("invalid") }
    }
    @Test fun unknownSchemaFails() {
        assertThrows(IllegalArgumentException::class.java) { StateCodec.decode(StateCodec.encode(AppState()).replace("\"schema\":2", "\"schema\":900")) }
    }
    @Test fun badCartQuantityFails() {
        val state = AppState(cart = listOf(CartItem("a", "bonamassa", quantity = 0)))
        assertThrows(IllegalArgumentException::class.java) { StateCodec.decode(StateCodec.encode(state)) }
    }
}
