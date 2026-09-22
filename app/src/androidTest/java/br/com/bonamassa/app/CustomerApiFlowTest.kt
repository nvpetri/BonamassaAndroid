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
import java.io.FileInputStream

/** Opt-in only. CI supplies an isolated API/PostgreSQL store, never a developer's live store. */
class CustomerApiFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun waitText(text: String) = compose.waitUntil(60_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun input(label: String, text: String) { compose.onNodeWithText(label).performScrollTo().performTextReplacement(text) }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Shell-owned temporary output survives AGP uninstalling the tested app.
        for (command in listOf("mkdir -p /data/local/tmp/bonamassa-screenshots", "screencap -p /data/local/tmp/bonamassa-screenshots/$name")) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        }
    }

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
        // Postal autofill is exercised with a controlled provider in CheckoutFlowTest.
        // This full API flow starts with a saved address and never depends on public ViaCEP.
        secure.write(SavedState(origin = endpoint.origin, slug = endpoint.storeSlug,
            checkout = Checkout(address = Address("Rua do Teste", "10", "Centro", "São Paulo", "SP", "01001000", complement = "Apto 12"))))
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
            waitText("Sair da conta")
            compose.onNodeWithText("Cardápio").performClick()
            waitText("Calabresa")
            click("Calabresa")
            waitText("Qual tamanho?")
            click("Meio a meio")
            click("Frango com requeijão")
            click("Borda de requeijão")
            compose.onNodeWithText("Adicionar à sacola").performClick()
            waitText("Produto adicionado à sacola")
            val signedIn = requireNotNull(secure.read()?.session)
            assertTrue(api.orders(signedIn.accessToken).items.isEmpty())
            compose.onNode(hasText("Continuar comprando") and hasAnyAncestor(isDialog())).performClick()
            waitText("Buscar sabores e ingredientes")
            input("Buscar sabores e ingredientes", "Refrigerante")
            compose.onNodeWithTag("customer_menu").performScrollToNode(hasText("Refrigerante 2 L"))
            click("Refrigerante 2 L")
            compose.onNodeWithText("Adicionar à sacola").performClick()
            waitText("Produto adicionado à sacola")
            assertEquals(2, requireNotNull(secure.read()).cart.size)
            assertTrue(api.orders(signedIn.accessToken).items.isEmpty())
            compose.onNodeWithText("Ir para checkout").performClick()
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
            screenshot("cliente-revisao.png")
            val saved = requireNotNull(secure.read())
            val session = requireNotNull(saved.session)
            assertEquals("CUSTOMER", session.user.role)
            assertEquals(listOf("calabresa", "frango"), saved.cart.first().flavorIds)
            assertEquals("CREAM", saved.cart.first().crust)
            val expected = 7000L + 1400L - 600L + api.catalog().deliveryFee
            assertTrue(api.orders(session.accessToken).items.isEmpty())
            // Recreate during review: navigation restores, quote is retained in the ViewModel.
            scenario.recreate()
            waitText("Confirmar e enviar pedido")
            compose.onNodeWithText("Confirmar e enviar pedido").performClick()
            waitText("Deseja enviar este pedido?")
            assertTrue(api.orders(session.accessToken).items.isEmpty())
            compose.onNodeWithText("Sim, enviar pedido").performClick()
            waitText("Aguardando confirmação")
            val created = api.orders(session.accessToken).items.single()
            assertEquals(expected, created.totals.total)
            assertEquals(600L, created.totals.discount)
            assertEquals(1, created.totals.discountedPizzas)
            assertEquals(10000L - expected, created.change)
            assertEquals("Rua do Teste", created.address?.street)
            assertEquals("Apto 12", created.address?.complement)
            assertEquals(2, created.items.size)
            assertEquals(api.catalog().deliveryFee, created.totals.fee)
            assertTrue(requireNotNull(secure.read()).cart.isEmpty())
            assertNull(secure.read()?.pending)

            fun command(order: Order, action: String, extra: JSONObject = JSONObject(), token: String = manager.accessToken, prefix: String = "/v1/staff/orders"): Order {
                extra.put("expectedVersion", order.version)
                return Decode.order(api.request("POST", "$prefix/${order.id}/$action", token, extra, UUID.randomUUID().toString()))
            }
            var current = command(created, "accept")
            assertEquals(Status.NEW, current.status)
            current = command(current, "prepare")
            assertEquals(Status.PREPARING, current.status)
            current = command(current, "ready")
            assertEquals(Status.READY, current.status)
            val driverRaw = api.request("POST", "/v1/staff/users", manager.accessToken, objectOf("email" to "driver-$tag@teste.example", "password" to "Driver-ci-password-2026", "name" to "Entregador CI", "phone" to "11922223333", "role" to "DRIVER"), UUID.randomUUID().toString())
            api.request("POST", "/v1/auth/email-verification/request", body = objectOf("storeSlug" to "bonamassa", "email" to "driver-$tag@teste.example"))
            val driver = Decode.session(api.request("POST", "/v1/auth/email-verification/confirm", body = objectOf("storeSlug" to "bonamassa", "email" to "driver-$tag@teste.example", "code" to "123456")))
            api.request("PATCH", "/v1/driver/availability", driver.accessToken, objectOf("expectedVersion" to api.request("GET", "/v1/me", driver.accessToken).getInt("version"), "available" to true), UUID.randomUUID().toString())
            current = command(current, "assign", objectOf("driverId" to driver.user.id))
            current = command(current, "collect", token = driver.accessToken, prefix = "/v1/driver/deliveries")
            current = command(current, "start", token = driver.accessToken, prefix = "/v1/driver/deliveries")
            assertEquals(Status.OUT_FOR_DELIVERY, api.order(session.accessToken, created.id).status)
            current = command(current, "complete", objectOf("recipient" to "Cliente Android", "paymentCollected" to true), driver.accessToken, "/v1/driver/deliveries")
            assertEquals(Status.DELIVERED, current.status)
            assertEquals(Status.DELIVERED, api.order(session.accessToken, created.id).status)
            screenshot("cliente-entregue.png")

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
    @Test fun closedStoreReservationPersistsAndEarlyOpeningReleasesItOnce() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bonamassaIntegration") == "true")
        val endpoint = Endpoint.parse("http://10.0.2.2:3001", "bonamassa", true)
        val api = BonamassaApi(endpoint)
        val manager = Decode.session(api.request("POST", "/v1/sessions", body = objectOf("storeSlug" to "bonamassa", "email" to "manager@teste.example", "password" to "Manager-ci-only-password-2026")))
        fun settings(extra: JSONObject): JSONObject {
            val store = api.request("GET", "/v1/staff/catalog", manager.accessToken).getJSONObject("store")
            val body = objectOf("expectedVersion" to store.getInt("version"), "name" to store.getString("name"),
                "deliveryFee" to store.getInt("deliveryFee"), "driverFee" to store.getInt("driverFee"))
            extra.keys().forEach { body.put(it, extra.get(it)) }
            return api.request("PATCH", "/v1/staff/store", manager.accessToken, body, UUID.randomUUID().toString())
        }
        fun clock(offset: Long) = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .withZone(java.time.ZoneId.of("America/Sao_Paulo")).format(Instant.now().plusSeconds(offset * 60))
        val customer = api.signIn("reserve-${UUID.randomUUID()}@teste.example", "Reserva-ci-password-2026", "Cliente reserva", "11912345678")
        var created: Order? = null
        try {
            settings(objectOf("scheduleEnabled" to true, "opensAt" to clock(60), "closesAt" to clock(180)))
            val c = api.catalog()
            assertFalse(c.open); assertTrue(c.canOrder)
            val q = api.quote(customer.accessToken, listOf(DraftLine(Kind.PIZZA, flavorIds = listOf("calabresa"))),
                Checkout(mode = Mode.PICKUP), allowScheduling = true)
            assertEquals(c.nextOpening, q.scheduledFor)
            val pending = Pending.order(q, endpoint, customer.user)
            created = api.send(customer.accessToken, pending)
            assertEquals(Status.SCHEDULED, created.status)
            assertEquals(created.id, api.send(customer.accessToken, pending).id)
            assertEquals(q.scheduledFor, api.order(customer.accessToken, created.id).scheduledFor)
            try { settings(objectOf("open" to true)); fail("Early opening must be confirmed") }
            catch (e: ApiFailure) { assertEquals("EARLY_OPEN_CONFIRMATION_REQUIRED", e.code) }
            settings(objectOf("open" to true, "confirmEarlyOpen" to true))
            val released = api.order(customer.accessToken, created.id)
            assertEquals(Status.NEW, released.status)
            assertEquals(1, released.events.count { it.action == "schedule-released" })
            api.catalog()
            assertEquals(released.version, api.order(customer.accessToken, created.id).version)
        } finally {
            created?.let {
                val current = api.order(customer.accessToken, it.id)
                if (current.canCancel) api.send(customer.accessToken, Pending.cancel(current, "Fim do teste", endpoint, customer.user))
            }
            settings(objectOf("scheduleEnabled" to false, "open" to true, "opensAt" to "17:00", "closesAt" to "03:00"))
            api.logout(customer.accessToken); api.logout(manager.accessToken)
        }
    }

}
