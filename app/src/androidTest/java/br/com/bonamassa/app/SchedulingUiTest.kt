package br.com.bonamassa.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bonamassa.app.connected.*
import br.com.bonamassa.app.ui.BonamassaTheme
import br.com.bonamassa.client.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SchedulingUiTest {
    @get:Rule val compose = createComposeRule()
    private val user = User("customer", "store", "Cliente", "cliente@test.example", "11999999999", "CUSTOMER")
    private val session = Session("token", "2027-01-01T00:00:00Z", user)
    private val at = "2026-09-14T20:00:00Z"
    private val totals = Totals(5500, 0, 0, 5500, null)
    private val pizza = DraftLine(Kind.PIZZA, flavorIds = listOf("calabresa"))
    private val catalog = Catalog("store", "Bonamassa", false, 700,
        listOf(Product("calabresa", "Calabresa", "", Kind.PIZZA, "TRADITIONAL", Size.entries.associateWith { 5500L }, true, null, emptyList(), null)),
        emptyList(), at, reservationsAvailable = true, nextOpening = at)

    @Test fun closedStoreAllowsContinuingAnExplicitReservation() {
        var continued = false
        val ui = CustomerUi(saved = SavedState(account = user, session = session, cart = listOf(pizza)), loaded = true, catalog = catalog)
        compose.setContent { BonamassaTheme { ConnectedCart(ui, { _, _ -> }, {}, {}, {}) { continued = true } } }
        compose.onNodeWithText("Continuar reserva").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(continued) }
    }
    @Test fun reviewRequiresTheReservationConfirmationButton() {
        var confirmed = false
        val quote = Quote("quote", "2026-09-14T19:00:00Z", emptyList(), totals, at)
        val ui = CustomerUi(saved = SavedState(account = user, session = session), loaded = true,
            catalog = catalog, review = Review(quote, Checkout(mode = Mode.PICKUP)))
        compose.setContent { BonamassaTheme { ConnectedReview(ui, {}, { confirmed = true }) } }
        compose.onNodeWithText("PEDIDO AGENDADO").assertExists()
        compose.onNodeWithText("Confirmar e enviar pedido").assertDoesNotExist()
        compose.onNodeWithText("Confirmar agendamento").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
