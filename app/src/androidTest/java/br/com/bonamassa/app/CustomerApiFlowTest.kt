package br.com.bonamassa.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import br.com.bonamassa.app.connected.SecureStore
import br.com.bonamassa.client.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Opt-in only. CI supplies an isolated API/PostgreSQL store, never a developer's live store. */
class CustomerApiFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun waitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun input(label: String, text: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(text) }

    @Test fun customerOrderUsesApiPricesAndReceivesKitchenAndDriverUpdates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bonamassaIntegration") == "true")
        val endpoint = Endpoint.parse("http://10.0.2.2:3001", "bonamassa", true)
        val api = BonamassaApi(endpoint)
        val manager = Decode.session(api.request("POST", "/v1/sessions", body = objectOf("storeSlug" to "bonamassa", "email" to "manager@teste.example", "password" to "Manager-ci-only-password-2026")))
        val tag = UUID.randomUUID().toString().take(8)
        val promo = api.request("POST", "/v1/staff/promotions", manager.accessToken, objectOf("name" to "Pizza Android $tag", "enabled" to true, "kind" to "PERCENTAGE", "value" to 10,
            "startsAt" to Instant.now().minusSeconds(60).toString(), "endsAt" to Instant.now().plusSeconds(3600).toString(), "pizzaLimit" to 20), UUID.randomUUID().toString())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secure = SecureStore(context)
        secure.write(SavedState(origin = endpoint.origin, slug = endpoint.storeSlug))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("Explorar cardápio")
            compose.onNodeWithText("Conta").performClick()
            click("Entrar ou criar conta")
            click("Criar conta")
            input("Seu nome", "Cliente Android $tag")
            input("Telefone com DDD", "11912345678")
            input("E-mail", "android-$tag@teste.example")
            input("Senha", "Cliente-ci-password-2026")
            click("Criar minha conta")
            waitText("Cliente Android $tag")
            compose.onNodeWithText("Cardápio").performClick()
            waitText("Calabresa")
            click("Calabresa")
            waitText("Qual tamanho?")
            click("Meio a meio")
            click("Frango com requeijão")
            click("Borda de requeijão")
            compose.onNodeWithText("Adicionar à sacola").performClick()
            waitText("Continuar pedido")
            compose.onNodeWithText("Continuar pedido").performClick()
            waitText("Onde vamos entregar?")
            input("Rua", "Rua do Teste")
            input("Número", "10")
            input("Bairro", "Centro")
            input("Cidade", "São Paulo")
            input("UF", "SP")
            input("CEP", "01001000")
            click("Dinheiro")
            input("Troco para quanto? Ex.: 100,00", "100,00")
            click("Pizza Android $tag")
            compose.onNodeWithText("Conferir valores").performClick()
            waitText("VALORES CONFIRMADOS PELA PIZZARIA")
            val saved = requireNotNull(secure.read())
            val session = requireNotNull(saved.session)
            assertEquals("CUSTOMER", session.user.role)
            assertEquals(listOf("calabresa", "frango"), saved.cart.single().flavorIds)
            assertEquals("CREAM", saved.cart.single().crust)
            val expected = 7000L - 600L + api.catalog().deliveryFee
            // Recreate during review: navigation restores, quote is retained in the ViewModel.
            scenario.recreate()
            waitText("Confirmar e enviar pedido")
            compose.onNodeWithText("Confirmar e enviar pedido").performClick()
            waitText("Aguardando confirmação")
            val created = api.orders(session.accessToken).items.single()
            assertEquals(expected, created.totals.total)
            assertEquals(600L, created.totals.discount)
            assertEquals(1, created.totals.discountedPizzas)
            assertEquals(10000L - expected, created.change)
            assertEquals("Rua do Teste", created.address?.street)
            assertTrue(requireNotNull(secure.read()).cart.isEmpty())
            assertNull(secure.read()?.pending)

            fun command(order: Order, action: String, extra: JSONObject = JSONObject(), token: String = manager.accessToken, prefix: String = "/v1/staff/orders"): Order {
                extra.put("expectedVersion", order.version)
                return Decode.order(api.request("POST", "$prefix/${order.id}/$action", token, extra, UUID.randomUUID().toString()))
            }
            var current = command(created, "accept")
            waitText("Pedido aceito")
            current = command(current, "prepare")
            waitText("Em preparo")
            current = command(current, "ready")
            waitText("Pronto")
            val driverRaw = api.request("POST", "/v1/staff/users", manager.accessToken, objectOf("email" to "driver-$tag@teste.example", "password" to "Driver-ci-password-2026", "name" to "Entregador CI", "phone" to "11922223333", "role" to "DRIVER"), UUID.randomUUID().toString())
            val driver = Decode.session(api.request("POST", "/v1/sessions", body = objectOf("storeSlug" to "bonamassa", "email" to "driver-$tag@teste.example", "password" to "Driver-ci-password-2026")))
            api.request("PATCH", "/v1/driver/availability", driver.accessToken, objectOf("expectedVersion" to driverRaw.getInt("version"), "available" to true), UUID.randomUUID().toString())
            current = command(current, "assign", objectOf("driverId" to driver.user.id))
            current = command(current, "collect", token = driver.accessToken, prefix = "/v1/driver/deliveries")
            current = command(current, "start", token = driver.accessToken, prefix = "/v1/driver/deliveries")
            waitText("Saiu para entrega")
            current = command(current, "complete", objectOf("recipient" to "Cliente Android", "paymentCollected" to true), driver.accessToken, "/v1/driver/deliveries")
            assertEquals(Status.DELIVERED, current.status)
            waitText("Obrigado por escolher a Bonamassa!")

            // Same production transport against real PostgreSQL: fixed combo and retry recovery.
            val comboQuote = api.quote(session.accessToken, listOf(DraftLine(Kind.COMBO, "combo-dupla")), Checkout(mode = Mode.PICKUP))
            assertEquals(6500L, comboQuote.totals.total)
            assertEquals(2, comboQuote.items.single().components.size)
            val pending = Pending.order(comboQuote, endpoint, session.user)
            val first = api.send(session.accessToken, pending)
            scenario.close()
            secure.write(requireNotNull(secure.read()).copy(pending = pending, cart = listOf(DraftLine(Kind.COMBO, "combo-dupla"))))
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText("Envio aguardando confirmação")
                compose.onNodeWithText("Verificar envio").performClick()
                waitText("Aguardando confirmação")
                compose.waitUntil(10_000) { secure.read()?.pending == null }
                assertEquals(first.id, secure.read()?.lastOrderId)
                assertTrue(requireNotNull(secure.read()).cart.isEmpty())
            }
            assertEquals(2, api.orders(session.accessToken).items.size)
            val cancel = Pending.cancel(first, "Teste de cancelamento", endpoint, session.user)
            assertEquals(Status.CANCELLED, api.send(session.accessToken, cancel).status)
            assertEquals(Status.CANCELLED, api.send(session.accessToken, cancel).status)
            val other = api.signIn("other-$tag@teste.example", "Other-ci-password-2026", "Outro cliente", "11988887777")
            try { api.order(other.accessToken, created.id); fail("Another customer's order must be private") } catch (e: ApiFailure) { assertEquals(404, e.status) }
            api.logout(other.accessToken)
            api.logout(session.accessToken)
            try { api.orders(session.accessToken); fail("Revoked session must fail") } catch (e: ApiFailure) { assertEquals(401, e.status) }
            api.logout(driver.accessToken)
        }
        // Remove the temporary account's local token; backend test data lives only in CI PostgreSQL.
        secure.write(SavedState(origin = endpoint.origin))
        api.logout(manager.accessToken)
        assertNotNull(promo.getString("id"))
    }
}
