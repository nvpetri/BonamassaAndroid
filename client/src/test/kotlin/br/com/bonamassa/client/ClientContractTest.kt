package br.com.bonamassa.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ClientContractTest {
    private val user = User("customer-a", "store-a", "Cliente", "cliente@example.com", "11912345678", "CUSTOMER")
    private val endpoint = Endpoint.parse("http://10.0.2.2:3001", "bonamassa", true)
    private val pizza = DraftLine(Kind.PIZZA, flavorIds = listOf("calabresa", "frango"), crust = "CREAM", quantity = 2)
    private fun session(u: User = user) = Session("a".repeat(43), "2026-09-13T21:00:00Z", u)
    private fun quote() = Quote(UUID.randomUUID().toString(), "2026-09-13T10:05:00Z", emptyList(), Totals(6500, 500, 0, 7000, null))
    private fun product(id: String, kind: Kind, price: Long) = Product(id, id, "", kind, if (kind == Kind.PIZZA) "TRADITIONAL" else null, Size.entries.associateWith { price }, true, null, emptyList(), null)
    private fun catalog() = Catalog("store-a", "Bonamassa", true, 500, listOf(product("calabresa", Kind.PIZZA, 5500), product("frango", Kind.PIZZA, 6000), product("CREAM", Kind.CRUST, 1000)), emptyList(), "2026-09-13T00:00:00Z")
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected validation to fail") } catch (_: IllegalArgumentException) {} }

    @Test fun customerQuoteNeverSendsIdentityChannelOrClientPrices() {
        val body = Checkout(mode = Mode.PICKUP, payment = Method.CARD, cash = "invalid").request(listOf(pizza))
        assertEquals(setOf("items", "mode", "address", "note", "payment", "cashTendered", "promotionId"), body.keySet())
        assertTrue(body.isNull("address")); assertTrue(body.isNull("cashTendered"))
        val line = body.getJSONArray("items").getJSONObject(0)
        assertEquals(setOf("kind", "flavorIds", "size", "crust", "quantity", "note"), line.keySet())
        assertEquals(listOf("calabresa", "frango"), line.getJSONArray("flavorIds").strings())
    }
    @Test fun comboUsesTheMerchantRecipeAndFixedProductPrice() {
        val line = DraftLine(Kind.COMBO, "combo-dupla", quantity = 3, note = "Sem talheres")
        assertEquals(setOf("kind", "productId", "quantity", "note"), line.json().keySet())
        val c = catalog().copy(products = catalog().products + product("combo-dupla", Kind.COMBO, 6500))
        assertEquals(6500L, c.estimate(line))
    }
    @Test fun drinkHasNoUnsupportedNoteOrPizzaOptions() {
        assertEquals(setOf("kind", "productId", "quantity"), DraftLine(Kind.DRINK, "refri", note = "ignored").json().keySet())
    }
    @Test fun highestFlavorPlusCrustUsesIntegerCents() { assertEquals(7000L, catalog().estimate(pizza)) }
    @Test fun unavailableFlavorBlocksEstimate() { rejected { catalog().estimate(pizza.copy(flavorIds = listOf("missing"))) } }
    @Test fun unavailableCrustBlocksEstimate() { rejected { catalog().estimate(pizza.copy(crust = "old-crust")) } }
    @Test fun duplicateFlavorAndQuantityLimitsAreEnforced() {
        rejected { pizza.copy(flavorIds = listOf("frango", "frango")).json() }
        rejected { pizza.copy(quantity = 21).json() }; rejected { pizza.copy(quantity = 0).json() }
        rejected { pizza.copy(note = "x".repeat(241)).json() }
    }
    @Test fun cashInputDoesNotRoundOrAcceptNegativeOrAmbiguousMoney() {
        assertEquals(10050L, parseCash("100,50")); assertEquals(10050L, parseCash("100.50")); assertNull(parseCash(" "))
        listOf("-1", "NaN", "1.000,00", "1.005", "1e3", "100001").forEach { input -> rejected { parseCash(input) } }
    }
    @Test fun deliveryAddressIsRequiredButPickupOmitsSavedAddress() {
        rejected { Checkout().request(listOf(pizza)) }
        assertTrue(Checkout(mode = Mode.PICKUP).request(listOf(pizza)).isNull("address"))
        val address = Address("Rua A", "10", "Centro", "São Paulo", "sp", "01001-000", "Casa")
        val body = Checkout(address = address).request(listOf(pizza))
        assertEquals("SP", body.getJSONObject("address").getString("state"))
        assertEquals("01001000", body.getJSONObject("address").getString("postalCode"))
    }
    @Test fun noEmptyCartOrPrepaidSubmission() {
        rejected { Checkout(mode = Mode.PICKUP).request(emptyList()) }
        rejected { Checkout(mode = Mode.PICKUP, payment = Method.PREPAID).request(listOf(pizza)) }
    }
    @Test fun endpointRejectsUnsafeOrMisconfiguredOrigins() {
        listOf("http://user:password@localhost:3001", "http://localhost:3001/v1", "http://localhost:3001?token=a", "file:///etc/passwd").forEach { rejected { Endpoint.parse(it, "bonamassa", true) } }
        rejected { Endpoint.parse("http://localhost:3001", "bonamassa", false) }
        rejected { Endpoint.parse("https://pizza.example", "../other", false) }
        assertEquals("https://pizza.example", Endpoint.parse("https://pizza.example/", "bonamassa", false).origin)
    }
    @Test fun photosStayAtTheStoreOrigin() {
        val id = UUID.randomUUID()
        assertEquals("${endpoint.origin}/v1/stores/bonamassa/images/$id", endpoint.photo("/v1/stores/bonamassa/images/$id"))
        assertNull(endpoint.photo("https://evil.example/photo")); assertNull(endpoint.photo("//evil.example")); assertNull(endpoint.photo("/v1/stores/other/images/$id"))
    }
    @Test fun pendingWriteSurvivesRestartWithOriginalKeyAndBody() {
        val pending = Pending.order(quote(), endpoint, user)
        val state = SavedState(endpoint.origin, endpoint.storeSlug, user, session(), listOf(pizza), setOf("frango"), Checkout(mode = Mode.PICKUP), pending)
        assertEquals(state, SavedCodec.decode(SavedCodec.encode(state)))
        assertEquals(pending.key, SavedCodec.decode(SavedCodec.encode(state.copy(session = null))).pending?.key)
    }
    @Test fun pendingWriteCannotBeReplayedForAnotherAccountOrServer() {
        val p = Pending.order(quote(), endpoint, user)
        assertTrue(p.belongsTo(endpoint, user))
        assertFalse(p.belongsTo(endpoint.copy(origin = "http://other:3001"), user))
        assertFalse(p.belongsTo(endpoint, user.copy(id = "customer-b")))
        assertFalse(p.belongsTo(endpoint, user.copy(storeId = "store-b")))
        rejected { SavedState(account = user, pending = p).signedIn(session(user.copy(id = "customer-b"))) }
    }
    @Test fun nextAccountDoesNotInheritCustomerData() {
        val next = SavedState(account = user, cart = listOf(pizza), checkout = Checkout(address = Address(street = "Private"))).signedIn(session(user.copy(id = "customer-b")))
        assertTrue(next.cart.isEmpty()); assertEquals("", next.checkout.address.street)
    }
    @Test fun expiredSessionCanResumeTheSameAccountWithoutLosingCart() {
        val state = SavedState(account = user, cart = listOf(pizza), pending = Pending.order(quote(), endpoint, user))
        assertEquals(state.pending, state.signedIn(session()).pending)
        assertEquals(state.cart, state.signedIn(session()).cart)
    }
    @Test fun incompatibleSavedDataDoesNotSilentlyResetPendingOrder() {
        rejected { SavedCodec.decode(SavedCodec.encode(SavedState()).replace("\"version\":1", "\"version\":99")) }
    }
    @Test fun statusesKeepReturnsSeparateFromSuccessfulDelivery() {
        assertTrue(Status.RETURNING.active); assertFalse(Status.RETURNED.active)
        assertNotEquals(Status.DELIVERED.label, Status.RETURNED.label)
        assertEquals(9, Status.entries.size)
    }
    @Test fun transientFailuresKeepPendingWrites() {
        listOf(401, 408, 429, 500, 502, 503, 307).forEach { assertFalse(ApiFailure(it, "ERROR", "", null).definitive) }
        assertFalse(ApiFailure(409, "IDEMPOTENCY_CONFLICT", "", null).definitive)
        assertTrue(ApiFailure(409, "QUOTE_CHANGED", "", null).definitive)
    }
    @Test fun httpTransportSendsBearerAndKeepsTheSameIdempotencyKeyOnRetry() {
        MockWebServer().use { server ->
            val client = BonamassaApi(Endpoint.parse(server.url("/").toString(), "bonamassa", true))
            val pending = Pending.order(quote(), endpoint, user)
            repeat(2) { server.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"BUSY","message":"Tente novamente","requestId":"req-1"}""")) }
            repeat(2) { try { client.send(session().accessToken, pending); fail() } catch (e: ApiFailure) { assertEquals("req-1", e.requestId) } }
            val first = server.takeRequest(); val second = server.takeRequest()
            assertEquals("Bearer ${session().accessToken}", first.getHeader("Authorization"))
            assertEquals(pending.key, first.getHeader("Idempotency-Key")); assertEquals(first.body.readUtf8(), second.body.readUtf8())
            assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key")); assertEquals(2, server.requestCount)
        }
    }
    @Test fun redirectsAreNotFollowedWithCredentials() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", server.url("/stolen")))
            val client = BonamassaApi(Endpoint.parse(server.url("/").toString(), "bonamassa", true))
            try { client.me("secret"); fail() } catch (e: ApiFailure) { assertEquals(307, e.status) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun staffLoginIsRejectedAndItsIssuedTokenIsRevoked() {
        MockWebServer().use { server ->
            val staff = user.copy(role = "MANAGER")
            val encoded = JSONObject(SavedCodec.encode(SavedState(session = session(staff)))).getJSONObject("session")
            server.enqueue(MockResponse().setBody(encoded.toString())); server.enqueue(MockResponse().setResponseCode(204))
            val client = BonamassaApi(Endpoint.parse(server.url("/").toString(), "bonamassa", true))
            try { client.signIn("STAFF@example.com", "password"); fail() } catch (e: ApiFailure) { assertEquals("CUSTOMER_ONLY", e.code) }
            val login = server.takeRequest(); assertEquals("staff@example.com", JSONObject(login.body.readUtf8()).getString("email"))
            val logout = server.takeRequest(); assertEquals("DELETE", logout.method); assertEquals("/v1/sessions/current", logout.path)
        }
    }
}
