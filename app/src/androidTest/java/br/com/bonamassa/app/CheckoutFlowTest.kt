package br.com.bonamassa.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bonamassa.app.connected.*
import br.com.bonamassa.app.ui.BonamassaTheme
import br.com.bonamassa.client.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CheckoutFlowTest {
    @get:Rule val compose = createComposeRule()
    private val user = User("customer", "store", "Cliente", "cliente@test.example", "11999999999", "CUSTOMER")
    private val ui = CustomerUi(saved = SavedState(account = user, session = Session("token", "later", user)), loaded = true,
        catalog = Catalog("store", "Bonamassa", true, 700, emptyList(), emptyList(), "2026-09-14T20:00:00Z"))
    private fun input(label: String, value: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(value) }
    private fun waitText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun cepAutofillsButNumberIsRequiredAndNoComplementClearsTheField() {
        var submitted: Checkout? = null
        val lookup = PostalCodeLookup { PostalAddress(it, "Praça da Sé", "Sé", "São Paulo", "SP") }
        compose.setContent { BonamassaTheme { ConnectedCheckout(ui, lookup) { submitted = it } } }
        input("CEP", "01001000")
        waitText("Endereço preenchido")
        compose.onNodeWithText("Rua").assertTextContains("Praça da Sé")
        compose.onNodeWithText("Conferir valores").assertIsNotEnabled()
        input("Número", "123A")
        input("Complemento (opcional)", "Bloco 2, apto 12")
        compose.onNodeWithText("Não possui complemento").performScrollTo().performClick()
        compose.onNodeWithText("Complemento (opcional)").assertIsNotEnabled().assertTextContains("")
        compose.onNodeWithText("Conferir valores").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("123A", submitted?.address?.number)
            assertEquals("", submitted?.address?.complement)
            assertEquals(true, submitted?.address?.noComplement)
            assertEquals("São Paulo", submitted?.address?.city)
        }
    }
    @Test fun changingCepRejectsThePreviousLookupResponse() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lookup = PostalCodeLookup {
            if (it == "01001000") { started.countDown(); release.await(5, TimeUnit.SECONDS) }
            PostalAddress(it, if (it == "01001000") "Rua antiga" else "Rua nova", "Centro", "São Paulo", "SP")
        }
        compose.setContent { BonamassaTheme { ConnectedCheckout(ui, lookup) {} } }
        input("CEP", "01001000")
        assertTrue(started.await(5, TimeUnit.SECONDS))
        input("CEP", "02002000")
        release.countDown()
        waitText("Rua nova")
        compose.onNodeWithText("Rua").assertTextContains("Rua nova")
        compose.onAllNodesWithText("Rua antiga").assertCountEquals(0)
    }
    @Test fun lookupFailureAllowsManualAddressWithoutMakingAnOrder() {
        var submitted: Checkout? = null
        compose.setContent { BonamassaTheme { ConnectedCheckout(ui, PostalCodeLookup { throw IOException("offline") }) { submitted = it } } }
        input("CEP", "01001000")
        waitText("Não foi possível consultar")
        compose.onNodeWithText("Preencher manualmente").performScrollTo().performClick()
        input("Rua", "Rua manual"); input("Número", "8"); input("Bairro", "Centro"); input("Cidade", "São Paulo"); input("UF", "SP")
        compose.onNodeWithText("Conferir valores").assertIsEnabled()
        compose.runOnIdle { assertNull(submitted) }
    }
}
